package co.elastic.apm.agent.report.queue;

import org.jctools.queues.MessagePassingQueue;

import java.util.concurrent.TimeUnit;

public class DrainableQueueProcessor<E, T> implements Runnable {

    protected final DrainableQueue<E, T> queue;
    protected final QueueSignalHandler handler;
    private final Thread processingThread;
    private final ProcessorLifecycleCallback callback;
    private final int shutdownTimeoutMillis;
    private final MessagePassingQueue.WaitStrategy waitStrategy;
    private final TimeoutExitCondition exitCondition;
    private final MessagePassingQueue.Consumer<T> consumer;
    private final long drainTimeout;
    private boolean stopRequested = false;

    public DrainableQueueProcessor(MessagePassingQueue.Supplier<? extends DrainableQueue<E, T>> queueSupplier,
                                   MutableRunnableThread processingThread,
                                   MessagePassingQueue.Consumer<T> consumer,
                                   ProcessorLifecycleCallback callback,
                                   long parkTimeNanos,
                                   int drainTimeout,
                                   int shutdownTimeoutMillis) {

        this.queue = queueSupplier.get();
        this.processingThread = processingThread;
        this.callback = callback;
        this.shutdownTimeoutMillis = shutdownTimeoutMillis;
        UnparkOnSignalWaitStrategy unparkOnSignalWaitStrategy = new UnparkOnSignalWaitStrategy(processingThread, parkTimeNanos);
        this.handler = unparkOnSignalWaitStrategy;
        this.waitStrategy = new MessagePassingQueue.WaitStrategy() {
            @Override
            public int idle(int idleCounter) {
                callback.onIdle();
                return unparkOnSignalWaitStrategy.idle(idleCounter);
            }
        };
        this.exitCondition = new TimeoutExitCondition();
        this.consumer = consumer;
        processingThread.setRunnable(this);
        this.drainTimeout = TimeUnit.MILLISECONDS.toNanos(drainTimeout);
    }

    public void start() {
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
        callback.onStart();
        while (!stopRequested) {
            exitCondition.newTimeoutIn(drainTimeout);
            queue.drain(consumer, waitStrategy, exitCondition);
            callback.onTimeout();
        }
        exitWhenIdleOrAfterTimeout(shutdownTimeoutMillis);
        callback.onShutdown();
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

    public void stop() throws Exception {
        // not interrupting the thread as it would make LockSupport.park return immediately
        stopRequested = true;
        processingThread.join(shutdownTimeoutMillis);
    }
}
