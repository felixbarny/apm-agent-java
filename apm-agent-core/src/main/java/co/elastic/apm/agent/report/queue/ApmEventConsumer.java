package co.elastic.apm.agent.report.queue;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;

public interface ApmEventConsumer {

    void acceptSpan(Span span);

    void acceptTransaction(Transaction transaction);

    void acceptError(ErrorCapture errorCapture);

    void acceptMetrics(MetricRegistry metricRegistry);
}
