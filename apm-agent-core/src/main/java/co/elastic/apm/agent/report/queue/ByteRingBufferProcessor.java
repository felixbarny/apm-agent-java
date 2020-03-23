package co.elastic.apm.agent.report.queue;

import org.jctools.queues.MessagePassingQueue;

public class ByteRingBufferProcessor extends DrainableQueueProcessor<byte[], ByteRingBuffer> {

    private final ByteRingBuffer ringBuffer;

    public ByteRingBufferProcessor(MessagePassingQueue.Supplier<ByteRingBuffer> drainableQueueSupplier,
                                   MutableRunnableThread processingThread,
                                   MessagePassingQueue.Consumer<ByteRingBuffer> consumer,
                                   ProcessorLifecycleCallback callback,
                                   long parkTimeNanos,
                                   int drainTimeout,
                                   int shutdownTimeoutMillis) {
        super(drainableQueueSupplier, processingThread, consumer, callback, parkTimeNanos, drainTimeout, shutdownTimeoutMillis);
        ringBuffer = (ByteRingBuffer) super.queue;
    }

    public boolean offer(byte[] event, int size) {
        boolean offered = ringBuffer.offer(event, size);
        if (offered) {
            handler.onNotEmpty();
        }
        return offered;
    }
}
