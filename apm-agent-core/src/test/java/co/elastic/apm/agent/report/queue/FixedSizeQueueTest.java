package co.elastic.apm.agent.report.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FixedSizeQueueTest {

    private FixedSizeQueue<String> queue;

    @BeforeEach
    void setUp() {
        queue = new FixedSizeQueue<String>(2);
    }

    @Test
    void offerOne() {
        queue.offer("foo");
        assertThat(queue.size()).isEqualTo(1);
        assertThat(asList(queue)).containsExactlyInAnyOrder("foo");
    }

    @Test
    void testConsumeEmptiesQueue() {
        queue.offer("foo");
        assertThat(queue.size()).isEqualTo(1);
        assertThat(asList(queue)).containsExactlyInAnyOrder("foo");
        assertThat(queue.size()).isEqualTo(0);
        assertThat(asList(queue)).isEmpty();
    }

    @Test
    void offerSize() {
        assertThat(queue.offer("foo")).isTrue();
        assertThat(queue.offer("bar")).isTrue();
        assertThat(queue.size()).isEqualTo(2);
        assertThat(asList(queue)).containsExactlyInAnyOrder("foo", "bar");
    }

    @Test
    void offerOverSize() {
        assertThat(queue.offer("foo")).isTrue();
        assertThat(queue.offer("bar")).isTrue();
        assertThat(queue.offer("baz")).isFalse();
        assertThat(queue.size()).isEqualTo(2);
        assertThat(asList(queue)).containsExactlyInAnyOrder("foo", "bar");
    }

    private static <E> List<E> asList(FixedSizeQueue<E> queue) {
        List<E> result = new ArrayList<>();
        queue.drain(result::add);
        assertThat(queue.size()).isEqualTo(0);
        return result;
    }
}
