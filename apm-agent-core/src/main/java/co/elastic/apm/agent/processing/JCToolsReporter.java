package co.elastic.apm.agent.processing;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.IntakeV2ReportingEventHandler;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.report.ReportingEvent;
import co.elastic.apm.agent.util.queues.MpscThreadLocalQueue;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Receives all (sampled and non-sampled) events from application threads and puts them into a thread-local queue.
 * That offloads all the post-processing work like recycling and breakdown metric tracking from the application threads.
 */
public class JCToolsReporter implements Reporter {

    private final MpscThreadLocalQueue<Object> eventQueue;
    private final AtomicLong dropped = new AtomicLong();

    public JCToolsReporter(MpscThreadLocalQueue<Object> eventQueue) {
        this.eventQueue = eventQueue;
    }

    @Override
    public void report(Transaction transaction) {
        if (!eventQueue.offer(transaction)) {
            transaction.trackMetrics();
            dropped.incrementAndGet();
            transaction.decrementReferences();
        }
    }

    @Override
    public void report(Span span) {
        if (!eventQueue.offer(span)) {
            dropped.incrementAndGet();
            span.decrementReferences();
        }
    }

    @Override
    public Future<Void> flush() {
        FutureTask<Void> future = new FutureTask<>(new Runnable() {
            @Override
            public void run() {

            }
        }, null);
        if (!eventQueue.offer(future)) {
            future.run();
        }
        return future;
    }

    @Override
    public void close() {
        eventQueue.offer(ReportingEvent.ReportingEventType.SHUTDOWN);
    }

    @Override
    public void report(ErrorCapture errorCapture) {
        if (!eventQueue.offer(errorCapture)) {
            dropped.incrementAndGet();
            errorCapture.recycle();
        }
    }

    @Override
    public void reportMetrics(MetricRegistry metricRegistry) {
        eventQueue.offer(metricRegistry);
    }

    @Override
    public long getDropped() {
        return dropped.get();
    }

    @Override
    public long getReported() {
        return IntakeV2ReportingEventHandler.reported;
    }

}
