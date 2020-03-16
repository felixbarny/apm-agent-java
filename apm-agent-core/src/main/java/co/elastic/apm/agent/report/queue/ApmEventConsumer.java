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

    void onTick();
}
