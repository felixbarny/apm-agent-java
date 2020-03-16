package co.elastic.apm.agent.report.queue;

import org.jctools.queues.MessagePassingQueue;

public class SignallingMessagePassingQueue<T> implements MessagePassingQueue<T> {

    private final MessagePassingQueue<T> delegate;
    private final Signaller signaller;

    public SignallingMessagePassingQueue(MessagePassingQueue<T> delegate, Signaller signaller) {
        this.delegate = delegate;
        this.signaller = signaller;
    }

    public boolean offer(T e) {
        boolean added = delegate.offer(e);
        if (added) {
            signaller.signal();
        }
        return added;
    }

    public boolean relaxedOffer(T e) {
        boolean added = delegate.relaxedOffer(e);
        if (added) {
            signaller.signal();
        }
        return added;
    }

    public T poll() {
        return delegate.poll();
    }

    public T peek() {
        return delegate.peek();
    }

    public int size() {
        return delegate.size();
    }

    public void clear() {
        delegate.clear();
    }

    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    public int capacity() {
        return delegate.capacity();
    }

    public T relaxedPoll() {
        return delegate.relaxedPoll();
    }

    public T relaxedPeek() {
        return delegate.relaxedPeek();
    }

    public int drain(Consumer<T> c) {
        return delegate.drain(c);
    }

    public int fill(Supplier<T> s) {
        return delegate.fill(s);
    }

    public int drain(Consumer<T> c, int limit) {
        return delegate.drain(c, limit);
    }

    public int fill(Supplier<T> s, int limit) {
        return delegate.fill(s, limit);
    }

    public void drain(Consumer<T> c, WaitStrategy wait, ExitCondition exit) {
        delegate.drain(c, wait, exit);
    }

    public void fill(Supplier<T> s, WaitStrategy wait, ExitCondition exit) {
        delegate.fill(s, wait, exit);
    }
}
