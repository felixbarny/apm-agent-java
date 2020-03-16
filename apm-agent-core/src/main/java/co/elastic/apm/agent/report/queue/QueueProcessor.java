package co.elastic.apm.agent.report.queue;

import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.jctools.queues.MessagePassingQueue;

public class QueueProcessor<T> extends AbstractLifecycleListener implements Runnable {

    private final MessagePassingQueue<T> queue;
    private final Thread processingThread;
    private final MessagePassingQueue.WaitStrategy waitStrategy;
    private final TimeoutExitCondition exitCondition;
    private final MessagePassingQueue.Consumer<T> eventConsumer;
    private final MessagePassingQueue.Consumer<Void> tickConsumer;

    protected QueueProcessor(MessagePassingQueue<T> queue,
                             MutableRunnableThread processingThread,
                             MessagePassingQueue.WaitStrategy waitStrategy,
                             MessagePassingQueue.Consumer<T> eventConsumer,
                             MessagePassingQueue.Consumer<Void> tickConsumer) {
        this.queue = queue;
        this.processingThread = processingThread;
        this.waitStrategy = waitStrategy;
        this.exitCondition = new TimeoutExitCondition();
        this.eventConsumer = eventConsumer;
        this.tickConsumer = tickConsumer;
        processingThread.setRunnable(this);
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        processingThread.start();
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            exitCondition.newTimeoutIn(100);
            queue.drain(eventConsumer, waitStrategy, exitCondition);
            tickConsumer.accept(null);
        }
    }

    @Override
    public void stop() {
        processingThread.interrupt();
    }
}
