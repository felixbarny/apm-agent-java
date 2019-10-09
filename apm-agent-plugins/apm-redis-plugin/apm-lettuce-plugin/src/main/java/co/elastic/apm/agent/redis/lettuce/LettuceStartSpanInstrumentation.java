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
package co.elastic.apm.agent.redis.lettuce;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.redis.RedisSpanUtils;
import io.lettuce.core.protocol.RedisCommand;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Starts a span in {@link io.lettuce.core.RedisChannelHandler#dispatch(RedisCommand)}
 *
 * The context will be propagated via the Netty instrumentation
 */
public class LettuceStartSpanInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.lettuce.core.RedisChannelHandler");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("dispatch")
            .and(returns(named("io.lettuce.core.protocol.RedisCommand")))
            .and(takesArguments(1))
            .and(takesArgument(0, named("io.lettuce.core.protocol.RedisCommand")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("redis", "lettuce");
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    private static void beforeDispatch(@Advice.Argument(0) RedisCommand command, @Advice.Local("span") Span span) {
        span = RedisSpanUtils.createRedisSpan(command.getType().name());
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    private static void afterDispatch(@Nullable @Advice.Local("span") Span span) {
        if (span != null) {
            span.deactivate();
        }
    }
}
