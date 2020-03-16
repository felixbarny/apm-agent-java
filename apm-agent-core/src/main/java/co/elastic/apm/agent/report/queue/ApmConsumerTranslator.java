package co.elastic.apm.agent.report.queue;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;
import org.jctools.queues.MessagePassingQueue;

public abstract class ApmConsumerTranslator<T> implements MessagePassingQueue.Consumer<T> {

    protected final ApmEventConsumer consumer;

    protected ApmConsumerTranslator(ApmEventConsumer consumer) {
        this.consumer = consumer;
    }

    static class ForEvents extends ApmConsumerTranslator<Object> {

        ForEvents(ApmEventConsumer consumer) {
            super(consumer);
        }

        @Override
        public void accept(Object e) {
            if (e instanceof Span) {
                consumer.onSpan((Span) e);
            } else if (e instanceof Transaction) {
                consumer.onTransaction((Transaction) e);
            } else if (e instanceof ErrorCapture) {
                consumer.onError((ErrorCapture) e);
            } else if (e instanceof MetricRegistry) {
                consumer.onMetrics((MetricRegistry) e);
            } else {
                throw new IllegalArgumentException("Unsupported type: " + e.getClass());
            }
        }
    }

    static class ForTick extends ApmConsumerTranslator<Void> {
        protected ForTick(ApmEventConsumer consumer) {
            super(consumer);
        }

        @Override
        public void accept(Void e) {
            consumer.onTick();
        }
    }
}
