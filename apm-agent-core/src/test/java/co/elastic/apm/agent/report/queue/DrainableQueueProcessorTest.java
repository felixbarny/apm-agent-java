package co.elastic.apm.agent.report.queue;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.SpscArrayQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

public class DrainableQueueProcessorTest {

    private static final int CAPACITY = 4;
    private DrainableQueueProcessor<String, String> stringQueueProcessor;
    private DrainableQueueProcessor<byte[], ByteRingBuffer> binaryQueueProcessor;
    private List<String> processedEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MessagePassingQueue.Consumer<String> stringConsumer = s -> binaryQueueProcessor.offer(s.getBytes());
        MessagePassingQueue.Consumer<ByteRingBuffer> byteRingBufferConsumer = buffer -> {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                buffer.writeTo(baos, new byte[16], 1);
                processedEvents.add(new String(baos.toByteArray()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
        stringQueueProcessor = new DrainableQueueProcessor<>(() -> DrainableQueue.MessagePassingQueueAdapter.of(new SpscArrayQueue<>(CAPACITY)),
            new MutableRunnableThread("string-processing"),
            stringConsumer,
            ProcessorLifecycleCallback.Noop.INSTANCE,
            100_000_000L,
            100,
            1000);
        binaryQueueProcessor = new DrainableQueueProcessor<>(() -> new SpscOffHeapByteBuffer(1024),
            new MutableRunnableThread("byte-processing"),
            byteRingBufferConsumer,
            ProcessorLifecycleCallback.Noop.INSTANCE,
            100_000_000L,
            100,
            1000);
        stringQueueProcessor.start(mock(ElasticApmTracer.class));
        binaryQueueProcessor.start(mock(ElasticApmTracer.class));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        stringQueueProcessor.stop();
        binaryQueueProcessor.stop();
    }

    @Test
    void testConsumeOne() {
        testProcessing(List.of("foo"));
    }

    @Test
    void testCapacity() {
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < CAPACITY; i++) {
            expected.add(Integer.toString(i));
        }
        testProcessing(expected);
    }

    private void testProcessing(List<String> events) {
        for (String event : events) {
            stringQueueProcessor.offer(event);
        }
        await().untilAsserted(() -> assertThat(processedEvents).isEqualTo(events));
    }
}
