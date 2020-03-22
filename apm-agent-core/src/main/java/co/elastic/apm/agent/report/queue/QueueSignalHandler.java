package co.elastic.apm.agent.report.queue;

public interface QueueSignalHandler {
    void onNotEmpty();

    class Noop implements QueueSignalHandler {
        public static QueueSignalHandler INSTANCE = new Noop();

        @Override
        public void onNotEmpty() {
        }
    }
}
