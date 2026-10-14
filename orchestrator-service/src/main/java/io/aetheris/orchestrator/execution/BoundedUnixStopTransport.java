package io.aetheris.orchestrator.execution;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Synthetic STOP-only local Unix IPC. Nonblocking connect/read/write with a
 * SINGLE monotonic end-to-end deadline (not per-frame or per-byte timeouts).
 * No agent tool, TCP listener, arbitrary command or production worker.
 */
public final class BoundedUnixStopTransport {
    private BoundedUnixStopTransport() {}

    public static SandboxWorkerStopWire.SignedFrame exchange(
            Path socket, SandboxWorkerStopWire.SignedFrame request, Duration timeout)
            throws IOException {
        Objects.requireNonNull(socket, "socket");
        long deadline = deadline(timeout);
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.configureBlocking(false);
            if (!channel.connect(UnixDomainSocketAddress.of(socket))) {
                try (Selector selector = Selector.open()) {
                    channel.register(selector, SelectionKey.OP_CONNECT);
                    while (!channel.finishConnect())
                        await(selector, deadline, "STOP_CONNECT_DEADLINE_EXCEEDED");
                }
            }
            write(channel, request, deadline);
            return read(channel, deadline);
        }
    }

    public static SandboxWorkerStopWire.SignedFrame read(
            SocketChannel channel, Duration timeout) throws IOException {
        return read(channel, deadline(timeout));
    }

    public static void write(SocketChannel channel,
                             SandboxWorkerStopWire.SignedFrame frame, Duration timeout)
            throws IOException {
        write(channel, frame, deadline(timeout));
    }

    private static SandboxWorkerStopWire.SignedFrame read(
            SocketChannel channel, long deadline) throws IOException {
        int messageSize = readBytes(channel, 4, deadline).getInt();
        if (messageSize < 1 || messageSize > 512)
            throw new IOException("STOP_FRAME_BODY_INVALID");
        ByteBuffer body = readBytes(channel, messageSize, deadline);
        int signatureSize = readBytes(channel, 4, deadline).getInt();
        if (signatureSize != 64) throw new IOException("STOP_SIGNATURE_SIZE_INVALID");
        ByteBuffer signature = readBytes(channel, 64, deadline);
        return new SandboxWorkerStopWire.SignedFrame(body.array(), signature.array());
    }

    private static ByteBuffer readBytes(SocketChannel channel, int count, long deadline)
            throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(count);
        try (Selector selector = Selector.open()) {
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_READ);
            while (buffer.hasRemaining()) {
                int amount = channel.read(buffer);
                if (amount < 0) throw new IOException("STOP_PEER_CLOSED");
                if (amount == 0)
                    await(selector, deadline, "STOP_READ_DEADLINE_EXCEEDED");
                else if (System.nanoTime() >= deadline)
                    throw new IOException("STOP_READ_DEADLINE_EXCEEDED");
            }
        }
        buffer.flip();
        return buffer;
    }

    private static void write(SocketChannel channel,
                              SandboxWorkerStopWire.SignedFrame frame, long deadline)
            throws IOException {
        byte[] body = frame.message(), signature = frame.signature();
        if (body.length < 1 || body.length > 512 || signature.length != 64)
            throw new IOException("STOP_FRAME_LENGTH_INVALID");
        ByteBuffer output = ByteBuffer.allocate(8 + body.length + signature.length);
        output.putInt(body.length).put(body).putInt(signature.length).put(signature).flip();
        try (Selector selector = Selector.open()) {
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_WRITE);
            while (output.hasRemaining()) {
                int n = channel.write(output);
                if (n == 0) await(selector, deadline, "STOP_WRITE_DEADLINE_EXCEEDED");
                else if (System.nanoTime() >= deadline)
                    throw new IOException("STOP_WRITE_DEADLINE_EXCEEDED");
            }
        }
    }

    private static long deadline(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        long millis = timeout.toMillis();
        if (millis < 1 || millis > 5000)
            throw new IllegalArgumentException("STOP timeout must be 1–5000 ms");
        return System.nanoTime() + timeout.toNanos();
    }

    private static void await(Selector selector, long deadline, String failure)
            throws IOException {
        long left = deadline - System.nanoTime();
        if (left <= 0) throw new IOException(failure);
        // Round up to avoid selector.select(0) (which can wait forever).
        long millis = Math.max(1L, (left + 999_999L) / 1_000_000L);
        if (selector.select(millis) == 0 && System.nanoTime() >= deadline)
            throw new IOException(failure);
        selector.selectedKeys().clear();
    }
}
