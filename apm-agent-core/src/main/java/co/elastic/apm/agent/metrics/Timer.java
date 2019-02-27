package co.elastic.apm.agent.metrics;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.objectpool.Recyclable;

import java.util.concurrent.atomic.AtomicLong;

public class Timer implements Recyclable {
    private AtomicLong totalTime = new AtomicLong();
    private AtomicLong count = new AtomicLong();

    void increment(long duration) {
        totalTime.addAndGet(duration);
        count.incrementAndGet();
    }

    public double getTotalTimeMs() {
        return totalTime.get() / AbstractSpan.MS_IN_MICROS;
    }

    public double getAverageMs() {
        return totalTime.get() / AbstractSpan.MS_IN_MICROS / count.get();
    }

    public long getCount() {
        return count.get();
    }

    @Override
    public void resetState() {
        totalTime.set(0);
        count.set(0);
    }
}
