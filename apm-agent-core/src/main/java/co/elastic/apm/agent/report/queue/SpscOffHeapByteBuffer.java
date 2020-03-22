package co.elastic.apm.agent.report.queue;

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
public class SpscOffHeapByteBuffer {
    public final static byte PRODUCER = 1;
    public final static byte CONSUMER = 2;
    // Cached array base offset
    private static final long ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
    // 24b,8b,8b,24b | pad | 24b,8b,8b,24b | pad
    private final ByteBuffer buffy;
    private final long headAddress;
    private final long tailCacheAddress;
    private final long tailAddress;
    private final long headCacheAddress;

    private final int capacity;
    private final int mask;
    private final long arrayBase;

    public SpscOffHeapByteBuffer(final int capacity) {
        this(allocateAlignedByteBuffer(
            getRequiredBufferSize(capacity),
            PortableJvmInfo.CACHE_LINE_SIZE),
            Pow2.roundToPowerOfTwo(capacity), (byte) (PRODUCER | CONSUMER));
    }

    /**
     * This is to be used for an IPC queue with the direct buffer used being a memory
     * mapped file.
     *
     * @param buff
     * @param capacity
     * @param viewMask
     */
    public SpscOffHeapByteBuffer(final ByteBuffer buff,
                                 final int capacity, byte viewMask) {
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
        if ((viewMask & PRODUCER) == PRODUCER) {
            setHeadCache(0);
            setTail(0);
        }
        // consumer owns head and tailCache
        if ((viewMask & CONSUMER) == CONSUMER) {
            setTailCache(0);
            setHead(0);
        }
        mask = this.capacity - 1;
    }

    public static int getRequiredBufferSize(final int capacity) {
        int p2Capacity = Pow2.roundToPowerOfTwo(capacity);
        if (p2Capacity > (Pow2.MAX_POW2))
            throw new IllegalArgumentException("capacity exceeds max buffer capacity: " + Pow2.MAX_POW2);
        return 4 * PortableJvmInfo.CACHE_LINE_SIZE + p2Capacity;
    }


    public boolean offer(final byte[] bytes) {
        return offer(bytes, bytes.length);
    }

    public boolean offer(final byte[] bytes, int size) {
        // has to be aligned in blocks of 4 bytes so that writing the size of the array never wraps in-between
        // otherwise we couldn't use putInt
        int alignedSize = alignToMultipleOf4(size);
        final long currentTail = getTailPlain();
        long tail = currentTail;
        int totalBytesToWrite = alignedSize + 4;
        if (!hasRemaining(totalBytesToWrite, currentTail)) {
            return false;
        }

        // writes the size of the array as we want to be able to read in steps that match the written byte arrays
        UNSAFE.putInt(calcElementOffset(tail), size);
        tail += 4;
        int written = 0;
        while (written < size) {
            long copyBytes = Math.min(capacity - (tail & mask), size - written);
            UNSAFE.copyMemory(bytes, ARRAY_BASE_OFFSET + written, null, calcElementOffset(tail), copyBytes);
            tail += copyBytes;
            written += copyBytes;
        }
        setTail(currentTail + totalBytesToWrite);
        return true;
    }

    private static int alignToMultipleOf4(int size) {
        return ((size + 3) / 4) * 4;
    }

    private boolean hasRemaining(int requestedCapacity, long currentTail) {
        final long wrapPoint = currentTail - capacity + requestedCapacity;
        if (getHeadCache() < wrapPoint) {
            setHeadCache(getHead());
            if (getHeadCache() < wrapPoint) {
                return false;
            }
        }
        return true;
    }

    public void writeTo(OutputStream os) throws IOException {
        writeTo(os, new byte[1024]);
    }

    public void writeTo(OutputStream os, byte[] buffer) throws IOException {
        for (long head = getHeadPlain(); hasMore(head); head = getHeadPlain()) {
            final long currentHead = head;
            final int size = UNSAFE.getInt(calcElementOffset(head));
            assert size <= capacity - 4 : String.format("%d > %d", size, capacity - 4);
            head += 4;
            int alignedSize = ((size + 3) / 4) * 4;
            int read = 0;
            while (read < size) {
                long copyBytes = Math.min(Math.min(capacity - (head & mask), size - read), buffer.length);
                UNSAFE.copyMemory(null, calcElementOffset(head), buffer, ARRAY_BASE_OFFSET + read, copyBytes);
                head += copyBytes;
                read += copyBytes;
                os.write(buffer, 0, (int) copyBytes);
            }
            setHead(currentHead + 4 + alignedSize);
        }
    }

    private boolean hasMore(long currentHead) {
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
