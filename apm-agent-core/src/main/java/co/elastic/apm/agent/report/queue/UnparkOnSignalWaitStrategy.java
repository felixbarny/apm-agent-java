package co.elastic.apm.agent.report.queue;

import org.jctools.queues.MessagePassingQueue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class UnparkOnSignalWaitStrategy implements MessagePassingQueue.WaitStrategy, Signaller {

    private final Thread consumerThread;
    private final AtomicBoolean signalNeeded = new AtomicBoolean(false);
    private final long parkTimeNanos;

    public UnparkOnSignalWaitStrategy(Thread consumerThread, long parkTimeNanos) {
        this.consumerThread = consumerThread;
        this.parkTimeNanos = parkTimeNanos;
    }

    @Override
    public int idle(int idleCounter) {
        signalNeeded.set(true);
        LockSupport.parkNanos(parkTimeNanos);
        return idleCounter;
    }

    @Override
    public void signal() {
        if (signalNeeded.get() && signalNeeded.compareAndSet(true, false)) {
            LockSupport.unpark(consumerThread);
        }
    }
}
