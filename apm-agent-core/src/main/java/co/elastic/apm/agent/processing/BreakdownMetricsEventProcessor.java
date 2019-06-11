package co.elastic.apm.agent.processing;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.ReportingEvent;
import org.jctools.queues.MessagePassingQueue;

import java.util.concurrent.FutureTask;

/**
 * Receives all events (sampled and non-sampled) and either recycles them (by decrementing the reference counter)
 * or sends them to the {@link #reporterQueue} where they are waiting to be reported to the APM Server.
 */
public class BreakdownMetricsEventProcessor implements EventProcessor {

    private final MessagePassingQueue<Object> reporterQueue;

    public BreakdownMetricsEventProcessor(MessagePassingQueue<Object> reporterQueue) {
        this.reporterQueue = reporterQueue;
    }

    @Override
    public void report(Transaction transaction) {
        if (!transaction.isNoop()) {
            transaction.trackMetrics();
            // we do report non-sampled transactions (without the context)
            if (!reporterQueue.offer(transaction)) {
                transaction.decrementReferences();
            }
        } else {
            transaction.decrementReferences();
        }
    }

    @Override
    public void report(Span span) {
        if (span.isSampled() && !span.isDiscard()) {
            if (!reporterQueue.offer(span)) {
                span.decrementReferences();
            }
        } else {
            span.decrementReferences();
        }
    }

    @Override
    public void report(ErrorCapture errorCapture) {
        if (!reporterQueue.offer(errorCapture)) {
            errorCapture.recycle();
        }
    }

    @Override
    public void onClose() {
        reporterQueue.offer(ReportingEvent.ReportingEventType.SHUTDOWN);
    }

    @Override
    public void reportMetrics(MetricRegistry event) {
        reporterQueue.offer(event);
    }

    @Override
    public void flush(FutureTask<Void> event) {
        reporterQueue.offer(event);
    }
}
