package co.elastic.apm.agent.report.queue;

import org.jctools.queues.MessagePassingQueue;

import java.io.IOException;
import java.io.OutputStream;

public interface ByteRingBuffer {

    boolean offer(byte[] bytes);

    boolean offer(byte[] bytes, int size);

    int writeTo(OutputStream os, byte[] buffer) throws IOException;

    int writeTo(OutputStream os, byte[] buffer, int limit) throws IOException;

    void drain(MessagePassingQueue.Consumer<ByteRingBuffer> consumer, MessagePassingQueue.ExitCondition exitCondition, MessagePassingQueue.WaitStrategy waitStrategy);
}
