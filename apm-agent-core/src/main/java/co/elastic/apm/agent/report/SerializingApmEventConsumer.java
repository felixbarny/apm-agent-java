package co.elastic.apm.agent.report;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.queue.ByteQueue;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import org.jctools.queues.MessagePassingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SerializingApmEventConsumer implements MessagePassingQueue.Consumer<Object> {

    private static final Logger logger = LoggerFactory.getLogger(SerializingApmEventConsumer.class);
    private final DslJsonSerializer serializer;
    private final ByteQueue byteQueue;

    public SerializingApmEventConsumer(DslJsonSerializer serializer, ByteQueue byteQueue) {
        this.serializer = serializer;
        this.byteQueue = byteQueue;
    }

    @Override
    public void accept(Object e) {
        if (e instanceof Span) {
            serializer.serializeSpanNdJson((Span) e);
        } else if (e instanceof Transaction) {
            serializer.serializeTransactionNdJson((Transaction) e);
        } else if (e instanceof ErrorCapture) {
            serializer.serializeErrorNdJson((ErrorCapture) e);
        } else if (e instanceof MetricRegistry) {
            serializer.serializeMetrics((MetricRegistry) e);
        } else {
            logger.warn("Unsupported type: {}", e.getClass());
        }
        serializer.writeTo(byteQueue);
    }
}
