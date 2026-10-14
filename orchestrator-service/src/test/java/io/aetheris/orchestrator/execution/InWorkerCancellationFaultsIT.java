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
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adversarial, synthetic-only separate OCI/Go/JPA scenarios:
 * prelaunch STOP, lost publisher, short-lived permit expiry, host kill.
 * No production runner, owner data, or general command arguments.
 */
@EnabledOnOs(OS.LINUX)
@SpringBootTest(properties = "aetheris.memory.retention-sweep-ms=3600000")
class InWorkerCancellationFaultsIT {
    @TempDir Path temp;
    @Autowired SandboxWorkerFenceRepository fences;
    @Autowired SandboxJobReservationRepository reservations;
    @Autowired PlatformTransactionManager transactions;

    private record Scenario(String worker, JobClaims job, Path fixture, Path state,
                            KeyPair issuer, DurableSandboxAdmissionLab db, String name) {}

    private Scenario scenario(String tag) throws Exception {
        if (!"true".equals(System.getenv("GITHUB_ACTIONS"))
                || !"true".equals(System.getenv("AETHERIS_STAGE45_INWORKER_CI")))
            throw new SecurityException("DISPOSABLE_INWORKER_CI_ONLY");
        Path compiled = Path.of(System.getProperty("aetheris.stage45.guard.bin",""));
        if (!Files.isRegularFile(compiled)) throw new SecurityException("GUARD_BINARY_MISSING");

        String worker = "ci_" + UUID.randomUUID().toString().replace("-","");
        var fence = new SandboxWorkerFenceEntity(worker);
        fence.armSyntheticFixture();
        fences.saveAndFlush(fence);
        KeyPair issuer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair grant = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        long now = Instant.now().getEpochSecond();
        var job = new JobClaims(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "fixtureOwner", "fixtureProject", worker, "fixtureApproval",
                Profile.SYNTHETIC_SLEEP, now - 1, now + 38, 500, 128, 32);
        var trusted = new TrustedContext(job.taskId(),job.ownerId(),job.projectId(),
                job.workerId(),job.approvalGrantId());
        var db = new DurableSandboxAdmissionLab(fences,reservations,transactions,
                grant.getPublic(),Clock.systemUTC(),
                id -> id.equals(job.taskId()) ? trusted : null);
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(grant.getPrivate());
        signature.update(SignedSandboxJobGate.canonical(job));
        assertThat(db.reserve(new SignedJob(job,signature.sign()))).isEqualTo(job.jobId());

        Files.setPosixFilePermissions(temp,PosixFilePermissions.fromString("rwxr-xr-x"));
        Path fixture = Files.createDirectory(temp.resolve("fixture-"+tag));
        Files.setPosixFilePermissions(fixture,PosixFilePermissions.fromString("rwxr-xr-x"));
        Files.copy(compiled,fixture.resolve("stage45-lease-guard"));
        Files.setPosixFilePermissions(fixture.resolve("stage45-lease-guard"),
                PosixFilePermissions.fromString("rwxr-xr-x"));
        Files.write(fixture.resolve("issuer.pub"),issuer.getPublic().getEncoded());
        Files.setPosixFilePermissions(fixture.resolve("issuer.pub"),
                PosixFilePermissions.fromString("rw-r--r--"));
        // Harmless, completely fixed workload. No user code or tools.
        Files.writeString(fixture.resolve("sleep-fixture.sh"), "sleep 300 & wait\n");
        Files.setPosixFilePermissions(fixture.resolve("sleep-fixture.sh"),
                PosixFilePermissions.fromString("rw-r--r--"));
        Path state=Files.createDirectory(temp.resolve("state-"+tag));
        Process group = new ProcessBuilder("sudo","-n","chgrp","65534",
                state.toAbsolutePath().toString()).start();
        if (!group.waitFor(5,TimeUnit.SECONDS) || group.exitValue()!=0)
            throw new IllegalStateException("CI fixture group provision unavailable");
        Files.setPosixFilePermissions(state,PosixFilePermissions.fromString("rwxrwx---"));
        return new Scenario(worker,job,fixture,state,issuer,db,
                SyntheticOciSleepProfilePlan.containerName(job.jobId()));
    }

