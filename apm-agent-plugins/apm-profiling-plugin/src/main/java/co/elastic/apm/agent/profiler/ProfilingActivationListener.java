/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
package co.elastic.apm.agent.profiler;

import co.elastic.apm.agent.impl.ActivationListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;

import java.util.Objects;

public class ProfilingActivationListener implements ActivationListener {

    private final ElasticApmTracer tracer;
    private final SamplingProfiler profiler;

    public ProfilingActivationListener(ElasticApmTracer tracer) {
        this(tracer, Objects.requireNonNull(tracer.getLifecycleListener(SamplingProfiler.class)));
    }

    ProfilingActivationListener(ElasticApmTracer tracer, SamplingProfiler profiler) {
        this.tracer = tracer;
        this.profiler = profiler;
    }

    @Override
    public void beforeActivate(TraceContextHolder<?> context) throws Throwable {
        if (!context.isSampled()) {
            return;
        }
        profiler.onActivation(Thread.currentThread(), context, tracer.getActive());
    }

    @Override
    public void afterDeactivate(TraceContextHolder<?> deactivatedContext) throws Throwable {
        if (!deactivatedContext.isSampled()) {
            return;
        }
        profiler.onDeactivation(Thread.currentThread(), deactivatedContext, tracer.getActive());
    }
}
