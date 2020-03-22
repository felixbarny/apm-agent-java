package co.elastic.apm.agent.report;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.queue.ByteRingBuffer;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import org.jctools.queues.MessagePassingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SerializingApmEventConsumer implements MessagePassingQueue.Consumer<Object> {

    private static final Logger logger = LoggerFactory.getLogger(SerializingApmEventConsumer.class);
    private final DslJsonSerializer serializer;
    private final ByteRingBuffer byteQueue;

    public SerializingApmEventConsumer(DslJsonSerializer serializer, ByteRingBuffer byteQueue) {
        this.serializer = serializer;
        this.byteQueue = byteQueue;
    }

    @Override
    public void accept(Object event) {
        try {
            serialize(event, serializer);
        } catch (RuntimeException e) {
            logger.error(e.getMessage(), e);
        } finally {
            serializer.writeTo(byteQueue);
        }
    }

    private void serialize(Object event, DslJsonSerializer serializer) {
        if (event instanceof Span) {
            serializer.serializeSpanNdJson((Span) event);
        } else if (event instanceof Transaction) {
            serializer.serializeTransactionNdJson((Transaction) event);
        } else if (event instanceof ErrorCapture) {
            serializer.serializeErrorNdJson((ErrorCapture) event);
        } else if (event instanceof MetricRegistry) {
            serializer.serializeMetrics((MetricRegistry) event);
        } else {
            logger.warn("Unsupported type: {}", event.getClass());
        }
    }
}
