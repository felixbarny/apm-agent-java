package co.elastic.apm.agent.report.queue;

import org.jctools.queues.MessagePassingQueue;

public interface DrainableQueue<E> {
    boolean offer(E element);

    void drain(MessagePassingQueue.Consumer<E> consumer);
}
