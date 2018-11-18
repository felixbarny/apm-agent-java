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
package co.elastic.apm.objectpool;

import javax.annotation.Nullable;
import java.util.List;

public class NoopObjectPool<T extends Recyclable> implements ObjectPool<T> {

    private final Allocator<T> allocator;

    public NoopObjectPool(Allocator<T> allocator) {
        this.allocator = allocator;
    }

    @Nullable
    @Override
    public T tryCreateInstance() {
        return allocator.createInstance();
    }

    @Override
    public T createInstance() {
        return allocator.createInstance();
    }

    @Override
    public void drainTo(ObjectPool<T> otherPool) {

    }

    @Override
    public void recycle(List<T> toRecycle) {

    }

    @Override
    public void recycle(T obj) {

    }

    @Override
    public boolean offer(T obj) {
        return false;
    }

    @Override
    public int capacity() {
        return 0;
    }

    @Override
    public int size() {
        return 0;
    }

}
