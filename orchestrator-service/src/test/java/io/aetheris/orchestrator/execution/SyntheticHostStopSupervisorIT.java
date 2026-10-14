package io.aetheris.orchestrator.execution;

import io.aetheris.orchestrator.execution.SignedSandboxJobGate.JobClaims;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.Profile;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.SignedJob;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.TrustedContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Explicit CI-only Docker control. No host command code exists in the main
 * runtime. The supervisor reads DB independently of worker self-report and
 * kills a fixed synthetic workload which deliberately never polls STOP.
 */
@SpringBootTest(properties = "aetheris.memory.retention-sweep-ms=3600000")
@EnabledOnOs(OS.LINUX)
class SyntheticHostStopSupervisorIT {
    @TempDir Path temp;
    @Autowired SandboxWorkerFenceRepository fences;
    @Autowired SandboxJobReservationRepository reservations;
    @Autowired PlatformTransactionManager transactions;

    private record Fixture(String workerId, JobClaims job,
                           DurableSandboxAdmissionLab admission,
                           SyntheticHostStopFenceLab supervisor,
                           Path directory, String name) {}

    private Fixture fixture(String suffix) throws Exception {
        if (!"true".equals(System.getenv("GITHUB_ACTIONS"))
                || !"true".equals(System.getenv("AETHERIS_STAGE45_HOST_SUPERVISOR_CI")))
            throw new SecurityException("HOST_SUPERVISOR_CI_ONLY");
        String worker = "ci_" + UUID.randomUUID().toString().replace("-", "");
        var fence = new SandboxWorkerFenceEntity(worker);
        fence.armSyntheticFixture();
        fences.saveAndFlush(fence);
        long now = Instant.now().getEpochSecond();
        var job = new JobClaims(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "syntheticOwner", "syntheticProject", worker, "syntheticGrant",
                Profile.SYNTHETIC_SLEEP, now - 1, now + 38, 500, 128, 32);
        KeyPair issuer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var context = new TrustedContext(job.taskId(), job.ownerId(),
                job.projectId(), worker, job.approvalGrantId());
        var admission = new DurableSandboxAdmissionLab(
                fences, reservations, transactions, issuer.getPublic(), Clock.systemUTC(),
                task -> task.equals(job.taskId()) ? context : null);
        var signature = Signature.getInstance("Ed25519");
        signature.initSign(issuer.getPrivate());
        signature.update(SignedSandboxJobGate.canonical(job));
        assertThat(admission.reserve(new SignedJob(job, signature.sign()))).isEqualTo(job.jobId());
        var supervisor = new SyntheticHostStopFenceLab(
                fences, reservations, transactions, Clock.systemUTC());

        Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rwxr-xr-x"));
        Path fixture = Files.createDirectory(temp.resolve(suffix));
        Files.setPosixFilePermissions(fixture, PosixFilePermissions.fromString("rwxr-xr-x"));
        // This script does NOT check STOP. An independent host observer,
        // not cooperative task code, must enforce cancellation.
        Files.writeString(fixture.resolve("sleep-fixture.sh"), """
                #!/bin/sh
                set -eu
                test "$(id -u)" = "65534"
                test ! -e /var/run/docker.sock
                test "$(awk 'END {print NR}' /proc/net/route)" -eq 1
                sleep 300 &
                sh -c 'sleep 300' &
                echo SYNTHETIC_UNRESPONSIVE_WORKER_READY
                wait
                """);
        Files.setPosixFilePermissions(fixture.resolve("sleep-fixture.sh"),
                PosixFilePermissions.fromString("rw-r--r--"));
        return new Fixture(worker, job, admission, supervisor, fixture,
                SyntheticOciSleepProfilePlan.containerName(job.jobId()));
    }

