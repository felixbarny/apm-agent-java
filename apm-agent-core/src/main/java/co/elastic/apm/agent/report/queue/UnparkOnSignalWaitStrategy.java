package co.elastic.apm.agent.report.queue;

import org.jctools.queues.MessagePassingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class UnparkOnSignalWaitStrategy implements MessagePassingQueue.WaitStrategy, Signaller {

    private final Thread consumerThread;
    private final AtomicBoolean signalNeeded = new AtomicBoolean(false);
    private final long parkTimeNanos;
    private static final Logger logger = LoggerFactory.getLogger(UnparkOnSignalWaitStrategy.class);

    public UnparkOnSignalWaitStrategy(Thread consumerThread, long parkTimeNanos) {
        this.consumerThread = consumerThread;
        this.parkTimeNanos = parkTimeNanos;
    }

    @Override
    public int idle(int idleCounter) {
        signalNeeded.set(true);
        long start = System.nanoTime();
        logger.info("park start");
        LockSupport.parkNanos(parkTimeNanos);
        logger.info("park end, took {}", System.nanoTime() - start);
        return idleCounter;
    }

    @Override
    public void signal() {
        if (signalNeeded.get() && signalNeeded.compareAndSet(true, false)) {
            logger.info("unpark");
            LockSupport.unpark(consumerThread);
        }
    }
}
