package co.elastic.apm.agent.report.queue;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;

public interface ApmEventConsumer {

    void onSpan(Span span);

    void onTransaction(Transaction transaction);

    void onError(ErrorCapture errorCapture);

    void onMetrics(MetricRegistry metricRegistry);

    /**
     * Gets called when the queue is empty or every {@code n} ms.
     * The tick rate can be configured via the constructor of {@link ConsumerProcessor}.
     * <p>
     * This lets implementations know when's a good time to flush accumulated events.
     * The method is called at a regular interval (configurable via {@link ConsumerProcessor}'s constructor)
     * or if there's nothing left in the queue to process (aka end of batch).
     * </p>
     */
    void flush();
}
