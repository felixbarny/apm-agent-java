package co.elastic.apm.agent.util.queues;

import org.jctools.queues.MessagePassingQueue;

import java.util.concurrent.locks.LockSupport;

public class ExponentiallyIncreasingSleepingJCToolsWaitStrategy implements MessagePassingQueue.WaitStrategy {
    private final int sleepTimeNsStart;
    private final int sleepTimeNsMax;
    private int idleCounter = 0;

    public ExponentiallyIncreasingSleepingJCToolsWaitStrategy(int sleepTimeNsStart, int sleepTimeNsMax, String name) {
        this.sleepTimeNsStart = sleepTimeNsStart;
        this.sleepTimeNsMax = sleepTimeNsMax;
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println(name + " idle counter " + idleCounter);
            }
        });
    }

    @Override
    public int idle(int currentSleep) {
        idleCounter++;
        if (currentSleep < sleepTimeNsStart) {
            currentSleep = sleepTimeNsStart;
        }
        if (currentSleep < sleepTimeNsMax) {
            LockSupport.parkNanos(currentSleep);
            return currentSleep * 2;
        } else {
            LockSupport.parkNanos(sleepTimeNsMax);
            return currentSleep;
        }
    }
}
