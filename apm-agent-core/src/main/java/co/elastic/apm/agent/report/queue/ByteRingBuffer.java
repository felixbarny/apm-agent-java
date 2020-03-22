package co.elastic.apm.agent.report.queue;

import org.jctools.queues.MessagePassingQueue;

import java.io.IOException;
import java.io.OutputStream;

public interface ByteRingBuffer {

    boolean offer(byte[] bytes);

    boolean offer(byte[] bytes, int size);

    void writeTo(OutputStream os, byte[] buffer) throws IOException;

    void writeTo(OutputStream os, byte[] buffer, MessagePassingQueue.ExitCondition exitCondition, MessagePassingQueue.WaitStrategy waitStrategy) throws IOException;
}
