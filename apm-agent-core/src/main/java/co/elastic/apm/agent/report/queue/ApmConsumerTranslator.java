package co.elastic.apm.agent.report.queue;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;
import org.jctools.queues.MessagePassingQueue;

public class ApmConsumerTranslator implements MessagePassingQueue.Consumer<Object> {
    private final ApmEventConsumer consumer;

    public ApmConsumerTranslator(ApmEventConsumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public void accept(Object e) {
        if (e instanceof Span) {
            consumer.acceptSpan((Span) e);
        } else if (e instanceof Transaction) {
            consumer.acceptTransaction((Transaction) e);
        } else if (e instanceof ErrorCapture) {
            consumer.acceptError((ErrorCapture) e);
        } else if (e instanceof MetricRegistry) {
            consumer.acceptMetrics((MetricRegistry) e);
        } else {
            throw new IllegalArgumentException("Unsupported type: " + e.getClass());
        }
    }
}
