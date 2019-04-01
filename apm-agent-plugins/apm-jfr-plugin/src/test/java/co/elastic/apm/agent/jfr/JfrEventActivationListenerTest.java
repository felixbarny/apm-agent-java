/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
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
//            .includeThreads(Thread.currentThread())
            .start();

        createSpans();

        profiler.stop()
            .exportFlamegraphSvgs(Paths.get("/Users/felixbarnsteiner/projects/github/brendangregg/FlameGraph/flamegraph.pl"), Paths.get("flamegraphs"));

        Map<String, String> labels = Map.of(
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
