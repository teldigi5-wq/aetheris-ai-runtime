package io.aetheris.orchestrator.execution;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Restricted Stage 45.5.1 test-only local control wire protocol.
 * A controller signs a STOP request; a separately launched worker returns
 * an independently signed receipt. No RUN command or payload exists.
 * This is not mutual TLS, peer credential authentication, an owner session,
 * or a production-ready remote worker protocol.
 */
public final class SandboxWorkerStopWire {
    private static final String REQ_DOMAIN = "aetheris:stage45:local-stop-request:v1";
    private static final String ACK_DOMAIN = "aetheris:stage45:local-stop-receipt:v1";
    public static final String STOP_STATUS = "STOP_FENCE_PERSISTED_NO_OS_TASK";

    private SandboxWorkerStopWire() {}

    public record StopRequest(UUID nonce, String workerId, long issuedAt,
                              long expiresAt, long epoch) {
        public StopRequest {
            Objects.requireNonNull(nonce, "nonce");
            validateWorker(workerId);
            if (epoch < 1 || expiresAt <= issuedAt || expiresAt - issuedAt > 30)
                throw new IllegalArgumentException("Invalid STOP lease or epoch");
        }
    }

    public record StopReceipt(UUID nonce, String workerId, long epoch, String status) {
        public StopReceipt {
            Objects.requireNonNull(nonce, "nonce");
            validateWorker(workerId);
            if (epoch < 1 || !STOP_STATUS.equals(status))
                throw new IllegalArgumentException("Invalid STOP receipt status");
        }
    }

    public record SignedFrame(byte[] message, byte[] signature) {
        public SignedFrame {
            message = Objects.requireNonNull(message, "message").clone();
            signature = Objects.requireNonNull(signature, "signature").clone();
        }
        @Override public byte[] message() { return message.clone(); }
        @Override public byte[] signature() { return signature.clone(); }
    }

    public static byte[] requestBytes(StopRequest request) {
        Objects.requireNonNull(request, "request");
        return encode(out -> {
            out.writeUTF(REQ_DOMAIN);
            writeUuid(out, request.nonce());
            out.writeUTF(request.workerId());
            out.writeLong(request.issuedAt());
            out.writeLong(request.expiresAt());
            out.writeLong(request.epoch());
        });
    }

    public static StopRequest parseRequest(byte[] message) throws IOException {
        try (var in = new DataInputStream(new ByteArrayInputStream(message))) {
            if (!REQ_DOMAIN.equals(in.readUTF()))
                throw new IOException("Invalid STOP request domain");
            StopRequest req = new StopRequest(readUuid(in), in.readUTF(),
                    in.readLong(), in.readLong(), in.readLong());
            if (in.available() != 0 || !Arrays.equals(message, requestBytes(req)))
                throw new IOException("Non-canonical STOP request frame");
            return req;
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid STOP request fields", invalid);
        }
    }

    public static byte[] receiptBytes(StopReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        return encode(out -> {
            out.writeUTF(ACK_DOMAIN);
            writeUuid(out, receipt.nonce());
            out.writeUTF(receipt.workerId());
            out.writeLong(receipt.epoch());
            out.writeUTF(receipt.status());
        });
    }

    public static StopReceipt parseReceipt(byte[] message) throws IOException {
        try (var in = new DataInputStream(new ByteArrayInputStream(message))) {
            if (!ACK_DOMAIN.equals(in.readUTF()))
                throw new IOException("Invalid STOP receipt domain");
            StopReceipt receipt = new StopReceipt(readUuid(in), in.readUTF(),
                    in.readLong(), in.readUTF());
            if (in.available() != 0 || !Arrays.equals(message, receiptBytes(receipt)))
                throw new IOException("Non-canonical STOP receipt frame");
            return receipt;
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid STOP receipt fields", invalid);
        }
    }

    public static SignedFrame sign(byte[] message, PrivateKey key) {
        Objects.requireNonNull(message, "message");
        if (message.length < 1 || message.length > 512)
            throw new IllegalArgumentException("STOP frame oversized");
        try {
            Signature ed25519 = Signature.getInstance("Ed25519");
            ed25519.initSign(Objects.requireNonNull(key, "key"));
            ed25519.update(message);
            return new SignedFrame(message, ed25519.sign());
        } catch (GeneralSecurityException invalid) {
            throw new IllegalArgumentException("Invalid signing key", invalid);
        }
    }

    public static boolean verified(SignedFrame frame, PublicKey key) {
        if (frame == null || key == null) return false;
        byte[] content = frame.message();
        byte[] signature = frame.signature();
        if (content.length < 1 || content.length > 512 || signature.length != 64)
            return false;
        try {
            Signature ed25519 = Signature.getInstance("Ed25519");
            ed25519.initVerify(key);
            ed25519.update(content);
            return ed25519.verify(signature);
        } catch (GeneralSecurityException invalid) {
            return false;
        }
    }

    /** Length-bounded fixed binary framing. No arbitrary JSON or command field. */
    public static void writeFrame(DataOutputStream out, SignedFrame frame) throws IOException {
        byte[] body = frame.message();
        byte[] signature = frame.signature();
        if (body.length < 1 || body.length > 512 || signature.length != 64)
            throw new IOException("Invalid signed frame length");
        out.writeInt(body.length);
        out.write(body);
        out.writeInt(signature.length);
        out.write(signature);
        out.flush();
    }

    public static SignedFrame readFrame(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 1 || length > 512) throw new IOException("Frame body too large");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        int sigLength = in.readInt();
        if (sigLength != 64) throw new IOException("Invalid frame signature size");
        byte[] sig = new byte[sigLength];
        in.readFully(sig);
        return new SignedFrame(bytes, sig);
    }

    /**
     * Test-only synchronous client, with independent verification of the
     * worker's signed, bound receipt. No agent API exposes this method.
     */
    public static StopReceipt requestStop(Path socket, StopRequest request,
                                          PrivateKey controllerKey,
                                          PublicKey pinnedWorkerKey) throws IOException {
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(socket));
            var out = new DataOutputStream(Channels.newOutputStream(channel));
            var in = new DataInputStream(Channels.newInputStream(channel));
            writeFrame(out, sign(requestBytes(request), controllerKey));
            SignedFrame response = readFrame(in);
            if (!verified(response, pinnedWorkerKey))
                throw new SecurityException("WORKER_STOP_RECEIPT_SIGNATURE_INVALID");
            StopReceipt receipt = parseReceipt(response.message());
            if (!receipt.nonce().equals(request.nonce())
                    || !receipt.workerId().equals(request.workerId())
                    || receipt.epoch() != request.epoch())
                throw new SecurityException("WORKER_STOP_RECEIPT_BINDING_INVALID");
            return receipt;
        }
    }

    private interface Writer { void write(DataOutputStream out) throws IOException; }

    private static byte[] encode(Writer writer) {
        try {
            var data = new ByteArrayOutputStream(256);
            var out = new DataOutputStream(data);
            writer.write(out);
            out.flush();
            byte[] encoded = data.toByteArray();
            if (encoded.length > 512) throw new IllegalArgumentException("STOP payload too large");
            return encoded;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot encode STOP protocol", e);
        }
    }

    private static void validateWorker(String workerId) {
        if (workerId == null || !workerId.matches("[A-Za-z0-9_-]{1,80}"))
            throw new IllegalArgumentException("Invalid STOP worker ID");
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }
}
