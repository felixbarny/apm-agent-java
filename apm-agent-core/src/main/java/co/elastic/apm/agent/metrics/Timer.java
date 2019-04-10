package co.elastic.apm.agent.metrics;

import co.elastic.apm.agent.objectpool.Recyclable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Timer implements Recyclable {
    private static final double MS_IN_MICROS = TimeUnit.MILLISECONDS.toMicros(1);

    private AtomicLong totalTime = new AtomicLong();
    private AtomicLong count = new AtomicLong();

    public void update(long durationNs) {
        update(durationNs, 1);
    }

    public void update(long durationNs, long count) {
        this.totalTime.addAndGet(durationNs);
        this.count.addAndGet(count);
    }

    public long getTotalTimeNs() {
        return totalTime.get();
    }

    public double getTotalTimeMs() {
        return totalTime.get() / MS_IN_MICROS;
    }

    public double getAverageMs() {
        return totalTime.get() / MS_IN_MICROS / count.get();
    }

    public long getCount() {
        return count.get();
    }

    public boolean hasContent() {
        return count.get() > 0;
    }

    @Override
    public void resetState() {
        totalTime.set(0);
        count.set(0);
    }
}
