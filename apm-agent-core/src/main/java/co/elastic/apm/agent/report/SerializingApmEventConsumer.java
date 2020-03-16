package co.elastic.apm.agent.report;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.Resetter;
import co.elastic.apm.agent.objectpool.impl.AbstractObjectPool;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.report.queue.ApmEventConsumer;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.SpscArrayQueue;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

public class SerializingApmEventConsumer implements ApmEventConsumer {

    private static final int MAX_BUFFER_SIZE_MB = 16;
    private static final int MiB_1 = 1024 * 1024;
    private final DslJsonSerializer serializer;
    private final AbstractObjectPool<ByteBuffer> bufferPool = QueueBasedObjectPool.of(new SpscArrayQueue<>(MAX_BUFFER_SIZE_MB), false, new Allocator<ByteBuffer>() {
        @Override
        public ByteBuffer createInstance() {
            return ByteBuffer.allocateDirect(MiB_1);
        }
    }, new Resetter<ByteBuffer>() {
        @Override
        public void recycle(ByteBuffer object) {
            object.clear();
        }
    });
    private final MessagePassingQueue<ByteBuffer> sendToApmServer;
    @Nullable
    private ByteBuffer currentBuffer;

    public SerializingApmEventConsumer(DslJsonSerializer serializer, MessagePassingQueue<ByteBuffer> sendToApmServer) {
        this.serializer = serializer;
        this.sendToApmServer = sendToApmServer;
        requestNextBuffer();
    }

    @Override
    public void onSpan(Span span) {
        if (currentBuffer == null) {
            requestNextBuffer();
        }
        if (currentBuffer != null) {
            serializer.serializeSpanNdJson(span);
            writeToBuffer(currentBuffer);
        }
    }

    @Override
    public void onTransaction(Transaction transaction) {
        if (currentBuffer == null) {
            requestNextBuffer();
        }
        if (currentBuffer != null) {
            serializer.serializeTransactionNdJson(transaction);
            writeToBuffer(currentBuffer);
        }
    }

    @Override
    public void onError(ErrorCapture errorCapture) {
        if (currentBuffer == null) {
            requestNextBuffer();
        }
        if (currentBuffer != null) {
            serializer.serializeErrorNdJson(errorCapture);
            writeToBuffer(currentBuffer);
        }
    }

    @Override
    public void onMetrics(MetricRegistry metricRegistry) {
        if (currentBuffer == null) {
            requestNextBuffer();
        }
        if (currentBuffer != null) {
            serializer.serializeMetrics(metricRegistry);
            writeToBuffer(currentBuffer);
        }
    }

    @Override
    public void onTick() {
        if (currentBuffer != null && needsFlush()) {
            publishCurrentBuffer(currentBuffer);
        }
        requestNextBuffer();
    }

    private boolean needsFlush() {
        // TODO implement timeout
        return true;
    }

    private void writeToBuffer(ByteBuffer currentBuffer) {
        if (currentBuffer.remaining() >= serializer.getBufferSize() + 4) {
            currentBuffer.putInt(serializer.getBufferSize());
            serializer.writeTo(currentBuffer);
        } else {
            publishCurrentBuffer(currentBuffer);
            requestNextBuffer();
        }
    }

    private void publishCurrentBuffer(ByteBuffer currentBuffer) {
        currentBuffer.flip();
        sendToApmServer.offer(currentBuffer);
        this.currentBuffer = null;
    }

    private void requestNextBuffer() {
        currentBuffer = bufferPool.tryCreateInstance();
    }
}
