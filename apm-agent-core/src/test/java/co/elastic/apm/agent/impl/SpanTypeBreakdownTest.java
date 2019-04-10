package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpanTypeBreakdownTest {

    private MockReporter reporter;
    private ElasticApmTracer tracer;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        tracer = MockTracer.createRealTracer(reporter);
    }

    /*
     * [ test transaction            ]
     * `---------[db span  ]
     * 0         10        20        30
     */
    @Test
    void testBreakdown_singleDbSpan() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test transaction")
            .withType("request");
        transaction.createSpan(10).withType("db").end(20);
        transaction.end(30);

        assertThat(getSelfTimer("transaction").getCount()).isEqualTo(1);
        assertThat(getSelfTimer("transaction").getTotalTimeNs()).isEqualTo(20);
        assertThat(getSelfTimer("db").getCount()).isEqualTo(1);
        assertThat(getSelfTimer("db").getTotalTimeNs()).isEqualTo(10);
    }

    /*
     * [ test transaction           ]
     * +---------[db span  ]
     * `---------[db span  ]
     * 0         10        20        30
     */
    @Test
    void testBreakdown_concurrentDbSpans_fullyOverlapping() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test transaction")
            .withType("request");
        final Span span1 = transaction.createSpan(10).withType("db");
        final Span span2 = transaction.createSpan(10).withType("db");
        span1.end(20);
        span2.end(20);
        transaction.end(30);

        assertThat(getSelfTimer("transaction").getCount()).isEqualTo(1);
        assertThat(getSelfTimer("transaction").getTotalTimeNs()).isEqualTo(20);
        assertThat(getSelfTimer("db").getCount()).isEqualTo(2);
        assertThat(getSelfTimer("db").getTotalTimeNs()).isEqualTo(20);
    }

    /*
     * [ test transaction            ]
     * +---------[db span  ]
     * `--------------[db span  ]
     * 0         10        20        30
     */
    @Test
    void testBreakdown_concurrentDbSpans_partiallyOverlapping() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test transaction")
            .withType("request");
        final Span span1 = transaction.createSpan(10).withType("db");
        final Span span2 = transaction.createSpan(15).withType("db");
        span1.end(20);
        span2.end(25);
        transaction.end(30);

        assertThat(getSelfTimer("transaction").getCount()).isEqualTo(1);
        assertThat(getSelfTimer("transaction").getTotalTimeNs()).isEqualTo(15);
        assertThat(getSelfTimer("db").getCount()).isEqualTo(2);
        assertThat(getSelfTimer("db").getTotalTimeNs()).isEqualTo(20);
    }

    /*
     * [ test transaction            ]
     * +----[db span  ]
     * `--------------[db span  ]
     * 0         10        20        30
     */
    @Test
    void testBreakdown_serialDbSpans_notOverlapping() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test transaction")
            .withType("request");
        transaction.createSpan(5).withType("db").end(15);
        transaction.createSpan(15).withType("db").end(25);
        transaction.end(30);

        assertThat(getSelfTimer("transaction").getCount()).isEqualTo(1);
        assertThat(getSelfTimer("transaction").getTotalTimeNs()).isEqualTo(10);
        assertThat(getSelfTimer("db").getCount()).isEqualTo(2);
        assertThat(getSelfTimer("db").getTotalTimeNs()).isEqualTo(20);
    }

    /*
     * [ test transaction            ]
     * +---------[app span ]
     *  `-------------[db span  ]
     * 0         10        20        30
     */
    @Test
    void testBreakdown_asyncGrandchildExceedsChild() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test transaction")
            .withType("request");
        final Span app = transaction.createSpan(10).withType("app");
        final Span db = app.createSpan(15).withType("db");
        app.end(20);
        db.end(25);
        transaction.end(30);

        assertThat(getSelfTimer("transaction").getCount()).isEqualTo(1);
        assertThat(getSelfTimer("transaction").getTotalTimeNs()).isEqualTo(20);
        assertThat(getSelfTimer("app").getCount()).isEqualTo(1);
        assertThat(getSelfTimer("app").getTotalTimeNs()).isEqualTo(5);
        assertThat(getSelfTimer("db").getCount()).isEqualTo(1);
        assertThat(getSelfTimer("db").getTotalTimeNs()).isEqualTo(5); // or should it be 10?
    }

    /*
     * [ test transaction  ]
     * `---------[db span            ]
     * 0         10        20        30
     */
    @Test
    void testBreakdown_singleDbSpan_exceedingParent() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test transaction")
            .withType("request");
        final Span span = transaction.createSpan(10).withType("db");
        transaction.end(20);
        span.end(30);

        assertThat(getSelfTimer("transaction").getCount()).isEqualTo(1);
        assertThat(getSelfTimer("transaction").getTotalTimeNs()).isEqualTo(10);
        assertThat(getSelfTimer("db").getCount()).isEqualTo(1);
        assertThat(getSelfTimer("transaction").getTotalTimeNs()).isEqualTo(10);
    }

    @Nonnull
    private Timer getSelfTimer(String spanType) {
        return tracer.getMetricRegistry().timer("self_time", Map.of("span.type", spanType, "transaction.type", "request", "transaction.name", "test transaction"));
    }
}
