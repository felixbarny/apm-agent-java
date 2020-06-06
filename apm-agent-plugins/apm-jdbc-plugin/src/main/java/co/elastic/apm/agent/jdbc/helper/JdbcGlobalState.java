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
package co.elastic.apm.agent.jdbc.helper;

import co.elastic.apm.agent.bci.GlobalState;
import co.elastic.apm.agent.util.DataStructures;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;

import java.sql.Connection;

@GlobalState
public class JdbcGlobalState {

    // Important implementation note:
    //
    // because this class is potentially loaded from multiple classloaders, making those fields 'static' will not
    // have the expected behavior, thus, any direct reference to `JdbcHelperImpl` should only be obtained from the
    // HelperClassManager<JdbcHelper> instance.
    public static final WeakConcurrentMap<Object, String> statementSqlMap = DataStructures.createWeakConcurrentMapWithCleanerThread();
    public static final WeakConcurrentMap<Connection, ConnectionMetaData> metaDataMap = DataStructures.createWeakConcurrentMapWithCleanerThread();
    public static final WeakConcurrentMap<Class<?>, Boolean> metadataSupported = new WeakConcurrentMap.WithInlinedExpunction<Class<?>, Boolean>();
    public static final WeakConcurrentMap<Class<?>, Boolean> connectionSupported = new WeakConcurrentMap.WithInlinedExpunction<Class<?>, Boolean>();

    public static void clearInternalStorage() {
        metaDataMap.clear();
        metadataSupported.clear();
        connectionSupported.clear();
    }

}
