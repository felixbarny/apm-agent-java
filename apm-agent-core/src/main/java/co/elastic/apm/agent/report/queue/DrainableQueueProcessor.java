package co.elastic.apm.agent.report.queue;

import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.jctools.queues.MessagePassingQueue;

import java.util.concurrent.TimeUnit;

public class DrainableQueueProcessor<E, T> extends AbstractLifecycleListener implements Runnable {

    private final DrainableQueue<E, T> queue;
    private final Thread processingThread;
    private final int shutdownTimeoutMillis;
    private final MessagePassingQueue.WaitStrategy waitStrategy;
    private final TimeoutExitCondition exitCondition;
    private final MessagePassingQueue.Consumer<T> consumer;
    private final long minTickNanos;
    private final QueueSignalHandler handler;
    private boolean stopRequested = false;

    public DrainableQueueProcessor(MessagePassingQueue.Supplier<DrainableQueue<E, T>> queueSupplier,
                                   MutableRunnableThread processingThread,
                                   MessagePassingQueue.Consumer<T> consumer,
                                   long parkTimeNanos,
                                   int minTickMillis,
                                   int shutdownTimeoutMillis) {

        this.queue = queueSupplier.get();
        this.processingThread = processingThread;
        this.shutdownTimeoutMillis = shutdownTimeoutMillis;
        UnparkOnSignalWaitStrategy unparkOnSignalWaitStrategy = new UnparkOnSignalWaitStrategy(processingThread, parkTimeNanos);
        this.handler = unparkOnSignalWaitStrategy;
        this.waitStrategy = unparkOnSignalWaitStrategy;
        this.exitCondition = new TimeoutExitCondition();
        this.consumer = consumer;
        processingThread.setRunnable(this);
        this.minTickNanos = TimeUnit.MILLISECONDS.toNanos(minTickMillis);
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        processingThread.start();
    }

    public boolean offer(E event) {
        boolean offered = queue.offer(event);
        if (offered) {
            handler.onNotEmpty();
        }
        return offered;
    }

    @Override
    public void run() {
        while (!stopRequested) {
            exitCondition.newTimeoutIn(minTickNanos);
            queue.drain(consumer, waitStrategy, exitCondition);
        }
        exitWhenIdleOrAfterTimeout(shutdownTimeoutMillis);
    }

    private void exitWhenIdleOrAfterTimeout(int shutdownTimeoutMillis) {
        exitCondition.newTimeoutIn(shutdownTimeoutMillis);
        queue.drain(consumer, new MessagePassingQueue.WaitStrategy() {
            @Override
            public int idle(int idleCounter) {
                exitCondition.newTimeoutIn(0);
                return idleCounter;
            }
        }, exitCondition);
    }

    @Override
    public void stop() throws InterruptedException {
        // not interrupting the thread as it would make LockSupport.park return immediately
        stopRequested = true;
        processingThread.join(shutdownTimeoutMillis);
    }
}
