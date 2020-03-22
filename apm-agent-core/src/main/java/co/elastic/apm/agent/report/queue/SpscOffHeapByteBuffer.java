package co.elastic.apm.agent.report.queue;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.util.PortableJvmInfo;
import org.jctools.util.Pow2;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import static co.elastic.apm.agent.report.queue.UnsafeDirectByteBuffer.alignedSlice;
import static co.elastic.apm.agent.report.queue.UnsafeDirectByteBuffer.allocateAlignedByteBuffer;
import static org.jctools.util.UnsafeAccess.UNSAFE;

/**
 * Implementation is based on {@code org.jctools.queues.SpscOffHeapIntQueue}
 */
public class SpscOffHeapByteBuffer extends OutputStream {
    // Cached array base offset
    private static final long ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
    private static final MessagePassingQueue.WaitStrategy BUSY_SPIN_WAIT_STRATEGY = new MessagePassingQueue.WaitStrategy() {
        @Override
        public int idle(int idleCounter) {
            return idleCounter;
        }
    };
    // 24b,8b,8b,24b | pad | 24b,8b,8b,24b | pad
    private final ByteBuffer buffy;
    private final long headAddress;
    private final long tailCacheAddress;
    private final long tailAddress;
    private final long headCacheAddress;

    private final int capacity;
    private final int mask;
    private final long arrayBase;
    private final MessagePassingQueue.ExitCondition exitWhenEmpty;
    private final QueueSignalHandler handler;

    public SpscOffHeapByteBuffer(final int capacity) {
        this(capacity, QueueSignalHandler.Noop.INSTANCE);
    }

    public SpscOffHeapByteBuffer(final int capacity, QueueSignalHandler instance) {
        this(allocateAlignedByteBuffer(
            getRequiredBufferSize(capacity),
            PortableJvmInfo.CACHE_LINE_SIZE),
            Pow2.roundToPowerOfTwo(capacity), instance);
    }

    public SpscOffHeapByteBuffer(final ByteBuffer buff,
                                 final int capacity, QueueSignalHandler handler) {
        this.capacity = Pow2.roundToPowerOfTwo(capacity);
        buffy = alignedSlice(4 * PortableJvmInfo.CACHE_LINE_SIZE + this.capacity,
            PortableJvmInfo.CACHE_LINE_SIZE, buff);
        this.handler = handler;

        long alignedAddress = UnsafeDirectByteBuffer.getAddress(buffy);

        headAddress = alignedAddress;
        tailCacheAddress = headAddress + 8;
        tailAddress = headAddress + 2L * PortableJvmInfo.CACHE_LINE_SIZE;
        headCacheAddress = tailAddress + 8;
        arrayBase = alignedAddress + 4L * PortableJvmInfo.CACHE_LINE_SIZE;
        // producer owns tail and headCache
        setHeadCache(0);
        setTail(0);
        // consumer owns head and tailCache
        setTailCache(0);
        setHead(0);
        mask = this.capacity - 1;
        exitWhenEmpty = new MessagePassingQueue.ExitCondition() {
            @Override
            public boolean keepRunning() {
                return hasContent(getHeadPlain());
            }
        };
    }

    public static int getRequiredBufferSize(final int capacity) {
        int p2Capacity = Pow2.roundToPowerOfTwo(capacity);
        if (p2Capacity > (Pow2.MAX_POW2))
            throw new IllegalArgumentException("capacity exceeds max buffer capacity: " + Pow2.MAX_POW2);
        return 4 * PortableJvmInfo.CACHE_LINE_SIZE + p2Capacity;
    }

    @Override
    public void write(int b) {
        write(new byte[]{(byte) b});
    }

    @Override
    public void write(byte[] b) {
        offer(b);
    }

    @Override
    public void write(byte[] b, int off, int len) {
        offer(b, off, len);
    }

    public boolean offer(final byte[] bytes) {
        return offer(bytes, 0, bytes.length);
    }

