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
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Actual two-process CI lab: Java synthetic DB heartbeat publisher and
 * separately launched Go host watchdog. The Go watchdog uses fixed Docker
 * kill on an unresponsive digest-pinned OCI sleep fixture. No production
 * owner data, user commands, or runtime process adapter.
 */
@SpringBootTest(properties = "aetheris.memory.retention-sweep-ms=3600000")
@EnabledOnOs(OS.LINUX)
class SyntheticSignedHostWatchdogIT {
    @TempDir Path root;
    @Autowired SandboxWorkerFenceRepository fences;
    @Autowired SandboxJobReservationRepository reservations;
    @Autowired SyntheticSupervisorPulseRepository pulses;
    @Autowired PlatformTransactionManager transactions;

    private record Fixture(String worker, JobClaims job, String containerName,
                           Path publicFile, Path heartbeatFile, Path stateDirectory,
                           KeyPair publisher, DurableSandboxAdmissionLab db,
                           SyntheticSupervisorPulseLab issuer) {}

    private Fixture setup(String tag) throws Exception {
        if (!"true".equals(System.getenv("GITHUB_ACTIONS"))
                || !"true".equals(System.getenv("AETHERIS_STAGE45_WATCH_CI")))
            throw new SecurityException("SYNTHETIC_CI_HOST_WATCH_ONLY");
        String worker="ci_"+UUID.randomUUID().toString().replace("-","");
        var fence=new SandboxWorkerFenceEntity(worker);
        fence.armSyntheticFixture();
        fences.saveAndFlush(fence);
        long now=Instant.now().getEpochSecond();
        var job=new JobClaims(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                "ciOwner","ciProject",worker,"ciGrant",Profile.SYNTHETIC_SLEEP,
                now-1,now+40,500,128,32);
        KeyPair signer=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair publisher=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var trusted=new TrustedContext(job.taskId(),job.ownerId(),job.projectId(),
                worker,job.approvalGrantId());
        var db=new DurableSandboxAdmissionLab(fences,reservations,transactions,
                signer.getPublic(),Clock.systemUTC(),task -> task.equals(job.taskId())?trusted:null);
        var sig=Signature.getInstance("Ed25519");
        sig.initSign(signer.getPrivate());
        sig.update(SignedSandboxJobGate.canonical(job));
        assertThat(db.reserve(new SignedJob(job,sig.sign()))).isEqualTo(job.jobId());

        Files.setPosixFilePermissions(root,PosixFilePermissions.fromString("rwxr-xr-x"));
        Path fixture=Files.createDirectory(root.resolve(tag));
        Files.setPosixFilePermissions(fixture,PosixFilePermissions.fromString("rwxr-xr-x"));
        Files.writeString(fixture.resolve("sleep-fixture.sh"),"""
                #!/bin/sh
                set -eu
                test "$(id -u)" = "65534"
                test ! -e /var/run/docker.sock
                sleep 300 &
                sh -c 'sleep 300' &
                echo SYNTHETIC_UNRESPONSIVE_WATCHDOG_FIXTURE_READY
                wait
                """);
        Files.setPosixFilePermissions(fixture.resolve("sleep-fixture.sh"),
                PosixFilePermissions.fromString("rw-r--r--"));
        String container=docker(SyntheticOciSleepProfilePlan.build(fixture,job.jobId()));
        assertThat(container).matches("[0-9a-f]{64}");
        awaitReady(container);
        Path publicKey=Files.write(root.resolve(tag+"-public.der"),publisher.getPublic().getEncoded());
        Files.setPosixFilePermissions(publicKey,PosixFilePermissions.fromString("rw-------"));
        Path heartbeat=root.resolve(tag+"-heartbeat.txt");
        Path stateDirectory=Files.createDirectory(root.resolve(tag+"-watch-state"));
        Files.setPosixFilePermissions(stateDirectory,PosixFilePermissions.fromString("rwx------"));
        var issuer=new SyntheticSupervisorPulseLab(fences,reservations,pulses,
                transactions,Clock.systemUTC(),publisher.getPrivate());
        return new Fixture(worker,job,container,publicKey,heartbeat,stateDirectory,publisher,db,issuer);
    }

