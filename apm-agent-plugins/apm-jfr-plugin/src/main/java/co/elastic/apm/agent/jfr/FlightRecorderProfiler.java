/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.jfr;

import co.elastic.apm.agent.util.HexUtils;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FlightRecorderProfiler {

    private static final Logger logger = LoggerFactory.getLogger(FlightRecorderProfiler.class);
    public static final int MAX_STACK_DEPTH = 128;

    private final Map<Map<String, String>, CallTree> callTreeByLabels = new HashMap<>();
    private final Path jfrFile;
    private final Duration samplingInterval;
    private final Recording recording;
    private List<Function<RecordedEvent, Map<String, String>>> labelExtractors = new ArrayList<>();
    private Map<String, Collection<Consumer<RecordedEvent>>> eventConsumers = new HashMap<>();
    private Collection<Long> includedThreadIds = new HashSet<>();

    public FlightRecorderProfiler(Duration samplingInterval) throws IOException {
        this(File.createTempFile("profile", ".jfr").toPath(), samplingInterval);
    }

    private FlightRecorderProfiler(Path jfrFile, Duration samplingInterval) throws IOException {
        this.jfrFile = jfrFile;
        this.samplingInterval = samplingInterval;
        recording = new Recording();
        recording.setSettings(Map.of("stackdepth", Integer.toString(MAX_STACK_DEPTH)));
        recording.setDestination(this.jfrFile);
        recording.disable(JfrEventActivationListener.ActiveTransactionEvent.class);
        activateSampleEvent(samplingInterval, "jdk.ExecutionSample");
    }

    public FlightRecorderProfiler activateNativeMethodSample() {
        activateSampleEvent(samplingInterval, "jdk.NativeMethodSample");
        return this;
    }

    private void activateSampleEvent(Duration samplingInterval, String eventName) {
        recording.enable(eventName).withPeriod(samplingInterval);
        addEventConsumer(eventName, event -> {
            if (includedThreadIds.isEmpty() || includedThreadIds.contains(event.getThread("sampledThread").getId())) {
                addToCallGraph(getLabels(event), event);
            }
        });
    }

    public void addEventConsumer(String eventType, Consumer<RecordedEvent> eventConsumer) {
        eventConsumers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(eventConsumer);
    }

    public Recording getRecording() {
        return recording;
    }

    public FlightRecorderProfiler withThreadLabels() {
        addLabelExtractor(event -> Map.of("thread", event.getThread("sampledThread").getJavaName()));
        return this;
    }

    public FlightRecorderProfiler withTransactionLabels(Duration minActivationDuration) {
        final String traceEventName = JfrEventActivationListener.ActiveTransactionEvent.class.getName();
        final TraceEventLabelHandler traceEventLabelHandler = new TraceEventLabelHandler();
        addEventConsumer(traceEventName, traceEventLabelHandler);
        addLabelExtractor(traceEventLabelHandler);
        recording.enable(traceEventName).withThreshold(minActivationDuration);
        return this;
    }

    public FlightRecorderProfiler includeThreads(Thread... threads) {
        includedThreadIds.addAll(Arrays.stream(threads).map(Thread::getId).collect(Collectors.toList()));
        return this;
    }

    public void addLabelExtractor(Function<RecordedEvent, Map<String, String>> labelExtractor) {
        this.labelExtractors.add(labelExtractor);
    }

    public FlightRecorderProfiler start() {
        if (recording.getState() != RecordingState.NEW) {
            throw new IllegalStateException("Already started");
        }
        logger.info("starting profiler");
        recording.start();
        return this;
    }

    public FlightRecorderProfiler stop() throws IOException {
        if (recording.getState() == RecordingState.CLOSED) {
            throw new IllegalStateException("Already stopped");
        }
        logger.info("stopping profiler");
        recording.stop();
        recording.close();
        processRecordingFile();
        logger.info("created {} call trees", callTreeByLabels.size());
        Files.delete(jfrFile);
        return this;
    }

    private void processRecordingFile() throws IOException {
        try (RecordingFile recordingFile = new RecordingFile(jfrFile)) {
            Map<String, AtomicLong> eventCount = new HashMap<>();
            while (recordingFile.hasMoreEvents()) {
                final RecordedEvent event = recordingFile.readEvent();
                final String eventType = event.getEventType().getName();
                eventCount.computeIfAbsent(eventType, k -> new AtomicLong()).incrementAndGet();
                eventConsumers.getOrDefault(eventType, Collections.emptyList()).forEach(c -> c.accept(event));
            }
            logger.info("event count {}", eventCount);
        }
    }

    private void addToCallGraph(Map<String, String> labels, RecordedEvent executionSampleEvent) {
        callTreeByLabels.computeIfAbsent(labels, k -> new CallTree(null, "root")).addStackTrace(executionSampleEvent.getStackTrace());
    }

    private Map<String, String> getLabels(RecordedEvent executionSampleEvent) {
        Map<String, String> labels = new HashMap<>();
        for (Function<RecordedEvent, Map<String, String>> labelExtractor : labelExtractors) {
            labels.putAll(labelExtractor.apply(executionSampleEvent));
        }
        return labels;
    }

    public Map<Map<String, String>, CallTree> getCallTreeByLabels() {
        if (recording.getState() != RecordingState.CLOSED) {
            throw new IllegalStateException("Recording is not yet closed");
        }
        return callTreeByLabels;
    }

    public void exportFlamegraphSvgs(Path flamegraphPl, Path folder) throws IOException {
        if (Files.notExists(folder)) {
            Files.createDirectory(folder);
        }
        for (Map.Entry<Map<String, String>, CallTree> entry : callTreeByLabels.entrySet()) {
            final ProcessBuilder flamegraphProcess = new ProcessBuilder(flamegraphPl.toAbsolutePath().toString());
            final Process process = flamegraphProcess.start();
            try (OutputStreamWriter out = new OutputStreamWriter(process.getOutputStream())) {
                entry.getValue().toFolded(out);
            }
            final String fileName = entry.getKey().entrySet().stream().map(e -> e.getKey() + "-" + e.getValue()).collect(Collectors.joining("-"));
            final Path svg = folder.resolve(fileName + ".svg");
            logger.info("exporting {}", svg);
            if (Files.notExists(svg)) {
                Files.createFile(svg);
            }
            try (InputStream inputStream = process.getInputStream()) {
                Files.copy(inputStream, svg, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    @Nullable
    public CallTree getAggregateCallTree() {
        return getCallTreeByLabels().values().stream().reduce(CallTree::merge).orElse(null);
    }

    public static class TraceEventLabelHandler implements Function<RecordedEvent, Map<String, String>>, Consumer<RecordedEvent> {

        private final Map<Long, TreeMap<Instant, RecordedEvent>> activationEventsByStartTimeAndThread = new HashMap<>();

        @Override
        public void accept(RecordedEvent event) {
            activationEventsByStartTimeAndThread
                .computeIfAbsent(event.getThread().getId(), k -> new TreeMap<>())
                .put(event.getStartTime(), event);
        }

        @Override
        public Map<String, String> apply(RecordedEvent event) {
            final RecordedEvent concurrentActivationEvent = getConcurrentActivationEvent(event);
            if (concurrentActivationEvent != null) {
                return Map.of("transactionId", HexUtils.longToHex(concurrentActivationEvent.getLong("transactionId")));
            } else {
                return Collections.emptyMap();
            }
        }

        @Nullable
        private RecordedEvent getConcurrentActivationEvent(RecordedEvent executionSampleEvent) {
            final TreeMap<Instant, RecordedEvent> activationEventsByTime = activationEventsByStartTimeAndThread.get(executionSampleEvent.getThread("sampledThread").getId());
            if (activationEventsByTime != null) {
                final Map.Entry<Instant, RecordedEvent> potentialMatch = activationEventsByTime.lowerEntry(executionSampleEvent.getStartTime());
                if (potentialMatch != null && potentialMatch.getValue().getEndTime().isAfter(executionSampleEvent.getStartTime())) {
                    return potentialMatch.getValue();
                }
            }
            return null;
        }

    }

    public static class CallTree {

        @Nullable
        private CallTree parent;
        private Map<String, CallTree> children = new HashMap<>();
        private String signature;
        private int count;
        private int selfCount;

        public CallTree(@Nullable CallTree parent, String signature) {
            this.parent = parent;
            this.signature = signature;
        }

        public CallTree merge(CallTree callTree) {
            if (!signature.equals(callTree.signature)) {
                throw new IllegalArgumentException(String.format("Signatures don't match %s %s", signature, callTree.signature));
            }
            count += callTree.count;
            selfCount += callTree.selfCount;
            for (Map.Entry<String, CallTree> entry : callTree.children.entrySet()) {
                children.merge(entry.getKey(), entry.getValue(), CallTree::merge);
            }
            return this;
        }

        public void addStackTrace(RecordedStackTrace stackTrace) {
            if (!stackTrace.isTruncated()) {
                addFrame(stackTrace.getFrames().listIterator(stackTrace.getFrames().size()));
                incrementCount(false);
            } else {
                logger.debug("skipping truncated stack trace");
            }
        }

        private void addFrame(ListIterator<RecordedFrame> iterator) {
            if (iterator.hasPrevious()) {
                final RecordedFrame frame = iterator.previous();
                final String signature = getSignature(frame);
                final CallTree child = children.computeIfAbsent(signature, k -> new CallTree(this, signature));
                child.incrementCount(!iterator.hasPrevious());
                child.addFrame(iterator);
            }
        }

        private void incrementCount(boolean isLeaf) {
            count++;
            if (isLeaf) {
                selfCount++;
            }
        }

        public boolean isLeaf() {
            return children.isEmpty();
        }

        public boolean isRoot() {
            return parent == null;
        }

        private String getSignature(RecordedFrame frame) {
            final RecordedMethod method = frame.getMethod();
            @Nullable final RecordedClass type = method.getType();
            if (type != null) {
                return type.getName() + "." + method.getName();
            } else {
                return method.getName();
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            try {
                toString(sb);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return sb.toString();
        }

        public void toString(Appendable out) throws IOException {
            toString(out, 0, count);
        }

        private void toString(Appendable out, int level, int rootCount) throws IOException {
            for (int i = 0; i <= level; i++) {
                out.append("  ");
            }
            out.append(signature)
                .append(' ').append(Integer.toString(count)).append(String.format(" (%.2f", ((double) count / rootCount) * 100)).append("%)")
                .append(' ').append(Integer.toString(selfCount)).append(String.format(" (%.2f", ((double) selfCount / rootCount) * 100)).append("%)")
                .append('\n');
            for (CallTree node : children.values()) {
                node.toString(out, level + 1, rootCount);
            }
        }

        /**
         * https://github.com/brendangregg/FlameGraph#2-fold-stacks
         *
         * @return
         */
        public String toFolded() {
            StringBuilder sb = new StringBuilder();
            try {
                toFolded(sb);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return sb.toString();
        }

        public void toFolded(Appendable out) throws IOException {
            if (parent != null && selfCount > 0) {
                parent.foldParents(out);
                out.append(signature).append(' ').append(Integer.toString(count)).append('\n');
            }
            for (CallTree node : children.values()) {
                node.toFolded(out);
            }
        }

        private void foldParents(Appendable out) throws IOException {
            if (parent != null) {
                parent.foldParents(out);
                out.append(signature).append(';');
            }
        }

        /**
         * https://github.com/spiermar/d3-flame-graph#input-format
         *
         * @return
         */
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            try {
                toJson(sb);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return sb.toString();
        }

        public void toJson(Appendable sb) throws IOException {
            sb.append('{')
                .append("\"name\":").append('"').append(signature).append('"').append(',')
                .append("\"value\":").append(Integer.toString(count)).append(',')
                .append("\"children\":")
                .append('[');
            for (Iterator<CallTree> iterator = children.values().iterator(); iterator.hasNext(); ) {
                CallTree node = iterator.next();
                sb.append('\n');
                node.toJson(sb);
                if (iterator.hasNext()) {
                    sb.append(',');
                }
            }
            sb.append(']');
            sb.append('}');
        }

    }
}
