package co.elastic.apm.agent.report.queue;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.jctools.queues.SpscArrayQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

public class QueueProcessorTest {

    private static final int THREAD_LOCAL_CAPACITY = 4;
    private QueueProcessor<String> queueProcessor;
    private List<String> processedEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MutableRunnableThread thread = new MutableRunnableThread("processing");
        queueProcessor = new QueueProcessor<>(() -> new SpscArrayQueue<>(THREAD_LOCAL_CAPACITY + 1), thread, processedEvents::add, 100_000_000L, 100, 1000);
        queueProcessor.start(mock(ElasticApmTracer.class));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        queueProcessor.stop();
    }

    @Test
    void testConsumeOne() {
        testProcessing(List.of("foo"));
    }

    @Test
    void testOverThreadLocalCapacity() {
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < THREAD_LOCAL_CAPACITY + 1; i++) {
            expected.add(Integer.toString(i));
        }
        testProcessing(expected);
    }

    private void testProcessing(List<String> events) {
        for (String event : events) {
            queueProcessor.offer(event);
        }
        await().untilAsserted(() -> assertThat(processedEvents).isEqualTo(events));
    }
}
