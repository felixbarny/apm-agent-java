package co.elastic.apm.agent.report.queue;

import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.jctools.queues.MessagePassingQueue;

import java.util.concurrent.TimeUnit;

public class QueueProcessor<T> extends AbstractLifecycleListener implements Runnable {

    private final MessagePassingQueue<T> queue;
    private final Thread processingThread;
    private final int shutdownTimeoutMillis;
    private final MessagePassingQueue.WaitStrategy waitStrategy;
    private final TimeoutExitCondition exitCondition;
    private final MessagePassingQueue.Consumer<T> consumer;
    private final long minTickNanos;
    private final QueueSignalHandler handler;
    private boolean stopRequested = false;

    public QueueProcessor(MessagePassingQueue.Supplier<MessagePassingQueue<T>> queueSupplier,
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

    public boolean offer(T event) {
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
            drain(consumer, waitStrategy, exitCondition);
        }
        exitWhenIdleOrAfterTimeout(shutdownTimeoutMillis);
    }

    private void exitWhenIdleOrAfterTimeout(int shutdownTimeoutMillis) {
        exitCondition.newTimeoutIn(shutdownTimeoutMillis);
        drain(consumer, new MessagePassingQueue.WaitStrategy() {
            @Override
            public int idle(int idleCounter) {
                exitCondition.newTimeoutIn(0);
                return idleCounter;
            }
        }, exitCondition);
    }

    private void drain(MessagePassingQueue.Consumer<T> c, MessagePassingQueue.WaitStrategy wait, TimeoutExitCondition exit) {
        MessagePassingQueue<T> queue = this.queue;
        int idleCounter = 0;
        while (exit.keepRunning()) {
            final T e = queue.relaxedPoll();
            if (e == null) {
                // in contrast to the JCTools implementation, this checks the exit condition before idling
                // this makes draining much more responsive to the exit condition
                if (!exit.keepRunning()) {
                    return;
                } else {
                    idleCounter = wait.idle(idleCounter);
                    continue;
                }
            }
            idleCounter = 0;
            c.accept(e);
        }
    }

    @Override
    public void stop() throws InterruptedException {
        // not interrupting the thread as it would make LockSupport.park return immediately
        stopRequested = true;
        processingThread.join(shutdownTimeoutMillis);
    }
}
