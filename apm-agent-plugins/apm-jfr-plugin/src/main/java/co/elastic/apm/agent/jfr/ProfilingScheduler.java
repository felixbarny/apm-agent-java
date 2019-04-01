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

import co.elastic.apm.agent.context.LifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.util.ExecutorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ProfilingScheduler implements LifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(ProfilingScheduler.class);

    @Nullable
    private ScheduledExecutorService scheduler;

    @Override
    public void start(ElasticApmTracer tracer) {
        final ProfilerConfiguration config = tracer.getConfig(ProfilerConfiguration.class);
        final long delay = config.getProfilingDelay().getMillis();
        if (delay > 0 && isJfrAvailable()) {
            scheduler = ExecutorUtils.createSingleThreadSchedulingDeamonPool("profiler-scheduler", 1);
            scheduler.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    try {
                        final FlightRecorderProfiler profiler = new FlightRecorderProfiler(Duration.ofMillis(config.getProfilingSamplingInterval().getMillis()));
                        profiler.withThreadLabels()
                            .activateNativeMethodSample()
                            .withTransactionLabels(Duration.ofMillis(config.getProfilingMinTransactionActivation().getMillis()))
                            .start();
                        Thread.sleep(config.getProfilingDuration().getMillis());
                        profiler.stop().exportFlamegraphSvgs(Paths.get("/Users/felixbarnsteiner/projects/github/brendangregg/FlameGraph/flamegraph.pl"), Paths.get("flamegraphs"));
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                        throw new RuntimeException(e);
                    }
                }
            }, delay, delay, TimeUnit.MILLISECONDS);
        }
    }

    private boolean isJfrAvailable() {
        try {
            Class.forName("jdk.jfr.Enabled", false, ProfilingScheduler.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void stop() throws Exception {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
