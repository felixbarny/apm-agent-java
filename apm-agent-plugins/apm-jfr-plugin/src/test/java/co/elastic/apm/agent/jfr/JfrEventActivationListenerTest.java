package co.elastic.apm.agent.jfr;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class JfrEventActivationListenerTest {

    private ElasticApmTracer tracer;
    private MockReporter reporter;

    @Before
    public void setUp() {
        reporter = new MockReporter();
        tracer = MockTracer.createRealTracer(reporter);
    }

    @Test
    public void endToEndTest() throws Exception {

        final FlightRecorderProfiler profiler = new FlightRecorderProfiler(Duration.ofMillis(10))
            .withThreadLabels()
            .withTransactionLabels(Duration.ofMillis(1))
            .includeThreads(Thread.currentThread())
            .start();

        createSpans();

        profiler.stop()
            .exportFlamegraphSvgs(Paths.get("/Users/felixbarnsteiner/projects/github/brendangregg/FlameGraph/flamegraph.pl"), Paths.get("flamegraphs"));

        var labels = Map.of(
            "transactionId", reporter.getFirstTransaction().getTraceContext().getTransactionId().toString(),
            "thread", Thread.currentThread().getName()
        );
        assertThat(profiler.getCallTreeByLabels().keySet()).contains(labels);

        System.out.println("string");
        profiler.getCallTreeByLabels().get(labels).toString(System.out);
        System.out.println("json");
        profiler.getCallTreeByLabels().get(labels).toJson(System.out);
        System.out.println();
        System.out.println("folded");
        profiler.getCallTreeByLabels().get(labels).toFolded(System.out);
    }

    private void createSpans() {
        final Transaction transaction = tracer.startRootTransaction(getClass().getClassLoader()).withName("transaction");
        try (Scope s1 = transaction.activateInScope()) {
            final Span span = tracer.getActive().createSpan().withName("span");
            try (Scope s2 = span.activateInScope()) {
                fibonacci(42);
            } finally {
                span.end();
            }
        } finally {
            transaction.end();
        }
    }

    private long fibonacci(long n) {
        return n < 2 ? n : fibonacci(n - 1) + fibonacci(n - 2);
    }

}
