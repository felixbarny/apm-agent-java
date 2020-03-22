package co.elastic.apm.agent.report.queue;

public interface ProcessorLifecycleCallback {

    /**
     * Called when the processor starts
     */
    void onStart();

    /**
     * Called when the queue is empty aka end-of-batch
     */
    void onIdle();

    /**
     * Called after the drain timeout has occurred
     */
    void onTimeout();

    /**
     * Called right before the processor shuts down
     */
    void onShutdown();

    class Noop implements ProcessorLifecycleCallback {

        public static final ProcessorLifecycleCallback INSTANCE = new Noop();

        @Override
        public void onStart() {
        }

        @Override
        public void onIdle() {
        }

        @Override
        public void onTimeout() {
        }

        @Override
        public void onShutdown() {
        }
    }
}
