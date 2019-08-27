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
package co.elastic.apm.agent.dispatcher;

import co.elastic.apm.agent.bci.HelperClassManager;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.dynamic.loading.MultipleParentClassLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;

import static org.assertj.core.api.Assertions.assertThat;

class HelperDispatcherTest {

    static {
        System.getProperties().put("HelperDispatcherTest.classLoader", HelperDispatcherTest.class.getClassLoader());
    }

    @BeforeEach
    @AfterEach
    void setUp() {
        System.getProperties().remove("HelperDispatcherTest.classLoader");
    }

    @Test
    void injectionTest() throws Exception {
        URLClassLoader cl = new URLClassLoader(new URL[]{}, null);

        HelperClassManager.ForHelperDispatcher.inject(cl, null, getClass().getName());

        Class<?> helperDispatcherInOtherCl = Class.forName(HelperDispatcher.class.getName(), false, cl);
        assertThat(helperDispatcherInOtherCl).isNotSameAs(HelperDispatcher.class);

        ClassLoader helperClassLoader = (ClassLoader) System.getProperties().get("HelperDispatcherTest.classLoader");
        assertThat(helperClassLoader).isNotNull();
        assertThat(helperClassLoader).isNotSameAs(getClass().getClassLoader());
        assertThat(helperClassLoader).isInstanceOf(ByteArrayClassLoader.ChildFirst.class);
        assertThat(helperClassLoader.getParent()).isInstanceOf(MultipleParentClassLoader.class);
    }
}
