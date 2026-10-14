package io.aetheris.orchestrator.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real separate-JVM STOP exchange tied to a synthetic DB fence/outbox.
 * No task or command execution. CI-only Unix socket and generated keys.
 */
@SpringBootTest(properties = "aetheris.memory.retention-sweep-ms=3600000")
@EnabledOnOs(OS.LINUX)
class DurableStopIpcBridgeProcessTest {
    @TempDir Path temp;
    @Autowired SandboxWorkerFenceRepository fences;
    @Autowired SandboxJobReservationRepository reservations;
    @Autowired SandboxStopDispatchRepository outbox;
    @Autowired PlatformTransactionManager tx;

    @Test
    void disconnectedWorkerThenSeparateJvmReceivesDbStopAndRestartIsRecoveryOnly() throws Exception {
        String workerId = "ci_" + UUID.randomUUID().toString().replace("-", "");
        var fence = new SandboxWorkerFenceEntity(workerId);
        fence.armSyntheticFixture();
        fences.saveAndFlush(fence);

        KeyPair controller = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair worker = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair jobSigner = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();

        Path dir = Files.createDirectory(temp.resolve("lab"));
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
        Path controllerPublic = secretFile(dir.resolve("controller.pub"), controller.getPublic().getEncoded());
        Path workerPrivate = secretFile(dir.resolve("worker.p8"), worker.getPrivate().getEncoded());
        Path socket = dir.resolve("control.sock");

        var ledger = new DurableSandboxAdmissionLab(fences, reservations, tx,
                jobSigner.getPublic(), Clock.systemUTC(), ignored -> null);
        var bridge = bridge(ledger, controller, worker, socket);
        var offline = bridge.stopAndDeliver(workerId);
        assertThat(offline.state()).isEqualTo("STOP_PERSISTED_DELIVERY_PENDING");
        assertThat(outbox.findById(workerId).orElseThrow().isAcknowledged()).isFalse();
        assertThat(fences.findById(workerId).orElseThrow().isStopped()).isTrue();

        Process process = workerProcess(dir, workerId, controllerPublic, workerPrivate, socket);
        try {
            waitForSocket(process, socket);
            // A completely new bridge instance reconstructs its state from
            // the durable DB ledger; the socket points to a distinct JVM.
            var reconnected = bridge(ledger, controller, worker, socket);
            var acknowledged = reconnected.retryDelivery(workerId);
            assertThat(acknowledged.state())
                    .isEqualTo("SIGNED_WORKER_MARKER_ACKNOWLEDGED_NO_OS_EXIT");
            assertThat(acknowledged.epoch()).isEqualTo(offline.epoch());
            assertThat(outbox.findById(workerId).orElseThrow().isAcknowledged()).isTrue();
            assertThat(Files.readString(dir.resolve("STOP_LATCHED")))
                    .isEqualTo("STOPPED:" + offline.epoch() + ":"
                            + outbox.findById(workerId).orElseThrow().getNonce() + "\n");
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();

            Process restarted = workerProcess(dir, workerId,
                    controllerPublic, workerPrivate, socket);
            try {
                waitForSocket(restarted, socket);
                long now = Clock.systemUTC().instant().getEpochSecond();
                var repeat = new SandboxWorkerStopWire.StopRequest(
                        outbox.findById(workerId).orElseThrow().getNonce(),
                        workerId, now - 1, now + 15, offline.epoch());
                var receipt = SandboxWorkerStopWire.requestStop(
                        socket, repeat, controller.getPrivate(), worker.getPublic());
                assertThat(receipt.epoch()).isEqualTo(offline.epoch());
                assertThat(restarted.waitFor(10, TimeUnit.SECONDS)).isTrue();
                assertThat(restarted.exitValue()).isZero();
                assertThat(reconnected.snapshot(workerId).state())
                        .isEqualTo("SIGNED_WORKER_MARKER_ACKNOWLEDGED_NO_OS_EXIT");
            } finally {
                restarted.destroyForcibly();
                if (restarted.isAlive()) restarted.waitFor(2, TimeUnit.SECONDS);
            }
        } finally {
            process.destroyForcibly();
            if (process.isAlive()) process.waitFor(2, TimeUnit.SECONDS);
        }
    }

    private DurableStopIpcBridgeLab bridge(DurableSandboxAdmissionLab ledger,
                                            KeyPair controller, KeyPair worker,
                                            Path socket) {
        return new DurableStopIpcBridgeLab(ledger, fences, outbox, tx,
                controller.getPrivate(), worker.getPublic(), Clock.systemUTC(),
                frame -> SandboxWorkerStopWire.exchange(socket, frame));
    }

    private static Path secretFile(Path path, byte[] bytes) throws Exception {
        Files.write(path, bytes);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        return path;
    }

    private static Process workerProcess(Path dir, String workerId,
                                         Path publicKey, Path privateKey,
                                         Path socket) throws Exception {
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

    private static void waitForSocket(Process process, Path socket) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (Files.exists(socket, LinkOption.NOFOLLOW_LINKS)) return;
            if (!process.isAlive()) throw new AssertionError("Separate worker failed to bind");
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for lab STOP socket");
    }
}
