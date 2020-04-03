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
package co.elastic.apm.agent.dubbo.advice;

import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.dubbo.helper.ApacheDubboAttachmentHelper;
import co.elastic.apm.agent.dubbo.helper.AsyncCallbackCreator;
import co.elastic.apm.agent.dubbo.helper.DubboTraceHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;

@VisibleForAdvice
public class ApacheMonitorFilterAdvice {

    @VisibleForAdvice
    public static ElasticApmTracer tracer;

    @VisibleForAdvice
    public static HelperClassManager<ApacheDubboAttachmentHelper> attachmentHelperClassManager;

    @VisibleForAdvice
    public static HelperClassManager<AsyncCallbackCreator> asyncCallbackCreatorClassManager;

    public static void init(ElasticApmTracer tracer) {
        ApacheMonitorFilterAdvice.tracer = tracer;
        DubboTraceHelper.init(tracer);
        attachmentHelperClassManager = HelperClassManager.ForAnyClassLoader.of(tracer,
            "co.elastic.apm.agent.dubbo.helper.ApacheDubboAttachmentHelperImpl");
        asyncCallbackCreatorClassManager = HelperClassManager.ForAnyClassLoader.of(tracer,
            "co.elastic.apm.agent.dubbo.helper.AsyncCallbackCreatorImpl");
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnterFilterInvoke(@Advice.Argument(1) Invocation invocation,
                                           @Advice.Local("span") Span span,
                                           @Advice.Local("transaction") Transaction transaction,
                                           @Advice.Local("scope") Scope scope) {
        RpcContext context = RpcContext.getContext();
        ApacheDubboAttachmentHelper helper = attachmentHelperClassManager.getForClassLoaderOfClass(Invocation.class);
        if (helper == null) {
            return;
        }
        // for consumer side, just create span, more information will be collected in provider side
        if (context.isConsumerSide()) {
            if (tracer == null || tracer.getActive() == null) {
                return;
            }

            span = DubboTraceHelper.createConsumerSpan(invocation.getInvoker().getInterface(), invocation.getMethodName(), invocation.getParameterTypes(),
                context.getUrl().getParameter("version"), context.getRemoteAddress());
            if (span != null) {
                span.getTraceContext().setOutgoingTraceContextHeaders(invocation, helper);
            }
            return;
        }


        // for provider side
        transaction = tracer.startChildTransaction(invocation, helper, Invocation.class.getClassLoader());
        if (transaction != null) {
            scope = transaction.activateInScope();
            DubboTraceHelper.fillTransaction(transaction, invocation.getInvoker().getInterface(),
                invocation.getMethodName(),
                invocation.getParameterTypes(),
                context.getUrl().getParameter("version"));
        }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExitFilterInvoke(@Advice.Argument(1) Invocation invocation,
                                          @Advice.Return Result result,
                                          @Advice.Local("span") final Span span,
                                          @Advice.Thrown Throwable t,
                                          @Advice.Local("transaction") Transaction transaction,
                                          @Advice.Local("scope") Scope scope) {

        RpcContext context = RpcContext.getContext();
        AbstractSpan<?> actualSpan = context.isConsumerSide() ? span : transaction;
        AsyncCallbackCreator callbackCreator = asyncCallbackCreatorClassManager.getForClassLoaderOfClass(AppResponse.class);
        if (actualSpan == null || callbackCreator == null) {
            return;
        }

        try {
            if (scope != null) {
                scope.close();
            }
            if (result instanceof AsyncRpcResult) {
                AsyncRpcResult asyncResult = (AsyncRpcResult) result;
                asyncResult.getResponseFuture().whenComplete(callbackCreator.create(actualSpan, invocation.getArguments()));
            }
        } finally {
            actualSpan.deactivate();
        }
    }
}
