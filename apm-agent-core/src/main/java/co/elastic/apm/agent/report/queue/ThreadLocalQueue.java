package co.elastic.apm.agent.report.queue;

import com.blogspot.mydailyjava.weaklockfree.DetachedThreadLocal;
import org.jctools.queues.MessagePassingQueue;

import java.util.Map;

public class ThreadLocalQueue<T> implements MessagePassingQueue<T> {

    private final DetachedThreadLocal<MessagePassingQueue<T>> threadLocalQueues = new DetachedThreadLocal<>(DetachedThreadLocal.Cleaner.INLINE);
    private final Supplier<MessagePassingQueue<T>> threadLocalQueueAllocator;
    private final MessagePassingQueue<T> globalQueue;

    public ThreadLocalQueue(Supplier<MessagePassingQueue<T>> threadLocalQueueAllocator, MessagePassingQueue<T> globalQueue) {
        this.threadLocalQueueAllocator = threadLocalQueueAllocator;
        this.globalQueue = globalQueue;
    }

    @Override
    public boolean offer(T element) {
        if (!getThreadLocalQueue().offer(element)) {
            return globalQueue.offer(element);
        }
        return true;
    }

    @Override
    public void drain(Consumer<T> c, WaitStrategy wait, ExitCondition exit) {
        int idleCounter = 0;
        while (exit.keepRunning()) {
            int drained = 0;
            for (Map.Entry<Thread, MessagePassingQueue<T>> entry : threadLocalQueues.getBackingMap()) {
                MessagePassingQueue<T> threadLocalQueue = entry.getValue();
                drained += threadLocalQueue.drain(c, threadLocalQueue.capacity());
            }
            drained += globalQueue.drain(c, globalQueue.capacity());
            if (drained == 0) {
                idleCounter = wait.idle(idleCounter);
            } else {
                idleCounter = 0;
            }
        }
    }

    private MessagePassingQueue<T> getThreadLocalQueue() {
        MessagePassingQueue<T> queue = threadLocalQueues.get();
        if (queue == null) {
            queue = threadLocalQueueAllocator.get();
            threadLocalQueues.set(queue);
        }
        return queue;
    }

    @Override
    public T poll() {
        return getThreadLocalQueue().poll();
    }

    @Override
    public T peek() {
        return getThreadLocalQueue().peek();
    }

    @Override
    public int size() {
        return getThreadLocalQueue().size();
    }

    @Override
    public void clear() {
        getThreadLocalQueue().clear();
    }

    @Override
    public boolean isEmpty() {
        return getThreadLocalQueue().isEmpty();
    }

    @Override
    public int capacity() {
        return getThreadLocalQueue().capacity();
    }

    @Override
    public boolean relaxedOffer(T e) {
        return getThreadLocalQueue().relaxedOffer(e);
    }

    @Override
    public T relaxedPoll() {
        return getThreadLocalQueue().relaxedPoll();
    }

    @Override
    public T relaxedPeek() {
        return getThreadLocalQueue().relaxedPeek();
    }

    @Override
    public int drain(Consumer<T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int fill(Supplier<T> s) {
        throw new UnsupportedOperationException();

    }

    @Override
    public int fill(Supplier<T> s, int limit) {
        throw new UnsupportedOperationException();

    }

    @Override
    public void fill(Supplier<T> s, WaitStrategy wait, ExitCondition exit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int drain(Consumer<T> c, int limit) {
        throw new UnsupportedOperationException();
    }
}
