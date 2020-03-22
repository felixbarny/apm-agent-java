package co.elastic.apm.agent.report.queue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

public class SpscOffHeapByteBufferTest {

    private SpscOffHeapByteBuffer buffer;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        buffer = new SpscOffHeapByteBuffer(16);
        executorService = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() {
        executorService.shutdown();
    }

    @Test
    void testOfferEmpty() throws IOException {
        buffer.offer(new byte[]{'c', 'a', 'f', 'e'}, 0);
        assertThat(readAll()).isEqualTo(new byte[]{});
    }

    @Test
    void testOfferOne() throws IOException {
        buffer.offer(new byte[]{'c', 'a', 'f', 'e'});
        assertThat(readAll()).isEqualTo(new byte[]{'c', 'a', 'f', 'e'});
    }

    @Test
    void testOfferFullCapacity() throws IOException {
        assertThat(buffer.offer(getBytes(12))).isTrue();
        assertThat(readAll()).isEqualTo(getBytes(12));
    }

    @Test
    void testOfferTooMuch() throws IOException {
        assertThat(buffer.offer(getBytes(13))).isFalse();
        assertThat(readAll()).isEqualTo(new byte[0]);
    }

    @Test
    void testOfferTwo() throws IOException {
        assertThat(buffer.offer(new byte[]{'c', 'a', 'f', 'e'})).isTrue();
        assertThat(buffer.offer(new byte[]{'b', 'a', 'b', 'e'})).isTrue();
        assertThat(readAll()).isEqualTo(new byte[]{'c', 'a', 'f', 'e', 'b', 'a', 'b', 'e'});
    }

    @Test
    void testOfferUntilFull() throws IOException {
        assertThat(buffer.offer(new byte[]{'c', 'a', 'f', 'e'})).isTrue();
        assertThat(buffer.offer(new byte[]{'b', 'a', 'b', 'e'})).isTrue();
        assertThat(buffer.offer(new byte[]{'c', 'a', 'f', 'e'})).isFalse();
        assertThat(readAll()).isEqualTo(new byte[]{'c', 'a', 'f', 'e', 'b', 'a', 'b', 'e'});
    }

    @Test
    void testOfferWrapAligned() throws IOException {
        assertThat(buffer.offer(new byte[]{'c', 'a', 'f', 'e'})).isTrue();
        assertThat(readAll()).isEqualTo(new byte[]{'c', 'a', 'f', 'e'});
        assertThat(buffer.offer(new byte[]{'b', 'a', 'b', 'e'})).isTrue();
        assertThat(buffer.offer(new byte[]{'f', 'o', 'o', 'o'})).isTrue();
        assertThat(readAll()).isEqualTo(new byte[]{'b', 'a', 'b', 'e', 'f', 'o', 'o', 'o'});
    }

    @Test
    void testOfferWrapNonAligned() throws IOException {
        assertThat(buffer.offer(new byte[]{'f', 'o', 'o'})).isTrue();
        assertThat(readAll()).isEqualTo(new byte[]{'f', 'o', 'o'});
        assertThat(buffer.offer(new byte[]{'b', 'a', 'r'})).isTrue();
        assertThat(buffer.offer(new byte[]{'b', 'a', 'z'})).isTrue();
        assertThat(readAll()).isEqualTo(new byte[]{'b', 'a', 'r', 'b', 'a', 'z'});
    }

    @Test
    void testOfferWrapNonAligned2() throws IOException {
        buffer = new SpscOffHeapByteBuffer(32);
        // put 24, 8 left
        assertThat(buffer.offer(getBytes(20))).isTrue();
        assertThat(readAll()).hasSize(20);
        // put another 24, wraps after 4 bytes
        assertThat(buffer.offer(getBytes(20))).isTrue();
        assertThat(readAll()).hasSize(20);
    }

    @Test
    void testAsyncConsumer() throws Exception {
        buffer = new SpscOffHeapByteBuffer(32);
        Future<byte[]> bytesFuture = executorService.submit(() -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            buffer.drain(buf -> {
                try {
                    buf.writeTo(baos, new byte[16], 1);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, idleCounter -> idleCounter, () -> baos.size() < 2);
            return baos.toByteArray();
        });

        assertThat(buffer.offer(getBytes(1))).isTrue();
        assertThat(buffer.offer(getBytes(1))).isTrue();
        assertThat(buffer.offer(getBytes(1))).isTrue();
        assertThat(bytesFuture.get()).hasSize(2);
        assertThat(readAll()).hasSize(1);
    }

    private byte[] getBytes(int count) {
        byte[] bytes = new byte[count];
        for (int i = 0; i < count; i++) {
            bytes[i] = (byte) i;

        }
        return bytes;
    }

    @Test
    void testLoopWrap() throws IOException {
        for (byte b = -127; b < 127; b++) {
            byte[] bytes = {b, b, b};
            assertThat(buffer.offer(bytes)).isTrue();
            assertThat(readAll()).isEqualTo(bytes);
        }
    }

    private byte[] readAll() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        buffer.writeTo(os, new byte[4]);
        return os.toByteArray();
    }
}
