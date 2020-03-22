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
public class SpscOffHeapByteBuffer implements ByteRingBuffer {
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

    public SpscOffHeapByteBuffer(final int capacity) {
        this(allocateAlignedByteBuffer(
            getRequiredBufferSize(capacity),
            PortableJvmInfo.CACHE_LINE_SIZE),
            Pow2.roundToPowerOfTwo(capacity));
    }

    public SpscOffHeapByteBuffer(final ByteBuffer buff,
                                 final int capacity) {
        this.capacity = Pow2.roundToPowerOfTwo(capacity);
        buffy = alignedSlice(4 * PortableJvmInfo.CACHE_LINE_SIZE + this.capacity,
            PortableJvmInfo.CACHE_LINE_SIZE, buff);

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
                return !isEmpty();
            }
        };
    }

    public static int getRequiredBufferSize(final int capacity) {
        int p2Capacity = Pow2.roundToPowerOfTwo(capacity);
        if (p2Capacity > (Pow2.MAX_POW2))
            throw new IllegalArgumentException("capacity exceeds max buffer capacity: " + Pow2.MAX_POW2);
        return 4 * PortableJvmInfo.CACHE_LINE_SIZE + p2Capacity;
    }

    private static int alignToMultipleOf4(int size) {
        return ((size + 3) / 4) * 4;
    }

    @Override
    public boolean offer(final byte[] bytes) {
        return offer(bytes, bytes.length);
    }

    @Override
    public boolean offer(final byte[] bytes, int size) {
        if (size == 0) {
            return true;
        }
        // has to be aligned in blocks of 4 bytes so that writing the size of the array never wraps in-between
        // otherwise we couldn't use putInt
        int alignedSize = alignToMultipleOf4(size);
        int totalBytesToWrite = alignedSize + 4;
        final long currentTail = getTailPlain();
        if (!hasCapacity(totalBytesToWrite, currentTail)) {
            return false;
        }

        // writes the size of the array as we want to be able to read in steps that match the written byte arrays
        UNSAFE.putInt(calcElementOffset(currentTail), size);
        long tail = currentTail + 4;
        long toBufferEnd = capacity - (tail & mask);
        if (toBufferEnd >= size) {
            UNSAFE.copyMemory(bytes, ARRAY_BASE_OFFSET, null, calcElementOffset(tail), size);
        } else {
            // when the buffer wraps, write one chunk at the end and the other chunk at the beginning
            UNSAFE.copyMemory(bytes, ARRAY_BASE_OFFSET, null, calcElementOffset(tail), toBufferEnd);
            UNSAFE.copyMemory(bytes, ARRAY_BASE_OFFSET + toBufferEnd, null, arrayBase, size - toBufferEnd);
        }
        setTail(currentTail + totalBytesToWrite);
        return true;
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

    @Override
    public void drain(MessagePassingQueue.Consumer<ByteRingBuffer> consumer, MessagePassingQueue.WaitStrategy wait, MessagePassingQueue.ExitCondition exit) {
        int idleCounter = 0;
        while (exit.keepRunning()) {
            long currentHead = getHeadPlain();
            if (isEmpty(currentHead)) {
                idleCounter = wait.idle(idleCounter);
                continue;
            }
            idleCounter = 0;
            consumer.accept(this);
        }
    }

    @Override
    public int writeTo(OutputStream os, byte[] buffer) throws IOException {
        return writeTo(os, buffer, Integer.MAX_VALUE);
    }

    @Override
    public int writeTo(OutputStream os, byte[] buffer, int limit) throws IOException {
        int i = 0;
        for (; i < limit && !isEmpty(getHeadPlain()); i++) {
            writeOne(os, buffer, getHeadPlain());
        }
        return i;
    }

    private void writeOne(OutputStream os, byte[] buffer, long currentHead) throws IOException {
        final int size = UNSAFE.getInt(calcElementOffset(currentHead));
        assert size <= capacity - 4 : String.format("%d > %d", size, capacity - 4);
        long head = currentHead + 4;
        int alignedSize = ((size + 3) / 4) * 4;
        int read = 0;
        // read multiple times if buffer wraps or if provided byte[] buffer is smaller than the size of the event
        while (read < size) {
            long toBufferEnd = capacity - (head & mask);
            long copyBytes = Math.min(Math.min(toBufferEnd, size - read), buffer.length);
            UNSAFE.copyMemory(null, calcElementOffset(head), buffer, ARRAY_BASE_OFFSET, copyBytes);
            head += copyBytes;
            read += copyBytes;
            os.write(buffer, 0, (int) copyBytes);
        }
        setHead(currentHead + 4 + alignedSize);
    }

    private boolean isEmpty(long currentHead) {
        if (currentHead >= getTailCache()) {
            setTailCache(getTail());
            if (currentHead >= getTailCache()) {
                return true;
            }
        }
        return false;
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
