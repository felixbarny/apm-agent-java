package co.elastic.apm.agent.report.queue;

import org.jctools.queues.MessagePassingQueue;

public class TimeoutExitCondition implements MessagePassingQueue.ExitCondition {

    private volatile long timeoutNanos;

    public void newTimeoutIn(long nanos) {
        timeoutNanos = System.nanoTime() + nanos;
    }

    @Override
    public boolean keepRunning() {
        return System.nanoTime() < timeoutNanos;
    }
}
