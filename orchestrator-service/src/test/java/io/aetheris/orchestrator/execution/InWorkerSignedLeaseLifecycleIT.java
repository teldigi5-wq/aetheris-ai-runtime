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
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dedicated CI-only real OCI + PostgreSQL test. The Go worker running INSIDE
 * the non-root, network-disabled container verifies a short signed lease
 * before fixed synthetic work and checks the lease repeatedly while running.
 * This Java test is the authorized synthetic *lease publisher*, not a
 * deployable agent worker/controller, and exercises zero real owner tasks.
 */
@EnabledOnOs(OS.LINUX)
@SpringBootTest(properties = "aetheris.memory.retention-sweep-ms=3600000")
class InWorkerSignedLeaseLifecycleIT {
    @TempDir Path temp;

    @Autowired SandboxWorkerFenceRepository fences;
    @Autowired SandboxJobReservationRepository reservations;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void workerRejectsTamperedLeaseThenStartsOnValidLeaseAndStopsAfterDbFence()
            throws Exception {
        if (!"true".equals(System.getenv("GITHUB_ACTIONS"))
                || !"true".equals(System.getenv("AETHERIS_STAGE45_INWORKER_CI")))
            throw new SecurityException("INWORKER_CI_ONLY");
        Path compiledGuard = Path.of(System.getProperty("aetheris.stage45.guard.bin", ""));
        if (!Files.isRegularFile(compiledGuard))
            throw new IllegalStateException("CI compiled Go lease verifier missing");

        String worker = "ci_" + UUID.randomUUID().toString().replace("-", "");
        var fence = new SandboxWorkerFenceEntity(worker);
        fence.armSyntheticFixture();
        fences.saveAndFlush(fence);

        KeyPair jobIssuer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair leaseIssuer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        long now = Instant.now().getEpochSecond();
        var job = new JobClaims(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "syntheticOwner", "syntheticProject",
                worker, "syntheticApproval", Profile.SYNTHETIC_SLEEP,
                now - 1, now + 45, 500, 128, 32);
        var trusted = new TrustedContext(job.taskId(), job.ownerId(), job.projectId(),
                job.workerId(), job.approvalGrantId());
        var admission = new DurableSandboxAdmissionLab(fences, reservations,
                transactions, jobIssuer.getPublic(), Clock.systemUTC(),
                id -> id.equals(job.taskId()) ? trusted : null);
        SignedJob signedJob = sign(jobIssuer, job);
        assertThat(admission.reserve(signedJob)).isEqualTo(job.jobId());

        var verifier = new WorkerSideFenceVerifierLab(fences, reservations,
                transactions, jobIssuer.getPublic(), Clock.systemUTC(),
                id -> id.equals(job.taskId()) ? trusted : null);
        assertThat(verifier.check(signedJob).epoch()).isEqualTo(1L);

        // Static fixed fixture: the OCI worker reads ONLY the public key,
        // signed lease, compiled Go guard, and harmless nested sleep script.
        Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rwxr-xr-x"));
        Path fixture = Files.createDirectory(temp.resolve("fixed"));
        Files.setPosixFilePermissions(fixture, PosixFilePermissions.fromString("rwxr-xr-x"));
        Path guard = fixture.resolve("stage45-lease-guard");
        Files.copy(compiledGuard, guard);
        Files.setPosixFilePermissions(guard, PosixFilePermissions.fromString("rwxr-xr-x"));
        Files.write(fixture.resolve("issuer.pub"), leaseIssuer.getPublic().getEncoded());
        Files.setPosixFilePermissions(fixture.resolve("issuer.pub"),
                PosixFilePermissions.fromString("rw-r--r--"));
        Files.writeString(fixture.resolve("sleep-fixture.sh"), """
                #!/bin/sh
                set -eu
                test "$(id -u)" = "65534"
                test ! -e /var/run/docker.sock
                test -z "${AETHERIS_SYNTHETIC_HOST_SECRET:-}"
                test "$(awk 'END {print NR}' /proc/net/route)" -eq 1
                sleep 300 &
                sh -c 'sleep 300' &
                wait
                """);
        Files.setPosixFilePermissions(fixture.resolve("sleep-fixture.sh"),
                PosixFilePermissions.fromString("rw-r--r--"));

        // The CI host owns the dedicated state directory, while group
        // 65534 gives the non-root OCI worker write access. Neither the
        // read-only workspace nor the host's credentials are writable.
        Path tamperState = prepareStateDirectory(temp.resolve("tamper-state"));
        Path state = prepareStateDirectory(temp.resolve("durable-stop-state"));
        String name = SyntheticOciSleepProfilePlan.containerName(job.jobId());
        boolean created = false;
        try {
            // Reject tampering BEFORE any child work can start inside OCI.
            publish(fixture, job, 1, "ALLOW", leaseIssuer, 25);
            Path leasePath = fixture.resolve("lease.txt");
            String correct = Files.readString(leasePath);
            Files.writeString(leasePath, correct.replace("|ALLOW|", "|STOPX|"));
            String invalidId = docker(InWorkerSignedLeaseOciPlan.build(
                    fixture, tamperState, job.jobId(), worker, 1));
            created = true;
            assertThat(invalidId).matches("[0-9a-f]{64}");
            awaitStopped(name, 54);
            assertThat(docker(List.of("docker", "logs", name)))
                    .contains("SYNTHETIC_INWORKER_INITIAL_DENIAL")
                    .doesNotContain("SYNTHETIC_INWORKER_SIGNED_READY");
            assertThat(Files.exists(tamperState.resolve("terminal.tombstone"))).isTrue();
            docker(List.of("docker", "rm", "-f", name));
            created = false;

            // Replaying an otherwise valid old ALLOW must fail because a
            // previous bad lease has persisted a DENY tombstone on the SAME
            // worker state mount. No shell children may launch.
            publish(fixture, job, 1, "ALLOW", leaseIssuer, 25);
            docker(InWorkerSignedLeaseOciPlan.build(
                    fixture, tamperState, job.jobId(), worker, 1));
            created = true;
            awaitStopped(name, 58);
            assertThat(docker(List.of("docker", "logs", name)))
                    .contains("SYNTHETIC_INWORKER_TERMINAL_LATCH_DENIED")
                    .doesNotContain("SYNTHETIC_INWORKER_SIGNED_READY");
            docker(List.of("docker", "rm", "-f", name));
            created = false;

            // Re-publish an authentic permit via atomic replace. The Go
            // process verifies it inside OCI without any signing key.
            publish(fixture, job, 1, "ALLOW", leaseIssuer, 25);
            String container = docker(InWorkerSignedLeaseOciPlan.build(
                    fixture, state, job.jobId(), worker, 1));
            created = true;
            assertThat(container).matches("[0-9a-f]{64}");
            awaitReady(name);
            assertThat(docker(List.of("docker", "inspect", "--format",
                    "{{.HostConfig.NetworkMode}}", name))).isEqualTo("none");
            assertThat(docker(List.of("docker", "inspect", "--format",
                    "{{.HostConfig.ReadonlyRootfs}}", name))).isEqualTo("true");
            assertThat(docker(List.of("docker", "top", name, "-eo",
                    "pid,ppid,comm")).lines().count()).isGreaterThanOrEqualTo(3);

            // STOP goes to persistent DB first, revoking reservation.
            var stop = admission.stopWorker(worker);
            assertThat(stop.epoch()).isEqualTo(2L);
            assertThat(admission.reservationVisible(job.jobId())).isFalse();
            assertThatThrownBy(() -> verifier.check(signedJob))
                    .isInstanceOf(SecurityException.class).hasMessageContaining("STOP");

            // Host does NOT kill a healthy cooperative worker in this test:
            // it signs only a STOP fact derived from the already committed
            // DB epoch. In-container Go checker notices it and kills its
            // own fixed child process group before exiting.
            publish(fixture, job, stop.epoch(), "STOP", leaseIssuer, 25);
            awaitStopped(name, 0);
            assertThat(docker(List.of("docker", "logs", name)))
                    .contains("SYNTHETIC_INWORKER_STOP_OBSERVED");
            assertThat(Files.exists(state.resolve("terminal.tombstone"))).isTrue();

            // Adversarial rollback: publisher replays an old, signed but
            // still within-expiry ALLOW. Re-created OCI worker must deny
            // *before* the fixed sleep script runs, even if STOP lease is
            // no longer present in /workspace. Persistent marker wins.
            docker(List.of("docker", "rm", "-f", name));
            created = false;
            publish(fixture, job, 1, "ALLOW", leaseIssuer, 25);
            docker(InWorkerSignedLeaseOciPlan.build(
                    fixture, state, job.jobId(), worker, 1));
            created = true;
            awaitStopped(name, 58);
            assertThat(docker(List.of("docker", "logs", name)))
                    .contains("SYNTHETIC_INWORKER_TERMINAL_LATCH_DENIED")
                    .doesNotContain("SYNTHETIC_INWORKER_SIGNED_READY");
            assertThat(Files.exists(state.resolve("terminal.tombstone"))).isTrue();
            assertThat(Files.exists(fixture.resolve("forbidden-write"))).isFalse();
            assertThat(reservations.findById(job.jobId()).orElseThrow().isRevoked()).isTrue();
            assertThat(fences.findById(worker).orElseThrow().isStopped()).isTrue();
        } finally {
            if (created) {
                try { docker(List.of("docker", "rm", "-f", name)); }
                catch (Exception ignored) { /* disposable CI cleanup best effort */ }
            }
        }
    }

