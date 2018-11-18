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

import co.elastic.apm.objectpool.Allocator;
import co.elastic.apm.objectpool.ObjectPool;
import co.elastic.apm.objectpool.Recyclable;
import org.jctools.queues.MessagePassingQueue;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;

public class QueueBasedObjectPool<T> extends AbstractObjectPool<T> {

    private final MessagePassingQueue<T> queue;

    /**
     * @param queue                   the underlying queue
     * @param preAllocate             when set to true, the allocator will be used to create maxPooledElements objects
     *                                which are then stored in the queue
     * @param allocator a factory method which is used to create new instances of the recyclable object. This factory is
     *                                used when there are no objects in the queue and to preallocate the queue.
     */
    public static <T extends Recyclable> QueueBasedObjectPool<T> ofRecyclable(MessagePassingQueue<T> queue, boolean preAllocate, Allocator<T> allocator) {
        return new QueueBasedObjectPool<>(queue, preAllocate, allocator, Resetter.ForRecyclable.<T>get());
    }

    public static <T> QueueBasedObjectPool<T> of(MessagePassingQueue<T> queue, boolean preAllocate, Allocator<T> allocator, Resetter<T> resetter) {
        return new QueueBasedObjectPool<>(queue, preAllocate, allocator, resetter);
    }

    private QueueBasedObjectPool(MessagePassingQueue<T> queue, boolean preAllocate, Allocator<T> allocator, Resetter<T> resetter) {
        super(allocator, resetter);
        this.queue = queue;
        if (preAllocate) {
            for (int i = 0; i < this.queue.size(); i++) {
                this.queue.offer(allocator.createInstance());
            }
        }
    }

    @Nullable
    @Override
    public T tryCreateInstance() {
        return queue.poll();
    }

    @Override
    public boolean offer(T obj) {
        return queue.offer(obj);
    }

    @Override
    public void drainTo(final ObjectPool<T> otherPool) {
        queue.drain(new MessagePassingQueue.Consumer<T>() {
            @Override
            public void accept(T e) {
                otherPool.offer(e);
            }
        }, otherPool.capacity());
    }

    @Override
    public void recycle(List<T> toRecycle) {
        final Iterator<T> iterator = toRecycle.iterator();
        queue.fill(new MessagePassingQueue.Supplier<T>() {
            @Override
            public T get() {
                return iterator.next();
            }
        }, toRecycle.size());
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public int capacity() {
        return queue.capacity();
    }

}
