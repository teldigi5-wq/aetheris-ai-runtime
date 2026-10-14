package io.aetheris.orchestrator.execution;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Test-only durable STOP marker carrying exact nonce + epoch. This lets a
 * restarted STOP-only JVM reconstruct a signed ACK for the SAME persisted
 * STOP (never a RUN action). A corrupt or partly written marker DENIES.
 */
public final class DurableStopMarkerLab {
    private static final Set<PosixFilePermission> PRIVATE_FILE =
            PosixFilePermissions.fromString("rw-------");

    private DurableStopMarkerLab() {}

    public record Marker(long epoch, UUID nonce) {
        public Marker {
            if (epoch < 1) throw new IllegalArgumentException("Invalid STOP epoch");
            Objects.requireNonNull(nonce, "nonce");
        }
    }

    public static boolean exists(Path file) {
        // A symlink or invalid STOP file must still prevent fresh activation.
        return Files.exists(file, LinkOption.NOFOLLOW_LINKS);
    }

    public static Marker requireValid(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(file).equals(PRIVATE_FILE)
                || Files.size(file) > 100)
            throw new SecurityException("INVALID_DURABLE_STOP_MARKER");
        String line = Files.readString(file, java.nio.charset.StandardCharsets.US_ASCII);
        if (!line.matches("STOPPED:[1-9][0-9]*:[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\n"))
            throw new SecurityException("INVALID_DURABLE_STOP_MARKER");
        String[] items = line.trim().split(":");
        try {
            long epoch = Long.parseLong(items[1]);
            UUID nonce = UUID.fromString(items[2]);
            if (!line.equals("STOPPED:" + epoch + ":" + nonce + "\n"))
                throw new SecurityException("NON_CANONICAL_STOP_MARKER");
            return new Marker(epoch, nonce);
        } catch (RuntimeException malformed) {
            throw new SecurityException("INVALID_DURABLE_STOP_MARKER", malformed);
        }
    }

    public static void latch(Path file, SandboxWorkerStopWire.StopRequest request)
            throws IOException {
        Objects.requireNonNull(request, "request");
        byte[] content = ("STOPPED:" + request.epoch() + ":" + request.nonce() + "\n")
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        // CREATE_NEW means a second different STOP cannot overwrite the
        // original persisted nonce/epoch, even after a previous connection
        // was interrupted. File content is never logged.
        try (FileChannel channel = FileChannel.open(file,
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(PRIVATE_FILE))) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }
}
