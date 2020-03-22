package co.elastic.apm.agent.report.queue;

import java.io.IOException;
import java.io.OutputStream;

public interface ByteRingBuffer extends DrainableQueue<byte[], ByteRingBuffer> {

    boolean offer(byte[] bytes, int size);

    int writeTo(OutputStream os, byte[] buffer) throws IOException;

    int writeTo(OutputStream os, byte[] buffer, int limit) throws IOException;
}