    @Test
    void signedStopFromCommittedPostgresEpochForcesUncooperativeWorkerExit()
            throws Exception {
        Fixture f=setup("stopped");
        Process watchdog=null;
        try {
            publish(f,"ACTIVE",1);
            watchdog=startWatchdog(f);
            Thread.sleep(200);
            assertThat(watchdog.isAlive()).isTrue();
            assertThat(f.db().stopWorker(f.worker()).epoch()).isEqualTo(2L);
            publish(f,"STOP",2);
            assertThat(watchdog.waitFor(8,TimeUnit.SECONDS)).isTrue();
            String logs=new String(watchdog.getInputStream().readAllBytes());
            assertThat(watchdog.exitValue()).isZero();
            assertThat(logs).contains("SYNTHETIC_HOST_WATCH_KILL_REASON=SIGNED_STOP_OBSERVED",
                    "SYNTHETIC_HOST_WATCH_TERMINATION_OBSERVED");
            assertStopped(f.containerName());
            assertThat(reservations.findById(f.job().jobId()).orElseThrow().isRevoked()).isTrue();
            assertThatThrownByActiveAfterStop(f);
            // Synthetic independent observation, not kernel attestation:
            var reporter=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            var ob=new SyntheticOciExitReceiptLab.Observation(
                    f.job().jobId(),f.worker(),2,f.containerName(),
                    Instant.now().getEpochSecond(),137,false,0,true,true);
            var signed=SyntheticOciExitReceiptLab.signObservedExit(ob,reporter.getPrivate());
            assertThat(SyntheticOciExitReceiptLab.verify(signed,reporter.getPublic(),
                    f.job().jobId(),f.worker(),2,f.containerName(),Clock.systemUTC())).isTrue();
        } finally {
            if(watchdog!=null && watchdog.isAlive())watchdog.destroyForcibly();
            remove(f.containerName());
        }
    }

    @Test
    void disconnectedPublisherCausesBoundedHostKillEvenThoughDbFenceIsStillActive()
            throws Exception {
        Fixture f=setup("silent");
        Process watchdog=null;
        try {
            publish(f,"ACTIVE",1);
            watchdog=startWatchdog(f);
            // CI controller intentionally issues no more pulses, representing
            // disconnected DB publisher or crashed signer. A still-running
            // noncooperative fixed worker must be forcibly killed at expiry.
            assertThat(watchdog.waitFor(9,TimeUnit.SECONDS)).isTrue();
            String logs=new String(watchdog.getInputStream().readAllBytes());
            assertThat(watchdog.exitValue()).isZero();
            assertThat(logs).contains("SYNTHETIC_HOST_WATCH_KILL_REASON=HEARTBEAT_UNAVAILABLE_OR_EXPIRED",
                    "SYNTHETIC_HOST_WATCH_TERMINATION_OBSERVED");
            assertStopped(f.containerName());
            assertThat(fences.findById(f.worker()).orElseThrow().isStopped()).isFalse();
            assertThat(reservations.findById(f.job().jobId()).orElseThrow().isRevoked()).isFalse();
            // Deliberately NO signed STOP exit receipt because the DB has
            // NOT persisted a STOP. Liveness kill is not revocation proof.
        } finally {
            if(watchdog!=null && watchdog.isAlive())watchdog.destroyForcibly();
            remove(f.containerName());
        }
    }

    @Test
    void abruptWatchdogCrashRequiresFreshSignedSequenceInsteadOfOldActiveReplay()
            throws Exception {
        Fixture f=setup("restart-replay");
        Process first=null, restarted=null;
        try {
            publish(f,"ACTIVE",1);
            first=startWatchdog(f);
            awaitStateWritten(f.stateDirectory().resolve("watch.state"));
            assertThat(first.isAlive()).isTrue();
            // The CI host kills ONLY the watchdog, not the OCI worker.
            // Its latest signed ACTIVE heartbeat is still unexpired.
            first.destroyForcibly();
            assertThat(first.waitFor(3,TimeUnit.SECONDS)).isTrue();
            assertThat(docker(List.of("docker","inspect","--format",
                    "{{.State.Running}}",f.containerName()))).isEqualTo("true");

            restarted=startWatchdog(f);
            assertThat(restarted.waitFor(8,TimeUnit.SECONDS)).isTrue();
            String logs=new String(restarted.getInputStream().readAllBytes());
            assertThat(restarted.exitValue()).isZero();
            assertThat(logs).contains(
                    "SYNTHETIC_HOST_WATCH_KILL_REASON=RESTART_REPLAYED_HEARTBEAT",
                    "SYNTHETIC_HOST_WATCH_TERMINATION_OBSERVED");
            assertStopped(f.containerName());
            // Database was not stopped. The watcher performed a conservative
            // liveness kill, and may NEVER sign a revocation/STOP receipt.
            assertThat(fences.findById(f.worker()).orElseThrow().isStopped()).isFalse();
        } finally {
            if(first!=null && first.isAlive()) first.destroyForcibly();
            if(restarted!=null && restarted.isAlive()) restarted.destroyForcibly();
            remove(f.containerName());
        }
    }

