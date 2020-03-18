package co.elastic.apm.agent.report.queue;

import javax.annotation.Nullable;

public class MutableRunnableThread extends Thread {

    @Nullable
    private volatile Runnable runnable;

    public MutableRunnableThread(String name) {
        super(name);
    }

    @Override
    public void run() {
        Runnable runnable = this.runnable;
        if (runnable != null) {
            runnable.run();
        }
    }

    public void setRunnable(Runnable runnable) {
        this.runnable = runnable;
    }
}
