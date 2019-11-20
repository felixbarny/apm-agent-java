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
package co.elastic.apm.agent.netty;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeMap;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * {@link Channel.Unsafe#disconnect(io.netty.channel.ChannelPromise)}
 */
public class ChannelDisconnectContextRemovingInstrumentation extends NettyInstrumentation {

    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(ChannelDisconnectContextRemovingInstrumentation.class);

    public ChannelDisconnectContextRemovingInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("io.netty.channel.")
            .and(hasSuperType(named("io.netty.channel.Channel$Unsafe")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("disconnect")
            .and(takesArguments(1))
            .and(takesArgument(0, named("io.netty.channel.ChannelPromise")));
    }

    @Override
    public Class<?> getAdviceClass() {
        return AdviceClass.class;
    }

    public static class AdviceClass {

        @Advice.OnMethodEnter
        private static void onBeforeDisconnect(@Advice.Origin Class<?> clazz, @Advice.Argument(0) ChannelPromise promise) {
            if (nettyContextHelper != null) {
                NettyContextHelper<AttributeMap> helper = nettyContextHelper.getForClassLoaderOfClass(clazz);
                if (helper != null) {
                    logger.debug("Channel.Unsafe#disconnect");
                    helper.removeContext(promise.channel());
                }
            }
        }
    }
}
