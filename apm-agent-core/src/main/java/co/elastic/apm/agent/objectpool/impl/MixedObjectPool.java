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
package co.elastic.apm.agent.objectpool.impl;

import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.ThreadAware;
import org.jctools.queues.MpmcArrayQueue;

import javax.annotation.Nullable;
import java.io.IOException;

public class MixedObjectPool<T extends ThreadAware> extends AbstractObjectPool<T> {

    private final ObjectPool<T> primaryPool;
    private final ObjectPool<T> secondaryPool;

    public static <T extends ThreadAware> MixedObjectPool<T> withThreadLocalBuffer(int threadLocalSize, int secondarySize, Allocator<T> allocator) {
        return new MixedObjectPool<T>(allocator,
            new ThreadLocalObjectPool<T>(threadLocalSize, false, allocator),
            QueueBasedObjectPool.of(new MpmcArrayQueue<T>(secondarySize), false, allocator, Resetter.ForRecyclable.<T>get()));
    }

    public MixedObjectPool(final Allocator<T> allocator, ObjectPool<T> primaryPool, ObjectPool<T> secondaryPool) {
        super(allocator);
        this.primaryPool = primaryPool;
        this.secondaryPool = secondaryPool;
    }

    @Nullable
    @Override
    public T tryCreateInstance() {
        final T recyclable = primaryPool.tryCreateInstance();
        if (recyclable != null) {
            return recyclable;
        }
        return secondaryPool.createInstance();
    }

    @Override
    public T createInstance() {
        T instance = super.createInstance();
        instance.setThread(Thread.currentThread());
        return instance;
    }

    @Override
    public boolean recycle(T obj) {
        if (obj.getThread() == null) {
            secondaryPool.recycle(obj);
        } else {
            if (!primaryPool.recycle(obj)) {
                secondaryPool.recycle(obj);
            }
        }
        return false;
    }

    @Override
    public int getSize() {
        return -1;
    }

    @Override
    public int getObjectsInPool() {
        return -1;
    }

    @Override
    public void close() throws IOException {
        primaryPool.close();
        secondaryPool.close();
    }
}
