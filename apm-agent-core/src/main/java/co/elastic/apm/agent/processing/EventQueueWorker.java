package co.elastic.apm.agent.processing;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.ReportingEvent;
import org.jctools.queues.MessagePassingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;

public class EventQueueWorker implements MessagePassingQueue.Consumer<Object>, Runnable, Closeable {

    private static final Logger logger = LoggerFactory.getLogger(EventQueueWorker.class);

    private final EventProcessor eventProcessor;
    private final MessagePassingQueue<Object> eventQueue;
    private final MessagePassingQueue.WaitStrategy waitStrategy;
    private final MessagePassingQueue.ExitCondition exitCondition;
    private final Thread processingThread;

    public EventQueueWorker(EventProcessor eventProcessor, MessagePassingQueue<Object> eventQueue,
                            MessagePassingQueue.WaitStrategy waitStrategy,
                            ThreadFactory threadFactory) {
        this.eventProcessor = eventProcessor;
        this.eventQueue = eventQueue;
        this.waitStrategy = waitStrategy;
        this.processingThread = threadFactory.newThread(this);
        this.exitCondition = new MessagePassingQueue.ExitCondition() {
            @Override
            public boolean keepRunning() {
                return !processingThread.isInterrupted();
            }
        };
    }

    @Override
    public void accept(Object event) {
        try {
            if (event instanceof Transaction) {
                eventProcessor.report((Transaction) event);
            } else if (event instanceof Span) {
                eventProcessor.report((Span) event);
            } else if (event instanceof ErrorCapture) {
                eventProcessor.report((ErrorCapture) event);
            } else if (event instanceof MetricRegistry) {
                eventProcessor.reportMetrics((MetricRegistry) event);
            } else if (event instanceof FutureTask) {
                eventProcessor.flush((FutureTask<Void>) event);
            } else if (event == ReportingEvent.ReportingEventType.SHUTDOWN) {
                eventProcessor.onClose();
                processingThread.interrupt();
            }
        } catch (Exception e) {
            logger.error("Unexpected event handling error", e);
        }
    }

    @Override
    public void run() {
        eventQueue.drain(this, waitStrategy, exitCondition);
    }

    public void start() {
        processingThread.start();
    }

    @Override
    public void close() {
        processingThread.interrupt();
    }
}