    @Test
    void corruptedHostHighWaterCannotBeIgnoredToContinueRunning()
            throws Exception {
        Fixture f=setup("corrupt-state");
        Process watcher=null;
        try {
            publish(f,"ACTIVE",1);
            Files.writeString(f.stateDirectory().resolve("watch.state"),"corrupted-counter");
            Files.setPosixFilePermissions(f.stateDirectory().resolve("watch.state"),
                    PosixFilePermissions.fromString("rw-------"));
            watcher=startWatchdog(f);
            assertThat(watcher.waitFor(8,TimeUnit.SECONDS)).isTrue();
            String logs=new String(watcher.getInputStream().readAllBytes());
            assertThat(watcher.exitValue()).isZero();
            assertThat(logs).contains(
                    "SYNTHETIC_HOST_WATCH_KILL_REASON=WATCH_STATE_INVALID",
                    "SYNTHETIC_HOST_WATCH_TERMINATION_OBSERVED");
            assertStopped(f.containerName());
        } finally {
            if(watcher!=null && watcher.isAlive()) watcher.destroyForcibly();
            remove(f.containerName());
        }
    }

    private static void awaitStateWritten(Path file) throws Exception {
        for(int i=0;i<60;i++) {
            if(Files.isRegularFile(file) && Files.size(file)>10) return;
            Thread.sleep(25);
        }
        throw new AssertionError("CI watchdog did not durably persist heartbeat");
    }

    private static void assertThatThrownByActiveAfterStop(Fixture f) {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> f.issuer().publish(f.job().jobId(),f.worker(),
                        f.containerName(),1,"ACTIVE"))
                .isInstanceOf(SecurityException.class);
    }

    private static void publish(Fixture f,String state,long epoch) throws Exception {
        String signed=f.issuer().publish(f.job().jobId(),f.worker(),
                f.containerName(),epoch,state);
        Path pending=f.heartbeatFile().resolveSibling(
                f.heartbeatFile().getFileName()+".tmp");
        Files.writeString(pending,signed);
        Files.setPosixFilePermissions(pending,PosixFilePermissions.fromString("rw-------"));
        Files.move(pending,f.heartbeatFile(),StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    private static Process startWatchdog(Fixture f) throws Exception {
        Path binary=Path.of(System.getProperty("aetheris.stage45.watchdog.bin",""));
        if(!Files.isRegularFile(binary))throw new SecurityException("SYNTHETIC_WATCHDOG_BINARY_MISSING");
        var builder=new ProcessBuilder(binary.toString(),f.publicFile().toString(),
                f.heartbeatFile().toString(),f.worker(),f.job().jobId().toString(),
                f.containerName(),"1",f.stateDirectory().toString()).redirectErrorStream(true);
        builder.environment().put("AETHERIS_STAGE45_WATCH_CI","true");
        return builder.start();
    }

    private static String docker(List<String> argv) throws Exception {
        if(!"docker".equals(argv.getFirst()))throw new SecurityException("CI_ONLY_DOCKER_CONTROL");
        var p=new ProcessBuilder(argv).redirectErrorStream(true).start();
        if(!p.waitFor(20,TimeUnit.SECONDS)) {
            p.destroyForcibly();throw new AssertionError("Synthetic Docker CLI timed out");
        }
        String out=new String(p.getInputStream().readAllBytes()).trim();
        if(p.exitValue()!=0)throw new IllegalStateException("Synthetic Docker CLI failed: "+out);
        return out;
    }

    private static void awaitReady(String id) throws Exception {
        for(int i=0;i<75;i++){
            if(docker(List.of("docker","logs",id))
                    .contains("SYNTHETIC_UNRESPONSIVE_WATCHDOG_FIXTURE_READY"))return;
            Thread.sleep(80);
        }
        throw new AssertionError("Synthetic OCI did not start");
    }

    private static void assertStopped(String id) throws Exception {
        assertThat(docker(List.of("docker","inspect","--format",
                "{{.State.Running}}:{{.State.Pid}}:{{.State.ExitCode}}",id)))
                .isEqualTo("false:0:137");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> docker(List.of("docker","top",id)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static void remove(String id) {
        try {docker(List.of("docker","rm","-f",id));}
        catch(Exception ignored){ /* disposable CI cleanup */ }
    }
}