    @Test
    void hostObserverEnforcesCommittedStopWhenFixedWorkerIgnoresCancellation()
            throws Exception {
        Fixture f = fixture("unresponsive");
        boolean created = false;
        try {
            String id = docker(SyntheticOciSleepProfilePlan.build(f.directory(), f.job().jobId()));
            created = true;
            assertThat(id).matches("[0-9a-f]{64}");
            awaitReady(f.name());
            assertThat(docker(List.of("docker", "top", f.name(), "-eo",
                    "pid,ppid,comm")).lines().count()).isGreaterThanOrEqualTo(4);
            assertThat(f.supervisor().evaluate(f.job().jobId(), f.workerId(), 1).terminate())
                    .isFalse();

            KeyPair observer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            Future<SyntheticOciExitReceiptLab.SignedReceipt> report;
            try (var executor = Executors.newSingleThreadExecutor()) {
                report = executor.submit(() -> {
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
                    while (System.nanoTime() < deadline) {
                        var decision = f.supervisor().evaluate(
                                f.job().jobId(), f.workerId(), 1);
                        if (decision.terminate()) {
                            if (!"DATABASE_STOP_LATCHED".equals(decision.reason()))
                                throw new SecurityException("Unexpected synthetic kill cause");
                            // Fixed synthetic container name comes ONLY from
                            // the authorized job ID, never from an agent.
                            docker(List.of("docker", "kill", "--signal=KILL", f.name()));
                            awaitExited(f.name());
                            var status = docker(List.of("docker", "inspect", "--format",
                                    "{{.State.Running}}:{{.State.Pid}}:{{.State.ExitCode}}",
                                    f.name()));
                            assertThat(status).isEqualTo("false:0:137");
                            assertThatThrownBy(() -> docker(List.of("docker", "top", f.name())))
                                    .isInstanceOf(IllegalStateException.class);
                            var observation = new SyntheticOciExitReceiptLab.Observation(
                                    f.job().jobId(), f.workerId(), decision.observedEpoch(),
                                    id, Instant.now().getEpochSecond(), 137, false, 0,
                                    true, reservations.findById(f.job().jobId())
                                            .orElseThrow().isRevoked());
                            return SyntheticOciExitReceiptLab.signObservedExit(
                                    observation, observer.getPrivate());
                        }
                        Thread.sleep(100);
                    }
                    throw new AssertionError("Host supervisor did not see durable STOP");
                });
                // Different test thread persists DB STOP, while fixed OCI
                // workload remains intentionally unresponsive.
                Thread.sleep(250);
                assertThat(f.admission().stopWorker(f.workerId()).epoch()).isEqualTo(2);
                var signed = report.get(10, TimeUnit.SECONDS);
                assertThat(SyntheticOciExitReceiptLab.verify(
                        signed, observer.getPublic(), f.job().jobId(), f.workerId(),
                        2, id, Clock.systemUTC())).isTrue();
                assertThat(SyntheticOciExitReceiptLab.verify(
                        signed, observer.getPublic(), UUID.randomUUID(), f.workerId(),
                        2, id, Clock.systemUTC())).isFalse();
                assertThat(SyntheticOciExitReceiptLab.verify(
                        signed, observer.getPublic(), f.job().jobId(), f.workerId(),
                        1, id, Clock.systemUTC())).isFalse();
            }
            assertThat(fences.findById(f.workerId()).orElseThrow().isStopped()).isTrue();
            assertThat(reservations.findById(f.job().jobId())
                    .orElseThrow().isRevoked()).isTrue();
        } finally {
            if (created) cleanup(f.name());
        }
    }

    @Test
    void hostObserverKillsStaleEpochEvenWhenDatabaseWorkerNotStopped() throws Exception {
        Fixture f = fixture("stale-epoch");
        boolean created = false;
        try {
            String id = docker(SyntheticOciSleepProfilePlan.build(f.directory(), f.job().jobId()));
            created = true;
            assertThat(id).matches("[0-9a-f]{64}");
            awaitReady(f.name());
            var decision = f.supervisor().evaluate(f.job().jobId(), f.workerId(), 2);
            assertThat(decision.reason()).isEqualTo("STALE_STOP_EPOCH");
            assertThat(decision.terminate()).isTrue();
            docker(List.of("docker", "kill", "--signal=KILL", f.name()));
            awaitExited(f.name());
            assertThat(fences.findById(f.workerId()).orElseThrow().isStopped()).isFalse();
            // This outcome is deliberately NOT signed as successful STOP:
            // the database has not actually persisted the STOP epoch.
        } finally {
            if (created) cleanup(f.name());
        }
    }

    private static String docker(List<String> args) throws Exception {
        if (!"docker".equals(args.getFirst())) throw new SecurityException("CI_DOCKER_ONLY");
        var process = new ProcessBuilder(args).redirectErrorStream(true).start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Disposable Docker operation timeout");
        }
        String output = new String(process.getInputStream().readAllBytes()).trim();
        if (process.exitValue() != 0)
            throw new IllegalStateException("Synthetic Docker failed: " + output);
        return output;
    }

    private static void awaitReady(String name) throws Exception {
        for (int i = 0; i < 60; i++) {
            if (docker(List.of("docker", "logs", name))
                    .contains("SYNTHETIC_UNRESPONSIVE_WORKER_READY")) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Synthetic unresponsive worker failed to initialize");
    }

    private static void awaitExited(String name) throws Exception {
        for (int i = 0; i < 60; i++) {
            if (docker(List.of("docker", "inspect", "--format",
                    "{{.State.Running}}:{{.State.Pid}}", name)).equals("false:0"))
                return;
            Thread.sleep(100);
        }
        throw new AssertionError("Docker PID namespace did not exit");
    }

    private static void cleanup(String name) {
        try { docker(List.of("docker", "rm", "-f", name)); }
        catch (Exception ignored) { /* disposable CI cleanup only */ }
    }
}
