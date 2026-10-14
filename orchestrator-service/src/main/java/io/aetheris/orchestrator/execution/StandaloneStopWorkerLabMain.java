package io.aetheris.orchestrator.execution;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Set;

/**
 * Separate-JVM UNIX-domain-socket STOP worker. CI-only laboratory entrypoint.
 *
 * It can ONLY persist an irreversible STOP fence and acknowledge it with
 * Ed25519. There is no launch/execute command, no Docker socket, no arbitrary
 * data input, no Spring bean and no network listener. In particular, this
 * class is NOT the OS-confined task-execution worker required for production.
 */
public final class StandaloneStopWorkerLabMain {
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY =
            PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> PRIVATE_FILE =
            PosixFilePermissions.fromString("rw-------");

    private StandaloneStopWorkerLabMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5
                || !"true".equals(System.getenv("CI"))
                || !"true".equals(System.getenv("AETHERIS_STAGE45_IPC_CI"))
                || !System.getProperty("os.name", "").toLowerCase().contains("linux"))
            throw new SecurityException("LAB_ONLY_UNAVAILABLE");

        Path dir = Path.of(args[0]).toAbsolutePath().normalize();
        String workerId = args[1];
        Path controllerPublicFile = Path.of(args[2]).toAbsolutePath().normalize();
        Path workerPrivateFile = Path.of(args[3]).toAbsolutePath().normalize();
        Path socket = Path.of(args[4]).toAbsolutePath().normalize();

        if (workerId == null || !workerId.matches("[A-Za-z0-9_-]{1,80}"))
            throw new SecurityException("INVALID_WORKER");
        assertNoSymlinks(dir);
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(dir).equals(PRIVATE_DIRECTORY))
            throw new SecurityException("PRIVATE_WORKER_DIRECTORY_REQUIRED");
        if (!socket.equals(dir.resolve("control.sock")))
            throw new SecurityException("SOCKET_LOCATION_DENIED");
        byte[] publicBytes = readPrivateFile(controllerPublicFile);
        byte[] secretBytes = readPrivateFile(workerPrivateFile);
        PublicKey controllerPublic = KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(publicBytes));
        PrivateKey workerPrivate = KeyFactory.getInstance("Ed25519")
                .generatePrivate(new PKCS8EncodedKeySpec(secretBytes));
        java.util.Arrays.fill(secretBytes, (byte) 0);

        Path stopMarker = dir.resolve("STOP_LATCHED");
        if (Files.exists(stopMarker, LinkOption.NOFOLLOW_LINKS))
            throw new SecurityException("DURABLE_STOP_ALREADY_LATCHED");
        if (Files.exists(socket, LinkOption.NOFOLLOW_LINKS))
            throw new SecurityException("SOCKET_ALREADY_EXISTS");

        // This is local IPC, not TCP. Directory and socket permissions are
        // supplementary controls; pinned signatures enforce sender identity.
        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            server.bind(UnixDomainSocketAddress.of(socket));
            Files.setPosixFilePermissions(socket, PRIVATE_FILE);
            System.out.println("SYNTHETIC_STOP_WORKER_BOUND");
            System.out.flush();
            for (int attempts = 0; attempts < 12; attempts++) {
                try (SocketChannel channel = server.accept()) {
                    var in = new DataInputStream(Channels.newInputStream(channel));
                    var out = new DataOutputStream(Channels.newOutputStream(channel));
                    var frame = SandboxWorkerStopWire.readFrame(in);
                    if (!SandboxWorkerStopWire.verified(frame, controllerPublic))
                        throw new SecurityException("CONTROLLER_SIGNATURE_DENIED");

                    var request = SandboxWorkerStopWire.parseRequest(frame.message());
                    long now = Instant.now().getEpochSecond();
                    if (!request.workerId().equals(workerId)
                            || request.issuedAt() > now
                            || request.expiresAt() <= now)
                        throw new SecurityException("WORKER_SCOPE_OR_LEASE_DENIED");

                    // Persist STOP before issuing any acknowledgement. The
                    // marker remains if the peer closes or the JVM exits.
                    persistStopMarker(stopMarker, request.epoch());
                    var receipt = new SandboxWorkerStopWire.StopReceipt(
                            request.nonce(), workerId, request.epoch(),
                            SandboxWorkerStopWire.STOP_STATUS);
                    SandboxWorkerStopWire.writeFrame(out,
                            SandboxWorkerStopWire.sign(
                                    SandboxWorkerStopWire.receiptBytes(receipt), workerPrivate));
                    System.out.println("SYNTHETIC_STOP_FENCE_PERSISTED");
                    return;
                } catch (IOException | SecurityException rejected) {
                    // Do not leak key bytes, signatures, request data,
                    // filesystem paths or owner metadata into CI logs.
                    if (Files.exists(stopMarker, LinkOption.NOFOLLOW_LINKS)) return;
                    System.err.println("SYNTHETIC_STOP_REQUEST_DENIED");
                }
            }
            throw new SecurityException("TOO_MANY_INVALID_STOP_REQUESTS");
        } finally {
            Files.deleteIfExists(socket);
        }
    }

    private static byte[] readPrivateFile(Path path) throws IOException {
        assertNoSymlinks(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(path).equals(PRIVATE_FILE)
                || Files.size(path) > 2048)
            throw new SecurityException("INVALID_TEST_KEY_PERMISSIONS");
        return Files.readAllBytes(path);
    }

    private static void assertNoSymlinks(Path path) {
        for (Path part = path; part != null; part = part.getParent()) {
            if (Files.isSymbolicLink(part))
                throw new SecurityException("SYMLINKED_WORKER_PATH_DENIED");
        }
    }

    private static void persistStopMarker(Path path, long epoch) throws IOException {
        byte[] content = ("STOPPED:" + epoch + "\n")
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        try (FileChannel file = FileChannel.open(path,
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(PRIVATE_FILE))) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) file.write(buffer);
            file.force(true);
        }
    }
}
