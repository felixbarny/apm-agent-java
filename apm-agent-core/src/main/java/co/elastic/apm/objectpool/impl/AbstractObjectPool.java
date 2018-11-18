/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.objectpool.impl;

import co.elastic.apm.objectpool.ObjectPool;
import co.elastic.apm.objectpool.Allocator;

import java.util.List;

public abstract class AbstractObjectPool<T> implements ObjectPool<T> {

    protected final Allocator<T> allocator;
    protected final Resetter<T> resetter;

    protected AbstractObjectPool(Allocator<T> allocator, Resetter<T> resetter) {
        this.allocator = allocator;
        this.resetter = resetter;
    }

    @Override
    public T createInstance() {
        T recyclable = tryCreateInstance();
        if (recyclable == null) {
            // queue is empty, falling back to creating a new instance
            return allocator.createInstance();
        } else {
            return recyclable;
        }
    }

    @Override
    public void drainTo(ObjectPool<T> otherPool) {
        for (int i = 0; i < otherPool.capacity(); i++) {
            T obj = tryCreateInstance();
            if (obj == null) {
                return;
            }
            otherPool.offer(obj);
        }
    }

    @Override
    public void recycle(List<T> toRecycle) {
        for (T obj : toRecycle) {
            recycle(obj);
        }
    }

    @Override
    public void recycle(T obj) {
        resetter.recycle(obj);
        offer(obj);
    }

}
