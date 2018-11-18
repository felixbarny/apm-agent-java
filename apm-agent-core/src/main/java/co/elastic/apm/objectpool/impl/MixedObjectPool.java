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
import co.elastic.apm.objectpool.Recyclable;
import org.jctools.queues.MpmcArrayQueue;

import javax.annotation.Nullable;

public class MixedObjectPool<T> extends AbstractObjectPool<T> {

    private final ObjectPool<T> primaryPool;
    private final ObjectPool<T> secondaryPool;

    public static <T extends Recyclable> MixedObjectPool<T> withThreadLocalBuffer(int threadLocalSize, int secondarySize, final Allocator<T> allocator) {
        return new MixedObjectPool<>(ThreadLocalObjectPool.of(threadLocalSize, true, allocator, Resetter.ForRecyclable.<T>get()),
            QueueBasedObjectPool.of(new MpmcArrayQueue<T>(secondarySize), false, allocator, Resetter.ForRecyclable.<T>get()),
            allocator, Resetter.ForRecyclable.<T>get());
    }

    public static <T> MixedObjectPool<T> withThreadLocalBuffer(int threadLocalSize, int secondarySize, final Allocator<T> allocator, final Resetter<T> resetter) {
        return new MixedObjectPool<>(ThreadLocalObjectPool.of(threadLocalSize, true, allocator, resetter),
            QueueBasedObjectPool.of(new MpmcArrayQueue<T>(secondarySize), false, allocator, resetter),
            allocator, resetter);
    }

    private MixedObjectPool(ObjectPool<T> primaryPool, ObjectPool<T> secondaryPool, final Allocator<T> allocator, final Resetter<T> resetter) {
        super(allocator, resetter);
        this.primaryPool = primaryPool;
        this.secondaryPool = secondaryPool;
    }

    @Nullable
    @Override
    public T tryCreateInstance() {
        final T recyclable = primaryPool.tryCreateInstance();
        if (recyclable == null) {
            secondaryPool.drainTo(primaryPool);
            for (int i = primaryPool.size(); i < primaryPool.capacity(); i++) {
                primaryPool.offer(allocator.createInstance());
            }
            return primaryPool.tryCreateInstance();
        }
        return recyclable;
    }

    @Override
    public boolean offer(T obj) {
        return secondaryPool.offer(obj);
    }

    @Override
    public int capacity() {
        return -1;
    }

    @Override
    public int size() {
        return -1;
    }

    ObjectPool<T> getPrimaryPool() {
        return primaryPool;
    }

    ObjectPool<T> getSecondaryPool() {
        return secondaryPool;
    }
}