    private static Path prepareStateDirectory(Path path) throws Exception {
        Files.createDirectory(path);
        // Ephemeral GitHub-hosted CI only: host owner retains directory
        // access, OCI worker gid 65534 receives *only* this group's
        // write permissions. No privileged action from runtime code.
        Process chgrp = new ProcessBuilder("sudo", "-n", "chgrp", "65534",
                path.toAbsolutePath().toString()).start();
        if (!chgrp.waitFor(5, TimeUnit.SECONDS) || chgrp.exitValue() != 0)
            throw new IllegalStateException("Synthetic CI state group setup failed");
        Files.setPosixFilePermissions(path,
                PosixFilePermissions.fromString("rwxrwx---"));
        return path;
    }

    private static void publish(Path fixture, JobClaims job, long epoch, String state,
                                KeyPair issuer, long ttlSeconds) throws Exception {
        // All fields fixed or derived from synthetic persisted task; no input
        // from an LLM or user can create this CI-only signed lease.
        long expiry = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = "AETHERIS45V1|" + job.workerId() + "|" + job.jobId()
                + "|" + epoch + "|" + expiry + "|" + state + "|SLEEP\n";
        var signer = Signature.getInstance("Ed25519");
        signer.initSign(issuer.getPrivate());
        signer.update(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String file = payload + HexFormat.of().formatHex(signer.sign()) + "\n";
        Path destination = fixture.resolve("lease.txt");
        Path staging = fixture.resolve("lease.tmp");
        Files.writeString(staging, file);
        Files.setPosixFilePermissions(staging, PosixFilePermissions.fromString("rw-r--r--"));
        Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static SignedJob sign(KeyPair signer, JobClaims job) throws Exception {
        var signature = Signature.getInstance("Ed25519");
        signature.initSign(signer.getPrivate());
        signature.update(SignedSandboxJobGate.canonical(job));
        return new SignedJob(job, signature.sign());
    }

    private static String docker(List<String> command) throws Exception {
        if (!"docker".equals(command.getFirst()))
            throw new SecurityException("FIXED_DOCKER_CI_ONLY");
        var pb = new ProcessBuilder(command).redirectErrorStream(true);
        pb.environment().put("AETHERIS_SYNTHETIC_HOST_SECRET", "CI_HOST_ONLY");
        var p = pb.start();
        if (!p.waitFor(20, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IllegalStateException("CI Docker control exceeded 20s");
        }
        String output = new String(p.getInputStream().readAllBytes()).trim();
        if (p.exitValue() != 0)
            throw new IllegalStateException("CI Docker control failed: " + output);
        return output;
    }

    private static void awaitReady(String name) throws Exception {
        for (int i = 0; i < 80; i++) {
            String logs = docker(List.of("docker", "logs", name));
            if (logs.contains("SYNTHETIC_INWORKER_SIGNED_READY")) return;
            Thread.sleep(100);
        }
        throw new AssertionError("In-worker lease validation never reached ready");
    }

    private static void awaitStopped(String name, int exitCode) throws Exception {
        for (int i = 0; i < 100; i++) {
            String status = docker(List.of("docker", "inspect", "--format",
                    "{{.State.Running}}:{{.State.Pid}}:{{.State.ExitCode}}", name));
            if (status.equals("false:0:" + exitCode)) {
                assertThatThrownBy(() -> docker(List.of("docker", "top", name)))
                        .isInstanceOf(IllegalStateException.class);
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Isolated signed worker did not exit with expected state");
    }
}
