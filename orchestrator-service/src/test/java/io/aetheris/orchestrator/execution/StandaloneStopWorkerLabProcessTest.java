package io.aetheris.orchestrator.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real distinct JVM process + UNIX domain socket; no command-execution
 * interface, production token, real user memory, Docker or network port.
 */
@EnabledOnOs(OS.LINUX)
class StandaloneStopWorkerLabProcessTest {
    @TempDir Path temp;

    @Test
    void independentlySignedStopPersistsAcrossWorkerRestartAndForgedPeerIsDenied()
            throws Exception {
        Path root = Files.createDirectory(temp.resolve("control"));
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        KeyPair controller = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair worker = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair attacker = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();

        Path controllerPub = createPrivateFile(root.resolve("controller.pub"),
                controller.getPublic().getEncoded());
        Path workerPriv = createPrivateFile(root.resolve("worker.p8"),
                worker.getPrivate().getEncoded());
        Path socket = root.resolve("control.sock");
        String workerId = "isolatedSyntheticA";
        Process separateWorker = startWorker(root, workerId, controllerPub, workerPriv, socket);
        try {
            awaitSocket(separateWorker, socket);
            long now = Instant.now().getEpochSecond();
            var request = new SandboxWorkerStopWire.StopRequest(
                    UUID.randomUUID(), workerId, now - 1, now + 15, 5);

            // A completely valid signature by the wrong principal must never
            // produce an authenticated ACK or a STOP marker.
            assertThatThrownBy(() -> SandboxWorkerStopWire.requestStop(
                    socket, request, attacker.getPrivate(), worker.getPublic()))
                    .isInstanceOf(IOException.class);
            assertThat(Files.exists(root.resolve("STOP_LATCHED"))).isFalse();

            var receipt = SandboxWorkerStopWire.requestStop(
                    socket, request, controller.getPrivate(), worker.getPublic());
            assertThat(receipt.nonce()).isEqualTo(request.nonce());
            assertThat(receipt.workerId()).isEqualTo(workerId);
            assertThat(receipt.epoch()).isEqualTo(5);
            assertThat(receipt.status()).isEqualTo(
                    SandboxWorkerStopWire.STOP_STATUS);

            assertThat(separateWorker.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(separateWorker.exitValue()).isZero();
            Path marker = root.resolve("STOP_LATCHED");
            assertThat(Files.readString(marker)).isEqualTo("STOPPED:5\n");
            assertThat(Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)).isTrue();
            assertThat(Files.getPosixFilePermissions(marker))
                    .isEqualTo(PosixFilePermissions.fromString("rw-------"));
            assertThat(Files.exists(socket)).isFalse();

            // The same worker process cannot be restarted into an accepting
            // state with the durable synthetic STOP marker still present.
            Process restarted = startWorker(root, workerId, controllerPub, workerPriv, socket);
            try {
                assertThat(restarted.waitFor(5, TimeUnit.SECONDS)).isTrue();
                assertThat(restarted.exitValue()).isNotZero();
                assertThat(Files.exists(socket)).isFalse();
                assertThat(Files.readString(marker)).isEqualTo("STOPPED:5\n");
            } finally {
                restarted.destroyForcibly();
            }
        } finally {
            separateWorker.destroyForcibly();
            if (separateWorker.isAlive()) separateWorker.waitFor(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void receiptFromUntrustedWorkerCannotMasqueradeAsPinnedWorker() throws Exception {
        KeyPair real = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair stranger = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var receipt = new SandboxWorkerStopWire.StopReceipt(UUID.randomUUID(),
                "isolatedSyntheticA", 1, SandboxWorkerStopWire.STOP_STATUS);
        var forged = SandboxWorkerStopWire.sign(
                SandboxWorkerStopWire.receiptBytes(receipt), stranger.getPrivate());
        assertThat(SandboxWorkerStopWire.verified(forged, real.getPublic())).isFalse();
    }

    private static Path createPrivateFile(Path path, byte[] bytes) throws IOException {
        Files.write(path, bytes);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        return path;
    }

    private Process startWorker(Path root, String workerId, Path controllerPub,
                                Path workerPriv, Path socket) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Path classes = Path.of(
                StandaloneStopWorkerLabMain.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI());
        ProcessBuilder builder = new ProcessBuilder(java.toString(), "-cp",
                classes.toString(), StandaloneStopWorkerLabMain.class.getName(),
                root.toString(), workerId, controllerPub.toString(),
                workerPriv.toString(), socket.toString());
        builder.environment().put("CI", "true");
        builder.environment().put("AETHERIS_STAGE45_IPC_CI", "true");
        // No key material or production credentials in argv/environment.
        return builder.start();
    }

    private static void awaitSocket(Process worker, Path socket) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (Files.exists(socket, LinkOption.NOFOLLOW_LINKS)) return;
            if (!worker.isAlive()) {
                String logs = new String(worker.getErrorStream().readAllBytes());
                throw new AssertionError("Synthetic worker exited before bound: " + logs);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Synthetic worker failed to bind UNIX socket");
    }
}
