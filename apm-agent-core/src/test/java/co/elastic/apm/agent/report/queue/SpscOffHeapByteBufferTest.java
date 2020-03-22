package co.elastic.apm.agent.report.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class SpscOffHeapByteBufferTest {

    private SpscOffHeapByteBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new SpscOffHeapByteBuffer(16);
    }

    @Test
    void testOfferOne() throws IOException {
        buffer.offer(new byte[]{'c', 'a', 'f', 'e'});
        assertThat(readAll()).isEqualTo(new byte[]{'c', 'a', 'f', 'e'});
    }

    @Test
    void testOfferFullCapacity() throws IOException {
        byte[] bytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        assertThat(buffer.offer(bytes)).isTrue();
        assertThat(readAll()).isEqualTo(bytes);
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
        buffer.writeTo(os);
        return os.toByteArray();
    }
}
