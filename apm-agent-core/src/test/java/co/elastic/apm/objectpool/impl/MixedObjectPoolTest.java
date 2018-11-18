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

import co.elastic.apm.objectpool.ObjectPoolTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MixedObjectPoolTest {

    private MixedObjectPool<ObjectPoolTest.TestRecyclable> objectPool;

    @BeforeEach
    void setUp() {
        objectPool = MixedObjectPool.withThreadLocalBuffer(2, 4, ObjectPoolTest.TestRecyclable::new);
    }

    @Test
    void testCapacity() {
        assertThat(objectPool.getPrimaryPool().capacity()).isEqualTo(2);
        assertThat(objectPool.getPrimaryPool().size()).isEqualTo(2);
        assertThat(objectPool.getSecondaryPool().capacity()).isEqualTo(4);
        assertThat(objectPool.getSecondaryPool().size()).isEqualTo(0);
    }

    @Test
    void testRefillPrimary() {
        objectPool.createInstance();
        objectPool.createInstance();
        assertThat(objectPool.getPrimaryPool().size()).isEqualTo(0);
        assertThat(objectPool.getSecondaryPool().size()).isEqualTo(0);

        objectPool.createInstance();
        assertThat(objectPool.getPrimaryPool().size()).isEqualTo(1);
        assertThat(objectPool.getSecondaryPool().size()).isEqualTo(0);
    }

    @Test
    void testRefillFromSecondary() {
        objectPool.createInstance();
        objectPool.createInstance();
        assertThat(objectPool.getPrimaryPool().size()).isEqualTo(0);
        assertThat(objectPool.getSecondaryPool().size()).isEqualTo(0);

        assertThat(objectPool.getSecondaryPool().offer(new ObjectPoolTest.TestRecyclable())).isTrue();
        assertThat(objectPool.getSecondaryPool().offer(new ObjectPoolTest.TestRecyclable())).isTrue();
        assertThat(objectPool.getSecondaryPool().offer(new ObjectPoolTest.TestRecyclable())).isTrue();
        assertThat(objectPool.getSecondaryPool().offer(new ObjectPoolTest.TestRecyclable())).isTrue();
        assertThat(objectPool.getSecondaryPool().offer(new ObjectPoolTest.TestRecyclable())).isFalse();

        objectPool.createInstance();

        assertThat(objectPool.getPrimaryPool().size()).isEqualTo(1);
        assertThat(objectPool.getSecondaryPool().size()).isEqualTo(2);
    }
}
