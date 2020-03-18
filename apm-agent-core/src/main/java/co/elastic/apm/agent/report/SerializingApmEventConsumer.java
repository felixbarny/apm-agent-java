package co.elastic.apm.agent.report;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.Resetter;
import co.elastic.apm.agent.objectpool.impl.AbstractObjectPool;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.report.queue.FlushableConsumer;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.SpscArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

public class SerializingApmEventConsumer implements FlushableConsumer<Object> {

    private static final Logger logger = LoggerFactory.getLogger(SerializingApmEventConsumer.class);
    private static final int MAX_BUFFER_SIZE_MB = 16;
    private static final int CHUNK_SIZE = 1024 * 1024;
    private final DslJsonSerializer serializer;
    private final AbstractObjectPool<ByteBuffer> bufferPool = QueueBasedObjectPool.of(new SpscArrayQueue<>(MAX_BUFFER_SIZE_MB), false, new Allocator<ByteBuffer>() {
        @Override
        public ByteBuffer createInstance() {
            return ByteBuffer.allocateDirect(CHUNK_SIZE);
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
    public void accept(Object e) {
        if (currentBuffer == null) {
            requestNextBuffer();
        }
        if (currentBuffer != null) {
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
            writeToBuffer(serializer, currentBuffer, e);
        }
    }

    @Override
    public void flush() {
        // TODO this flushes every time the queue is empty
        //   as we only have few but relatively large buffers, they run out quickly
        //   either only flush every 1s
        //   or have one larger circular byte buffer, similar to the go agent
        if (currentBuffer != null && currentBuffer.position() > 0) {
            doFlush(currentBuffer);
        }
    }

    private void writeToBuffer(DslJsonSerializer serializer, ByteBuffer currentBuffer, Object event) {
        int totalEventSize = serializer.getBufferSize() + 4;
        if (currentBuffer.remaining() < totalEventSize) {
            if (totalEventSize > CHUNK_SIZE) {
                logger.warn("Event exceeds max size of 1MB {}", event);
                return;
            }
            currentBuffer = doFlush(currentBuffer);
        }
        if (currentBuffer != null) {
            currentBuffer.putInt(serializer.getBufferSize());
            serializer.writeTo(currentBuffer);
        }
    }

    @Nullable
    private ByteBuffer doFlush(ByteBuffer currentBuffer) {
        publishCurrentBuffer(currentBuffer);
        return requestNextBuffer();
    }

    private void publishCurrentBuffer(ByteBuffer currentBuffer) {
        currentBuffer.flip();
        sendToApmServer.offer(currentBuffer);
        this.currentBuffer = null;
    }

    @Nullable
    private ByteBuffer requestNextBuffer() {
        currentBuffer = bufferPool.tryCreateInstance();
        return currentBuffer;
    }
}
