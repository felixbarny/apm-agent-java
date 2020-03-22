package co.elastic.apm.agent.report.queue;

import java.io.IOException;
import java.io.OutputStream;

public interface ByteQueue {

    boolean offer(byte[] bytes);

    boolean offer(byte[] bytes, int size);

    void writeTo(OutputStream os, byte[] buffer) throws IOException;
}
