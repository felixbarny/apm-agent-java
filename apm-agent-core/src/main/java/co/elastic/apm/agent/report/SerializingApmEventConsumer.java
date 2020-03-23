package co.elastic.apm.agent.report;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.processor.Processor;
import co.elastic.apm.agent.report.queue.ByteRingBufferProcessor;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import org.jctools.queues.MessagePassingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SerializingApmEventConsumer implements MessagePassingQueue.Consumer<Object> {

    private static final Logger logger = LoggerFactory.getLogger(SerializingApmEventConsumer.class);
    private final DslJsonSerializer serializer;
    private final ByteRingBufferProcessor byteQueue;
    private final List<Processor> processors;

    public SerializingApmEventConsumer(DslJsonSerializer serializer, ByteRingBufferProcessor ringBufferProcessor, List<Processor> processors) {
        this.serializer = serializer;
        this.byteQueue = ringBufferProcessor;
        this.processors = processors;
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
            Span span = (Span) event;
            serializer.serializeSpanNdJson(span);
            span.decrementReferences();
        } else if (event instanceof Transaction) {
            Transaction transaction = (Transaction) event;
            transaction.trackMetrics();
            for (int i = 0, size = processors.size(); i < size; i++) {
                processors.get(i).processBeforeReport(transaction);
            }
            serializer.serializeTransactionNdJson(transaction);
            transaction.decrementReferences();
        } else if (event instanceof ErrorCapture) {
            ErrorCapture errorCapture = (ErrorCapture) event;
            for (int i = 0, size = processors.size(); i < size; i++) {
                processors.get(i).processBeforeReport(errorCapture);
            }
            serializer.serializeErrorNdJson(errorCapture);
            errorCapture.recycle();
        } else if (event instanceof MetricRegistry) {
            serializer.serializeMetrics((MetricRegistry) event);
        } else {
            logger.warn("Unsupported type: {}", event.getClass());
        }
    }
}
