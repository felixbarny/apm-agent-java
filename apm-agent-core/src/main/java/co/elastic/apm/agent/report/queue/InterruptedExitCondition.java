package co.elastic.apm.agent.report.queue;

import org.jctools.queues.MessagePassingQueue;

public class InterruptedExitCondition implements MessagePassingQueue.ExitCondition {

    public static final MessagePassingQueue.ExitCondition INSTANCE = new InterruptedExitCondition();

    @Override
    public boolean keepRunning() {
        return !Thread.currentThread().isInterrupted();
    }
}
