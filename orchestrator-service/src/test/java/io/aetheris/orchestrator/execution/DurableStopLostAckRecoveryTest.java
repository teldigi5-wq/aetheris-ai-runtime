package io.aetheris.orchestrator.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real separate-JVM recovery after simulated ACK loss AFTER the worker wrote
 * its STOP marker. Never launches a task or obtains an owner credential.
 */
@SpringBootTest(properties = "aetheris.memory.retention-sweep-ms=3600000")
@EnabledOnOs(OS.LINUX)
class DurableStopLostAckRecoveryTest {
    @TempDir Path temp;
    @Autowired SandboxWorkerFenceRepository fences;
    @Autowired SandboxJobReservationRepository reservations;
    @Autowired SandboxStopDispatchRepository outbox;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void lostAcknowledgementAfterWorkerStoppedRecoversExactNonceAndEpoch() throws Exception {
        String workerId = "ci_" + UUID.randomUUID().toString().replace("-", "");
        var fence = new SandboxWorkerFenceEntity(workerId);
        fence.armSyntheticFixture();
        fences.saveAndFlush(fence);
        var generator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair controller = generator.generateKeyPair();
        KeyPair worker = generator.generateKeyPair();
        KeyPair issuer = generator.generateKeyPair();
        Path directory = Files.createDirectory(temp.resolve("stop-only"));
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        Path controllerPub = keyFile(directory.resolve("controller.pub"),
                controller.getPublic().getEncoded());
        Path workerPriv = keyFile(directory.resolve("worker.p8"),
                worker.getPrivate().getEncoded());
        Path socket = directory.resolve("control.sock");
        var ledger = new DurableSandboxAdmissionLab(fences, reservations, transactions,
                issuer.getPublic(), Clock.systemUTC(), ignored -> null);

        Process first = launchWorker(directory, workerId, controllerPub, workerPriv, socket);
        try {
            awaitSocket(first, socket);
            var bridgeWithLostReply = bridge(ledger, controller, worker, frame -> {
                // Worker signs a real reply, but the transport reports the
                // response lost before durable DB acknowledgement.
                SandboxWorkerStopWire.exchange(socket, frame);
                throw new IOException("synthetic ACK dropped after worker persistence");
            });
            var pending = bridgeWithLostReply.stopAndDeliver(workerId);
            assertThat(pending.state()).isEqualTo("STOP_PERSISTED_DELIVERY_PENDING");
            assertThat(first.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(first.exitValue()).isZero();
            assertThat(Files.exists(socket, LinkOption.NOFOLLOW_LINKS)).isFalse();
            var outboxRow = outbox.findById(workerId).orElseThrow();
            UUID originalNonce = outboxRow.getNonce();
            assertThat(outboxRow.isAcknowledged()).isFalse();
            assertThat(Files.readString(directory.resolve("STOP_LATCHED")))
                    .isEqualTo("STOPPED:" + pending.epoch() + ":" + originalNonce + "\n");

            // A separate, restarted STOP-only worker rejects an unrelated
            // signed request but re-acknowledges the original durable STOP.
            Process resumed = launchWorker(directory, workerId, controllerPub, workerPriv, socket);
            try {
                awaitSocket(resumed, socket);
                long now = Instant.now().getEpochSecond();
                var wrong = new SandboxWorkerStopWire.StopRequest(
                        UUID.randomUUID(), workerId, now - 1, now + 15, pending.epoch());
                assertThatThrownBy(() -> SandboxWorkerStopWire.requestStop(
                        socket, wrong, controller.getPrivate(), worker.getPublic()))
                        .isInstanceOf(IOException.class);
                assertThat(outbox.findById(workerId).orElseThrow().isAcknowledged()).isFalse();

                var newCoordinator = bridge(ledger, controller, worker,
                        frame -> SandboxWorkerStopWire.exchange(socket, frame));
                var verified = newCoordinator.retryDelivery(workerId);
                assertThat(verified.state())
                        .isEqualTo("SIGNED_WORKER_MARKER_ACKNOWLEDGED_NO_OS_EXIT");
                assertThat(outbox.findById(workerId).orElseThrow().getNonce())
                        .isEqualTo(originalNonce);
                assertThat(outbox.findById(workerId).orElseThrow().isAcknowledged()).isTrue();
                assertThat(resumed.waitFor(10, TimeUnit.SECONDS)).isTrue();
                assertThat(resumed.exitValue()).isZero();
                assertThat(Files.exists(socket, LinkOption.NOFOLLOW_LINKS)).isFalse();
                // This is the STOP-only worker JVM's exit; there is NO
                // executed task whose process-tree exit is being attested.
            } finally {
                resumed.destroyForcibly();
                if (resumed.isAlive()) resumed.waitFor(2, TimeUnit.SECONDS);
            }
        } finally {
            first.destroyForcibly();
            if (first.isAlive()) first.waitFor(2, TimeUnit.SECONDS);
        }
    }

    private DurableStopIpcBridgeLab bridge(DurableSandboxAdmissionLab ledger,
            KeyPair controller, KeyPair worker, DurableStopIpcBridgeLab.StopTransport transport) {
        return new DurableStopIpcBridgeLab(ledger, fences, outbox, transactions,
                controller.getPrivate(), worker.getPublic(), Clock.systemUTC(), transport);
    }

    private static Path keyFile(Path path, byte[] bytes) throws Exception {
        Files.write(path, bytes);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        return path;
    }

    private static Process launchWorker(Path dir, String workerId, Path publicKey,
                                        Path privateKey, Path socket) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Path classes = Path.of(StandaloneStopWorkerLabMain.class
                .getProtectionDomain().getCodeSource().getLocation().toURI());
        var pb = new ProcessBuilder(java.toString(), "-cp", classes.toString(),
                StandaloneStopWorkerLabMain.class.getName(),
                dir.toString(), workerId, publicKey.toString(),
                privateKey.toString(), socket.toString());
        pb.environment().put("CI", "true");
        pb.environment().put("AETHERIS_STAGE45_IPC_CI", "true");
        return pb.start();
    }

    private static void awaitSocket(Process process, Path socket) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (Files.exists(socket, LinkOption.NOFOLLOW_LINKS)) return;
            if (!process.isAlive())
                throw new AssertionError("STOP-only worker did not bind");
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for signed STOP-only socket");
    }
}
