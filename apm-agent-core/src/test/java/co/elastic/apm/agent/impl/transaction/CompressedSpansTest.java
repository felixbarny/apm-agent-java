package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class CompressedSpansTest {

    private ElasticApmTracer tracer;
    private MockReporter reporter;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        reporter.disableDestinationAddressCheck();
        tracer = MockTracer.createRealTracer(reporter);
    }

    @Test
    void testCompressDuplicateSpan() {
        Transaction transaction = tracer.startRootTransaction(null);

        createDbSpan(transaction, "a");
        createDbSpan(transaction, "a");

        transaction.end();

        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getSpanByName("a").getCompressedCount()).isEqualTo(2);
    }

    @Test
    void testReuseCompressedSpans() {
        Transaction transaction = tracer.startRootTransaction(null);

        Span a1 = createDbSpan(transaction, "a");
        Span a2 = createDbSpan(transaction, "a");
        Span a3 = createDbSpan(transaction, "a");

        assertThat(a1).isNotSameAs(a2);
        assertThat(a2).isSameAs(a3);

        transaction.end();

        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getSpanByName("a").getCompressedCount()).isEqualTo(3);
    }

    @Test
    void testCompressSameDestination() {
        when(tracer.getConfig(CoreConfiguration.class).getSpanMinDuration()).thenReturn(TimeDuration.of("1s"));
        Transaction transaction = tracer.startRootTransaction(null);

        createDbSpan(transaction, "a");
        createDbSpan(transaction, "b");

        transaction.end();

        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getSpanByName("Calls to mysql").getCompressedCount()).isEqualTo(2);
    }

    @Test
    void testNoCompressionWhenContextHasBeenPropagated() {
        Transaction transaction = tracer.startRootTransaction(null);

        createPropagatingHttpSpan(transaction, "GET elastic.co");
        createPropagatingHttpSpan(transaction, "GET elastic.co");

        transaction.end();

        assertThat(reporter.getSpans()).hasSize(2);
    }

    @Test
    void testCompressDuplicateSpanWithParentSpan() {
        Transaction transaction = tracer.startRootTransaction(null);
        Span parentSpan = transaction.createSpan().withName("parent span");

        createDbSpan(parentSpan, "a");
        createDbSpan(parentSpan, "a");

        parentSpan.end();
        transaction.end();

        assertThat(reporter.getSpans()).hasSize(2);
        assertThat(reporter.getSpanByName("a").getCompressedCount()).isEqualTo(2);
        assertThat(reporter.getSpanByName("parent span").getCompressedCount()).isEqualTo(0);

    }

    @Test
    void testNoDuplicate() {
        Transaction transaction = tracer.startRootTransaction(null);

        createDbSpan(transaction, "a");
        createDbSpan(transaction, "b");

        transaction.end();

        assertThat(reporter.getSpans()).hasSize(2);
        assertThat(reporter.getSpanByName("a")).isNotNull();
        assertThat(reporter.getSpanByName("b")).isNotNull();
    }

    @Test
    void testDuplicateAndNoDuplicate() {
        Transaction transaction = tracer.startRootTransaction(null);

        createDbSpan(transaction, "a");
        createDbSpan(transaction, "a");
        createDbSpan(transaction, "b");
        createDbSpan(transaction, "c");
        createDbSpan(transaction, "c");

        transaction.end();

        assertThat(reporter.getSpans()).hasSize(3);
        assertThat(reporter.getSpanByName("a").getCompressedCount()).isEqualTo(2);
        assertThat(reporter.getSpanByName("b").getCompressedCount()).isEqualTo(0);
        assertThat(reporter.getSpanByName("c").getCompressedCount()).isEqualTo(2);
    }

    private Span createDbSpan(AbstractSpan<?> parent, String spanName) {
        Span span = parent.createExitSpan()
            .withType("db")
            .withSubtype("mysql")
            .withName(spanName);
        span.getContext().getDestination().getService().withName("mysql").withResource("mysql").withType("db");
        span.end();
        return span;
    }

    private void createPropagatingHttpSpan(AbstractSpan<?> parent, String name) {
        Span span = parent.createExitSpan()
            .withType("external")
            .withSubtype("http")
            .withName(name);
        span.getContext().getDestination().getService().withName("http://elastic.co").withResource("elastic.co:80").withType("external");
        span.propagateTraceContext(new HashMap<String, String>(), (k, v, m) -> m.put(k, v));
        span.end();
    }
}
