package co.elastic.apm.agent.util.queues;

import com.blogspot.mydailyjava.weaklockfree.DetachedThreadLocal;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.SpscArrayQueue;

import javax.annotation.Nullable;
import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class MpscThreadLocalQueue<E> extends AbstractQueue<E> implements MessagePassingQueue<E> {

    /**
     * Using a {@link DetachedThreadLocal} instead of a plain {@link ThreadLocal} has the benefit that it does not create class loader leaks
     * when used from non-persistent class loaders.
     */
    private final DetachedThreadLocal<MessagePassingQueue<E>> threadLocalQueues;
    private final List<MessagePassingQueue<E>> queues = new CopyOnWriteArrayList<>();
    private final int capacityPerThread;

    public MpscThreadLocalQueue(DetachedThreadLocal.Cleaner cleaner, final int capacityPerThread) {
        this.capacityPerThread = capacityPerThread;
        threadLocalQueues = new DetachedThreadLocal<MessagePassingQueue<E>>(cleaner) {
            @Override
            protected MessagePassingQueue<E> initialValue(Thread thread) {
                final SpscArrayQueue<E> queue = new SpscArrayQueue<>(capacityPerThread);
                queues.add(queue);
                return queue;
            }
        };
    }

    public void expungeStaleEntries() {
        threadLocalQueues.getBackingMap().expungeStaleEntries();
        List<MessagePassingQueue<E>> aliveQueues = new ArrayList<>(threadLocalQueues.getBackingMap().approximateSize());
        for (Map.Entry<Thread, MessagePassingQueue<E>> entry : threadLocalQueues.getBackingMap()) {
            aliveQueues.add(entry.getValue());
        }
        queues.retainAll(aliveQueues);
    }

    @Override
    public int size() {
        int size = 0;
        for (int i = 0; i < queues.size(); i++) {
            size += queues.get(i).size();
        }
        return size;
    }

    @Override
    public Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean offer(E e) {
        return threadLocalQueues.get().offer(e);
    }

    @Override
    public E poll() {
        for (int i = 0; i < queues.size(); i++) {
            E poll = queues.get(i).poll();
            if (poll != null) {
                return poll;
            }
        }
        return null;
    }

    @Override
    public E peek() {
        for (int i = 0; i < queues.size(); i++) {
            E peek = queues.get(i).peek();
            if (peek != null) {
                return peek;
            }
        }
        return null;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < queues.size(); i++) {
            boolean empty = queues.get(i).isEmpty();
            if (!empty) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the capacity for the current thread's queue
     * @return the capacity for the current thread's queue
     */
    @Override
    public int capacity() {
        return capacityPerThread - threadLocalQueues.get().size();
    }

    @Override
    public boolean relaxedOffer(E e) {
        return offer(e);
    }

    @Override
    @Nullable
    public E relaxedPoll() {
        for (int i = 0; i < queues.size(); i++) {
            E poll = queues.get(i).relaxedPoll();
            if (poll != null) {
                return poll;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public E relaxedPeek() {
        for (int i = 0; i < queues.size(); i++) {
            E peek = queues.get(i).relaxedPeek();
            if (peek != null) {
                return peek;
            }
        }
        return null;
    }

    @Override
    public int drain(MessagePassingQueue.Consumer<E> c) {
        int drained = 0;
        for (int i = 0; i < queues.size(); i++) {
            drained += queues.get(i).drain(c);
        }
        return drained;
    }

    @Override
    public int drain(MessagePassingQueue.Consumer<E> c, int limit) {
        int drained = 0;
        for (int i = 0; i < queues.size(); i++) {
            limit -= drained;
            drained += queues.get(i).drain(c, limit);
        }
        return drained;
    }

    @Override
    public void drain(MessagePassingQueue.Consumer<E> c, WaitStrategy wait, ExitCondition exit) {
        int idleCounter = 0;
        while (exit.keepRunning()) {
            int drain = drain(c);
            if (drain == 0) {
                idleCounter = wait.idle(idleCounter);
                continue;
            }
            idleCounter = 0;
        }
    }

    @Override
    public int fill(Supplier<E> s) {
        return threadLocalQueues.get().fill(s);
    }

    @Override
    public int fill(Supplier<E> s, int limit) {
        return threadLocalQueues.get().fill(s, limit);
    }

    @Override
    public void fill(Supplier<E> s, WaitStrategy wait, ExitCondition exit) {
        threadLocalQueues.get().fill(s, wait, exit);
    }
}