    @Test
    void postgresStopWhileWorkerHeldBeforeLaunchPreventsAnySyntheticChildren()
            throws Exception {
        Scenario s=scenario("race");
        boolean created=false;
        try {
            publish(s,1,"ALLOW",20);
            Files.writeString(s.state().resolve("prestart.hold"),"CI-HOLD\n");
            docker(InWorkerSignedLeaseOciPlan.build(
                    s.fixture(),s.state(),s.job().jobId(),s.worker(),1));
            created=true;
            awaitLog(s.name(),"SYNTHETIC_INWORKER_PRESTART_HELD");

            var stop=s.db().stopWorker(s.worker());
            assertThat(stop.epoch()).isEqualTo(2);
            publish(s,stop.epoch(),"STOP",20);
            awaitStopped(s.name(),60);
            String logs=docker(List.of("docker","logs",s.name()));
            assertThat(logs).contains("SYNTHETIC_PRESTART_STOP_DENIED")
                    .doesNotContain("SYNTHETIC_INWORKER_SIGNED_READY");
            assertThat(Files.exists(s.state().resolve("terminal.tombstone"))).isTrue();
            assertThat(Files.exists(s.state().resolve("launch.once"))).isFalse();
            assertThat(s.db().reservationVisible(s.job().jobId())).isFalse();

            docker(List.of("docker","rm","-f",s.name()));created=false;
            publish(s,1,"ALLOW",20);
            docker(InWorkerSignedLeaseOciPlan.build(
                    s.fixture(),s.state(),s.job().jobId(),s.worker(),1));
            created=true;
            awaitStopped(s.name(),58);
            assertThat(docker(List.of("docker","logs",s.name())))
                    .doesNotContain("SYNTHETIC_INWORKER_SIGNED_READY");
        } finally { if(created) remove(s.name()); }
    }

    @Test
    void deletingLeaseAfterLaunchStopsChildrenAndPermanentlyDeniesRestart()
            throws Exception {
        Scenario s=scenario("offline");
        boolean created=false;
        try {
            publish(s,1,"ALLOW",20);
            docker(InWorkerSignedLeaseOciPlan.build(
                    s.fixture(),s.state(),s.job().jobId(),s.worker(),1));
            created=true;
            awaitLog(s.name(),"SYNTHETIC_INWORKER_SIGNED_READY");
            assertThat(Files.exists(s.state().resolve("launch.once"))).isTrue();
            Files.delete(s.fixture().resolve("lease.txt"));
            awaitStopped(s.name(),56);
            assertThat(Files.exists(s.state().resolve("terminal.tombstone"))).isTrue();

            docker(List.of("docker","rm","-f",s.name()));created=false;
            publish(s,1,"ALLOW",20);
            docker(InWorkerSignedLeaseOciPlan.build(
                    s.fixture(),s.state(),s.job().jobId(),s.worker(),1));
            created=true;
            awaitStopped(s.name(),58);
            assertThat(docker(List.of("docker","logs",s.name())))
                    .doesNotContain("SYNTHETIC_INWORKER_SIGNED_READY");
        } finally { if(created) remove(s.name()); }
    }

    @Test
    void issuerSilenceAndExpiredLeaseTerminateWorkloadWithoutRemoteStop()
            throws Exception {
        Scenario s=scenario("expiry");
        boolean created=false;
        try {
            publish(s,1,"ALLOW",10);
            docker(InWorkerSignedLeaseOciPlan.build(
                    s.fixture(),s.state(),s.job().jobId(),s.worker(),1));
            created=true;
            awaitLog(s.name(),"SYNTHETIC_INWORKER_SIGNED_READY");
            // No lease refresh and no database STOP: expiration is the
            // worker-local fail-closed backstop for disconnected issuer.
            awaitStopped(s.name(),56);
            assertThat(Files.exists(s.state().resolve("terminal.tombstone"))).isTrue();
            assertThat(Files.exists(s.state().resolve("launch.once"))).isTrue();
        } finally { if(created) remove(s.name()); }
    }

