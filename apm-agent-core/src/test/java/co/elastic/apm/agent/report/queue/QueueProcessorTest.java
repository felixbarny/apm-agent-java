package co.elastic.apm.agent.report.queue;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.jctools.queues.MpscChunkedArrayQueue;
import org.jctools.queues.SpscArrayQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class QueueProcessorTest {

    private static final int THREAD_LOCAL_CAPACITY = 4;
    private QueueProcessor<String> queueProcessor;
    private List<String> processedEvents = new ArrayList<>();
    private SignallingMessagePassingQueue<String> queue;
    private UnparkOnSignalWaitStrategy waitStrategy;

    @BeforeEach
    void setUp() {
        MutableRunnableThread thread = new MutableRunnableThread();
        waitStrategy = spy(new UnparkOnSignalWaitStrategy(thread, 100_000_000L));
        queue = new SignallingMessagePassingQueue<>(new ThreadLocalQueue<>(() -> new SpscArrayQueue<>(THREAD_LOCAL_CAPACITY), new MpscChunkedArrayQueue<>(4)), waitStrategy);
        queueProcessor = new QueueProcessor<>(queue, thread, waitStrategy, processedEvents::add, e -> {});
        queueProcessor.start(mock(ElasticApmTracer.class));
    }

    @AfterEach
    void tearDown() {
        queueProcessor.stop();
    }

    @Test
    void testConsumeOne() {
        testProcessing(List.of("foo"));

        verify(waitStrategy).signal();
    }

    @Test
    void testOverThreadLocalCapacity() {
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < THREAD_LOCAL_CAPACITY + 1; i++) {
            expected.add(Integer.toString(i));
        }
        testProcessing(expected);

        verify(waitStrategy, times(expected.size())).signal();
    }

    private void testProcessing(List<String> events) {
        for (String event : events) {
            queue.offer(event);
        }
        await().untilAsserted(() -> assertThat(processedEvents).isEqualTo(events));
    }
}
