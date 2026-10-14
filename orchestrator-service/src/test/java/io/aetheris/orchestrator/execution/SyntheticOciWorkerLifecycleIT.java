package io.aetheris.orchestrator.execution;

import io.aetheris.orchestrator.execution.SignedSandboxJobGate.JobClaims;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.Profile;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.SignedJob;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.TrustedContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Explicit opt-in IT only: Maven Surefire excludes *IT from regular 'test'.
 * Uses ProcessBuilder ONLY in test source, only to invoke literal Docker
 * CLI with fixed OCI flags on disposable GitHub-hosted Linux runner.
 * This is NOT a deployable worker and does not create an agent execution API.
 */
@EnabledOnOs(OS.LINUX)
@SpringBootTest(properties = "aetheris.memory.retention-sweep-ms=3600000")
class SyntheticOciWorkerLifecycleIT {
    @TempDir Path directory;

    @Autowired SandboxWorkerFenceRepository fences;
    @Autowired SandboxJobReservationRepository reservations;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void signedOwnerGrantIsRequiredThenOciWorkerRunsUnderLimitsAndDbStopKillsTree()
            throws Exception {
        if (!"true".equals(System.getenv("GITHUB_ACTIONS"))
                || !"true".equals(System.getenv("AETHERIS_STAGE45_OCI_LIFECYCLE_CI")))
            throw new SecurityException("Disposable GitHub Actions OCI fixture only");

        String workerId = "ci_" + UUID.randomUUID().toString().replace("-", "");
        var fence = new SandboxWorkerFenceEntity(workerId);
        fence.armSyntheticFixture(); // test fixture only; no runtime rearm API
        fences.saveAndFlush(fence);

        KeyPair signer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        long now = Instant.now().getEpochSecond();
        var bad = claim(workerId, "wrongOwner", now);
        var valid = claim(workerId, "syntheticOwner", now);
        var controller = new DurableSandboxAdmissionLab(
                fences, reservations, transactionManager, signer.getPublic(),
                Clock.systemUTC(), taskId ->
                        taskId.equals(valid.taskId())
                                ? trusted(valid) : new TrustedContext(
                                        bad.taskId(), "approvedOwner", bad.projectId(),
                                        bad.workerId(), bad.approvalGrantId()));

        assertThatThrownBy(() -> controller.reserve(sign(signer, bad)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("OWNER_SCOPE_DENIED");
        assertThat(reservations.findById(bad.jobId())).isEmpty();

        assertThat(controller.reserve(sign(signer, valid))).isEqualTo(valid.jobId());
        assertThat(controller.reservationVisible(valid.jobId())).isTrue();
        assertThatThrownBy(() -> controller.reserve(sign(signer, valid)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("REPLAY");

        // The container runs as uid 65534 and must be able to traverse
        // temporary fixture paths, but only the test host can write them.
        Files.setPosixFilePermissions(directory,
                java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
        Path fixture = Files.createDirectory(directory.resolve("fixed"));
        Files.setPosixFilePermissions(fixture,
                java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
        Files.writeString(fixture.resolve("sleep-fixture.sh"), """
                #!/bin/sh
                set -eu
                test "$(id -u)" = "65534"
                test ! -e /var/run/docker.sock
                test -z "${AETHERIS_SYNTHETIC_HOST_SECRET:-}"
                test "$(awk 'END {print NR}' /proc/net/route)" -eq 1
                grep -Eq '^NoNewPrivs:[[:space:]]*1([[:space:]]*)$' /proc/self/status
                grep -Eq '^CapEff:[[:space:]]*0+([[:space:]]*)$' /proc/self/status
                if (echo UNSAFE > /workspace/forbidden-write 2>/dev/null); then exit 30; fi
                sleep 300 &
                sh -c 'sleep 300' &
                echo SYNTHETIC_SIGNED_WORKLOAD_READY
                wait
                """);
        Files.setPosixFilePermissions(fixture.resolve("sleep-fixture.sh"),
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
        String name = SyntheticOciSleepProfilePlan.containerName(valid.jobId());
        boolean created = false;
        try {
            var start = SyntheticOciSleepProfilePlan.build(fixture, valid.jobId());
            // The OCI worker does NOT independently verify this DB fence;
            // this is a lab controller-side gate and has a TOCTOU race.
            assertThat(controller.reservationVisible(valid.jobId())).isTrue();
            String container = docker(start);
            created = true;
            assertThat(container).isNotBlank();
            awaitReady(name);
            awaitDescendants(name);
            assertThat(docker(List.of("docker", "inspect", "--format",
                    "{{.HostConfig.NetworkMode}}", name))).isEqualTo("none");
            assertThat(docker(List.of("docker", "inspect", "--format",
                    "{{.HostConfig.ReadonlyRootfs}}", name))).isEqualTo("true");

            var receipt = controller.stopWorker(workerId);
            assertThat(receipt.status()).isEqualTo("STOP_PERSISTED_OS_TERMINATION_UNVERIFIED");
            assertThat(receipt.revokedRows()).isEqualTo(1);
            assertThat(controller.reservationVisible(valid.jobId())).isFalse();
            assertThatThrownBy(() -> controller.reserve(sign(signer,
                    claim(workerId, "syntheticOwner", Instant.now().getEpochSecond()))))
                    .isInstanceOf(SecurityException.class).hasMessageContaining("STOP_LATCHED");

            // The test controller explicitly issues OS kill after DB STOP.
            // This is NOT wired to TaskControlService or production worker.
            docker(List.of("docker", "kill", "--signal=KILL", name));
            awaitStopped(name);
            assertThat(Files.exists(fixture.resolve("forbidden-write"))).isFalse();
            assertThat(reservations.findById(valid.jobId()).orElseThrow().isRevoked()).isTrue();
            assertThat(fences.findById(workerId).orElseThrow().isStopped()).isTrue();
        } finally {
            if (created) {
                try { docker(List.of("docker", "rm", "-f", name)); }
                catch (Exception ignored) { /* cleanup best-effort on disposable CI */ }
            }
        }
    }

    private static JobClaims claim(String workerId, String owner, long now) {
        return new JobClaims(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                owner, "projectFixture", workerId, "syntheticApproval",
                Profile.SYNTHETIC_SLEEP, now - 1, now + 40, 500, 128, 32);
    }

    private static TrustedContext trusted(JobClaims c) {
        return new TrustedContext(c.taskId(), c.ownerId(), c.projectId(),
                c.workerId(), c.approvalGrantId());
    }

    private static SignedJob sign(KeyPair pair, JobClaims claim) throws Exception {
        var signature = Signature.getInstance("Ed25519");
        signature.initSign(pair.getPrivate());
        signature.update(SignedSandboxJobGate.canonical(claim));
        return new SignedJob(claim, signature.sign());
    }

    private static String docker(List<String> args) throws Exception {
        if (!"docker".equals(args.getFirst()))
            throw new SecurityException("Only synthetic Docker test control allowed");
        var pb = new ProcessBuilder(args).redirectErrorStream(true);
        pb.environment().put("AETHERIS_SYNTHETIC_HOST_SECRET", "NEVER_ENTER_CONTAINER");
        Process process = pb.start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Synthetic OCI control timed out");
        }
        String output = new String(process.getInputStream().readAllBytes()).trim();
        if (process.exitValue() != 0)
            throw new IllegalStateException("Synthetic OCI control failed: " + output);
        return output;
    }

    private static void awaitReady(String name) throws Exception {
        for (int i = 0; i < 40; i++) {
            String logs = docker(List.of("docker", "logs", name));
            if (logs.contains("SYNTHETIC_SIGNED_WORKLOAD_READY")) return;
            Thread.sleep(200);
        }
        throw new AssertionError("Synthetic OCI worker never reached ready state");
    }

    private static void awaitDescendants(String name) throws Exception {
        for (int i = 0; i < 40; i++) {
            String processes = docker(List.of("docker", "top", name,
                    "-eo", "pid,ppid,comm"));
            if (processes.lines().count() >= 4) return;
            Thread.sleep(200);
        }
        throw new AssertionError("Synthetic worker process descendants not observed");
    }

    private static void awaitStopped(String name) throws Exception {
        for (int i = 0; i < 35; i++) {
            String state = docker(List.of("docker", "inspect", "--format",
                    "{{.State.Running}}:{{.State.Pid}}", name));
            if ("false:0".equals(state)) {
                assertThatThrownBy(() -> docker(List.of("docker", "top", name)))
                        .isInstanceOf(IllegalStateException.class);
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("OS container process namespace did not terminate");
    }
}