    @Test
    void abruptHostKillCannotReactivateOldAllowAfterContainerRestart()
            throws Exception {
        Scenario s=scenario("hostcrash");
        boolean created=false;
        try {
            publish(s,1,"ALLOW",25);
            docker(InWorkerSignedLeaseOciPlan.build(
                    s.fixture(),s.state(),s.job().jobId(),s.worker(),1));
            created=true;
            awaitLog(s.name(),"SYNTHETIC_INWORKER_SIGNED_READY");
            assertThat(Files.exists(s.state().resolve("launch.once"))).isTrue();
            assertThat(Files.exists(s.state().resolve("terminal.tombstone"))).isFalse();
            // Test host simulates sudden container loss with no STOP delivery.
            docker(List.of("docker","kill","--signal=KILL",s.name()));
            awaitStopped(s.name(),137);
            docker(List.of("docker","rm","-f",s.name()));created=false;

            // The same synthetically signed, unexpired ALLOW is *not*
            // an authorization to launch again after this local crash.
            publish(s,1,"ALLOW",20);
            docker(InWorkerSignedLeaseOciPlan.build(
                    s.fixture(),s.state(),s.job().jobId(),s.worker(),1));
            created=true;
            awaitStopped(s.name(),58);
            assertThat(docker(List.of("docker","logs",s.name())))
                    .doesNotContain("SYNTHETIC_INWORKER_SIGNED_READY");
            assertThat(Files.exists(s.state().resolve("launch.once"))).isTrue();
            // DB may still hold an unrevoked reservation here: this proves
            // only fail-closed SAME-HOST restart, not distributed recovery.
        } finally { if(created) remove(s.name()); }
    }

    private static void publish(Scenario s,long epoch,String state,long ttl) throws Exception {
        long expiry=Instant.now().getEpochSecond()+ttl;
        String payload="AETHERIS45V1|"+s.worker()+"|"+s.job().jobId()+"|"
                +epoch+"|"+expiry+"|"+state+"|SLEEP\n";
        Signature signer=Signature.getInstance("Ed25519");
        signer.initSign(s.issuer().getPrivate());
        signer.update(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String content=payload+HexFormat.of().formatHex(signer.sign())+"\n";
        Path temp=s.fixture().resolve("lease.tmp");
        Files.writeString(temp,content);
        Files.setPosixFilePermissions(temp,PosixFilePermissions.fromString("rw-r--r--"));
        Files.move(temp,s.fixture().resolve("lease.txt"),
                StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }

    private static String docker(List<String> args) throws Exception {
        if (!"docker".equals(args.getFirst()))
            throw new SecurityException("CI_FIXED_DOCKER_ONLY");
        Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
        if (!p.waitFor(20,TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new AssertionError("CI Docker operation timed out");
        }
        String response=new String(p.getInputStream().readAllBytes()).trim();
        if(p.exitValue()!=0)
            throw new IllegalStateException("CI Docker operation failed: "+response);
        return response;
    }

    private static void awaitLog(String name,String marker) throws Exception {
        for(int i=0;i<100;i++) {
            if(docker(List.of("docker","logs",name)).contains(marker))return;
            Thread.sleep(100);
        }
        throw new AssertionError("Synthetic worker did not reach expected state: "+marker);
    }

    private static void awaitStopped(String name,int code) throws Exception {
        for(int i=0;i<170;i++) {
            String status=docker(List.of("docker","inspect","--format",
                    "{{.State.Running}}:{{.State.Pid}}:{{.State.ExitCode}}",name));
            if(status.equals("false:0:"+code)) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Synthetic OCI process did not exit with code "+code);
    }

    private static void remove(String name) {
        try {docker(List.of("docker","rm","-f",name));}
        catch(Exception ignored) {/* disposable CI cleanup */ }
    }
}
