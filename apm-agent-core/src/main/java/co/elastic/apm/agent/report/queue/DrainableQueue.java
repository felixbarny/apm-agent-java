package co.elastic.apm.agent.report.queue;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MessagePassingQueue.Consumer;

public interface DrainableQueue<E, T> {

    boolean offer(E event);

    void drain(Consumer<T> consumer, MessagePassingQueue.WaitStrategy wait, MessagePassingQueue.ExitCondition exit);

    class MessagePassingQueueAdapter<E> implements DrainableQueue<E, E> {
        private final MessagePassingQueue<E> queue;

        private MessagePassingQueueAdapter(MessagePassingQueue<E> queue) {
            this.queue = queue;
        }

        public static <E> DrainableQueue<E, E> of(MessagePassingQueue<E> queue) {
            return new MessagePassingQueueAdapter<>(queue);
        }

        @Override
        public boolean offer(E event) {
            return queue.offer(event);
        }

        @Override
        public void drain(Consumer<E> consumer, MessagePassingQueue.WaitStrategy wait, MessagePassingQueue.ExitCondition exit) {
            MessagePassingQueue<E> queue = this.queue;
            int idleCounter = 0;
            while (exit.keepRunning()) {
                final E e = queue.relaxedPoll();
                if (e == null) {
                    // in contrast to the JCTools implementation, this checks the exit condition before idling
                    // this makes draining much more responsive to the exit condition
                    if (!exit.keepRunning()) {
                        return;
                    } else {
                        idleCounter = wait.idle(idleCounter);
                        continue;
                    }
                }
                idleCounter = 0;
                consumer.accept(e);
            }
        }
    }
}
