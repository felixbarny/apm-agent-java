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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.impl.transaction.Transaction;

import javax.servlet.http.HttpServletRequest;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;

import static co.elastic.apm.agent.bci.ElasticApmInstrumentation.tracer;

public class ServletTestHelper {

    public static void register(Map<String, Object> registry) {
        try {
            MethodHandle getUri = MethodHandles.lookup().findStatic(ServletTestHelper.class, "addUriLabel", MethodType.methodType(void.class, HttpServletRequest.class));
            registry.put("addUriLabel", getUri);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void addUriLabel(HttpServletRequest request) {
        if (tracer != null) {
            Transaction transaction = tracer.currentTransaction();
            if (transaction != null) {
                transaction.addLabel("url", request.getRequestURI());
            }
        }
    }
}
