package co.elastic.apm.agent.report.queue;

import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.jctools.queues.MessagePassingQueue;

public class QueueProcessor<T> extends AbstractLifecycleListener implements Runnable {

    private final MessagePassingQueue<T> queue;
    private final Thread processingThread;
    private final MessagePassingQueue.WaitStrategy waitStrategy;
    private final MessagePassingQueue.ExitCondition exitCondition;
    private final MessagePassingQueue.Consumer<T> consumer;

    protected QueueProcessor(MessagePassingQueue<T> queue,
                             MutableRunnableThread processingThread,
                             MessagePassingQueue.WaitStrategy waitStrategy,
                             MessagePassingQueue.ExitCondition exitCondition,
                             MessagePassingQueue.Consumer<T> consumer) {
        this.queue = queue;
        this.processingThread = processingThread;
        this.waitStrategy = waitStrategy;
        this.exitCondition = exitCondition;
        this.consumer = consumer;
        processingThread.setRunnable(this);
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        processingThread.start();
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            queue.drain(consumer, waitStrategy, exitCondition);
        }
    }

    @Override
    public void stop() {
        processingThread.interrupt();
    }
}
