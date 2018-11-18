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
package co.elastic.apm.benchmark.objectpool;

import co.elastic.apm.benchmark.AbstractBenchmark;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.ElasticApmTracerBuilder;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.objectpool.impl.MixedObjectPool;
import co.elastic.apm.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.objectpool.impl.ThreadLocalObjectPool;
import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.runner.RunnerException;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ObjectPoolBenchmark extends AbstractBenchmark {

    private ElasticApmTracer tracer;
    private MixedObjectPool<Transaction> mixedObjectPool;
    private ThreadLocalObjectPool<Transaction> threadLocalObjectPool;
    private QueueBasedObjectPool<Transaction> jctoolsQueueObjectPool;
    private QueueBasedObjectPool<Transaction> jctoolsAtomicQueueObjectPool;

    public static void main(String[] args) throws RunnerException {
        run(ObjectPoolBenchmark.class);
    }

    @Setup
    public void setUp() {
        tracer = new ElasticApmTracerBuilder().build();
        jctoolsQueueObjectPool = QueueBasedObjectPool.ofRecyclable(new MpmcArrayQueue<>(512), true, () -> new Transaction(tracer));
        jctoolsAtomicQueueObjectPool = QueueBasedObjectPool.ofRecyclable(new MpmcAtomicArrayQueue<>(256), true, () -> new Transaction(tracer));
        mixedObjectPool = MixedObjectPool.withThreadLocalBuffer(32, 512, () -> new Transaction(tracer));
        threadLocalObjectPool = ThreadLocalObjectPool.ofRecyclable(64, true, () -> new Transaction(tracer));
    }

//        @Benchmark
    @Threads(16)
    public Transaction testNewOperator() {
        return new Transaction(tracer);
    }

    @Benchmark
    @Threads(16)
    public Transaction testJctoolsAtomicQueueObjectPool() {
        Transaction transaction = jctoolsAtomicQueueObjectPool.createInstance();
        jctoolsAtomicQueueObjectPool.recycle(transaction);
        return transaction;
    }

//    @Benchmark
    @Threads(16)
    public Transaction testJctoolsQueueObjectPool() {
        Transaction transaction = jctoolsQueueObjectPool.createInstance();
        jctoolsQueueObjectPool.recycle(transaction);
        return transaction;
    }

    @Benchmark
    @Threads(16)
    public Transaction testMixedObjectPool() {
        Transaction transaction = mixedObjectPool.createInstance();
        mixedObjectPool.recycle(transaction);
        return transaction;
    }

    //    @Benchmark
    @Threads(8)
    public Transaction testThreadLocalObjectPool() {
        Transaction transaction = threadLocalObjectPool.createInstance();
        threadLocalObjectPool.recycle(transaction);
        return transaction;
    }

}
