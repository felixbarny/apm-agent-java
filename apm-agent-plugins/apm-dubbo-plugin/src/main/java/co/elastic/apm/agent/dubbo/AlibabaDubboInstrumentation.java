/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.dubbo;

import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.dubbo.advice.AlibabaDubboFilterAdvice;
import co.elastic.apm.agent.dubbo.helper.DubboAttachmentHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import com.alibaba.dubbo.rpc.Invocation;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class AlibabaDubboInstrumentation extends AbstractDubboInstrumentation {

    @VisibleForAdvice
    public static HelperClassManager<DubboAttachmentHelper<Invocation>> alibabaDubboAttachmentHelperClassManager;

    public AlibabaDubboInstrumentation(ElasticApmTracer tracer) {
        AlibabaDubboFilterAdvice.init(tracer);
        alibabaDubboAttachmentHelperClassManager = HelperClassManager.ForAnyClassLoader.of(tracer,
            "co.elastic.apm.agent.dubbo.helper.AlibabaDubboAttachmentHelper");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("com.alibaba.dubbo.monitor.support.MonitorFilter");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("invoke")
            .and(takesArgument(0, named("com.alibaba.dubbo.rpc.Invoker")))
            .and(takesArgument(1, named("com.alibaba.dubbo.rpc.Invocation")));
    }

    @Override
    public Class<?> getAdviceClass() {
        return AlibabaDubboFilterAdvice.class;
    }

}