    public boolean offer(final byte[] bytes, int offset, int size) {
        // has to be aligned in blocks of 4 bytes so that writing the size of the array never wraps in-between
        // otherwise we couldn't use putInt
        int alignedSize = alignToMultipleOf4(size);
        final long currentTail = getTailPlain();
        long tail = currentTail;
        int totalBytesToWrite = alignedSize + 4;
        if (!hasCapacity(totalBytesToWrite, currentTail)) {
            return false;
        }

        // writes the size of the array as we want to be able to read in steps that match the written byte arrays
        UNSAFE.putInt(calcElementOffset(tail), size);
        tail += 4;
        int written = 0;
        while (written < size) {
            long copyBytes = Math.min(capacity - (tail & mask), size - written);
            UNSAFE.copyMemory(bytes, ARRAY_BASE_OFFSET + offset + written, null, calcElementOffset(tail), copyBytes);
            tail += copyBytes;
            written += copyBytes;
        }
        setTail(currentTail + totalBytesToWrite);
        handler.onNotEmpty();
        return true;
    }

    private static int alignToMultipleOf4(int size) {
        return ((size + 3) / 4) * 4;
    }

    private boolean hasCapacity(int requestedCapacity, long currentTail) {
        final long wrapPoint = currentTail - capacity + requestedCapacity;
        if (getHeadCache() < wrapPoint) {
            setHeadCache(getHead());
            if (getHeadCache() < wrapPoint) {
                return false;
            }
        }
        return true;
    }

    public void writeTo(OutputStream os, byte[] buffer) throws IOException {
        writeTo(os, buffer, exitWhenEmpty, BUSY_SPIN_WAIT_STRATEGY);
    }

    public void writeTo(OutputStream os, byte[] buffer, MessagePassingQueue.ExitCondition exitCondition, MessagePassingQueue.WaitStrategy waitStrategy) throws IOException {
        int idleCounter = 0;
        while (exitCondition.keepRunning()) {
            long head = getHeadPlain();
            if (!hasContent(head)) {
                idleCounter = waitStrategy.idle(idleCounter);
                continue;
            }
            idleCounter = 0;
            final long currentHead = head;
            final int size = UNSAFE.getInt(calcElementOffset(head));
            assert size <= capacity - 4 : String.format("%d > %d", size, capacity - 4);
            head += 4;
            int alignedSize = ((size + 3) / 4) * 4;
            int read = 0;
            while (read < size) {
                long copyBytes = Math.min(Math.min(capacity - (head & mask), size - read), buffer.length);
                UNSAFE.copyMemory(null, calcElementOffset(head), buffer, ARRAY_BASE_OFFSET, copyBytes);
                head += copyBytes;
                read += copyBytes;
                os.write(buffer, 0, (int) copyBytes);
            }
            setHead(currentHead + 4 + alignedSize);
        }
    }

    private boolean hasContent(long currentHead) {
        if (currentHead >= getTailCache()) {
            setTailCache(getTail());
            if (currentHead >= getTailCache()) {
                return false;
            }
        }
        return true;
    }

    private long calcElementOffset(final long currentHead) {
        return arrayBase + (currentHead & mask);
    }

    public int size() {
        return (int) (getTail() - getHead());
    }

    public boolean isEmpty() {
        return getTail() == getHead();
    }

    private long getHeadPlain() {
        return UNSAFE.getLong(null, headAddress);
    }

    private long getHead() {
        return UNSAFE.getLongVolatile(null, headAddress);
    }

    private void setHead(final long value) {
        UNSAFE.putOrderedLong(null, headAddress, value);
    }

    private long getTailPlain() {
        return UNSAFE.getLong(null, tailAddress);
    }

    private long getTail() {
        return UNSAFE.getLongVolatile(null, tailAddress);
    }

    private void setTail(final long value) {
        UNSAFE.putOrderedLong(null, tailAddress, value);
    }

    private long getHeadCache() {
        return UNSAFE.getLong(null, headCacheAddress);
    }

    private void setHeadCache(final long value) {
        UNSAFE.putLong(headCacheAddress, value);
    }

    private long getTailCache() {
        return UNSAFE.getLong(null, tailCacheAddress);
    }

    private void setTailCache(final long value) {
        UNSAFE.putLong(tailCacheAddress, value);
    }

}
