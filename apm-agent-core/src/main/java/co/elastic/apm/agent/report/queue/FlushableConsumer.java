package co.elastic.apm.agent.report.queue;

import org.jctools.queues.MessagePassingQueue;

public interface FlushableConsumer<T> extends MessagePassingQueue.Consumer<T> {

    /**
     * Gets called when the queue is empty or every {@code n} ms.
     * The tick rate can be configured via the constructor of {@link ConsumerProcessor}.
     * <p>
     * This lets implementations know when's a good time to flush accumulated events.
     * The method is called at a regular interval (configurable via {@link ConsumerProcessor}'s constructor)
     * or if there's nothing left in the queue to process (aka end of batch).
     * </p>
     */
    void flush();

    class ConsumerAdapter<T> implements FlushableConsumer<T> {

        private final MessagePassingQueue.Consumer<T> consumer;

        private ConsumerAdapter(MessagePassingQueue.Consumer<T> consumer) {
            this.consumer = consumer;
        }

        public static <T> FlushableConsumer<T> of(MessagePassingQueue.Consumer<T> consumer) {
            return new ConsumerAdapter<>(consumer);
        }

        @Override
        public void accept(T e) {
            consumer.accept(e);
        }

        @Override
        public void flush() {
        }
    }
}
