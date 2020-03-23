package co.elastic.apm.agent.report;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.queue.ByteRingBufferProcessor;
import co.elastic.apm.agent.report.queue.DrainableQueueProcessor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public class JCToolsReporter implements Reporter {

    private final DrainableQueueProcessor<Object, Object> eventSerializer;
    private final ByteRingBufferProcessor eventSender;
    private final AtomicLong dropped = new AtomicLong();

    public JCToolsReporter(DrainableQueueProcessor<Object, Object> eventSerializer, ByteRingBufferProcessor eventSender) {
        this.eventSerializer = eventSerializer;
        this.eventSender = eventSender;
    }

    @Override
    public void start() {
        eventSerializer.start();
        eventSender.start();
    }

    @Override
    public void report(Transaction transaction) {
        if (!eventSerializer.offer(transaction)) {
            dropped.incrementAndGet();
            transaction.trackMetrics();
            transaction.decrementReferences();
        }
    }

    @Override
    public void report(Span span) {
        if (!eventSerializer.offer(span)) {
            dropped.incrementAndGet();
            span.decrementReferences();
        }
    }

    @Override
    public void report(ErrorCapture error) {
        if (!eventSerializer.offer(error)) {
            dropped.incrementAndGet();
            error.recycle();
        }
    }

    @Override
    public long getDropped() {
        return 0;
    }

    @Override
    public long getReported() {
        return 0;
    }

    @Override
    public Future<Void> flush() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.complete(null);
        return future;
    }

    @Override
    public void stop() throws Exception {
        eventSerializer.stop();
        eventSender.stop();
    }

    @Override
    public void scheduleMetricReporting(MetricRegistry metricRegistry, long intervalMs, ElasticApmTracer tracer) {

    }
}
