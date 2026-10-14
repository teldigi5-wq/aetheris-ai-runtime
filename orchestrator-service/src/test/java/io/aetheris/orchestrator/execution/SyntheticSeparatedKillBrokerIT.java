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
 * CI-only OS identity separation: UID 65534 never receives Docker socket,
 * daemon credentials, OS privileges or any executable arguments. The
 * standard hosted-runner identity holds the privileged fixed-target broker.
 */
@SpringBootTest(properties = "aetheris.memory.retention-sweep-ms=3600000")
@EnabledOnOs(OS.LINUX)
class SyntheticSeparatedKillBrokerIT {
    @TempDir Path root;
    @Autowired SandboxWorkerFenceRepository fences;
    @Autowired SandboxJobReservationRepository reservations;
    @Autowired SyntheticSupervisorPulseRepository pulses;
    @Autowired PlatformTransactionManager transactions;

    private record Fixture(String worker, JobClaims job, String container,
                           Path publisherPublic, Path verifierPrivate,
                           Path brokerPublic, Path pulseFile, Path journalDir,
                           Path socket, DurableSandboxAdmissionLab db,
                           SyntheticSupervisorPulseLab issuer) {}

    private Fixture setup(String name) throws Exception {
        if (!"true".equals(System.getenv("GITHUB_ACTIONS"))
                || !"true".equals(System.getenv("AETHERIS_STAGE45_BROKER_CI")))
            throw new SecurityException("SEPARATE_BROKER_SYNTHETIC_CI_ONLY");
        Files.setPosixFilePermissions(root,PosixFilePermissions.fromString("rwxr-xr-x"));
        String worker="ci_"+UUID.randomUUID().toString().replace("-","");
        var fence=new SandboxWorkerFenceEntity(worker);
        fence.armSyntheticFixture();
        fences.saveAndFlush(fence);
        long now=Instant.now().getEpochSecond();
        var job=new JobClaims(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                "syntheticOwner","syntheticProject",worker,"syntheticGrant",
                Profile.SYNTHETIC_SLEEP,now-1,now+40,500,128,32);
        var jobSigner=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var pulseSigner=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var verifierSigner=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var trusted=new TrustedContext(job.taskId(),job.ownerId(),job.projectId(),
                worker,job.approvalGrantId());
        var db=new DurableSandboxAdmissionLab(fences,reservations,transactions,
                jobSigner.getPublic(),Clock.systemUTC(),
                id->id.equals(job.taskId())?trusted:null);
        var signature=Signature.getInstance("Ed25519");
        signature.initSign(jobSigner.getPrivate());
        signature.update(SignedSandboxJobGate.canonical(job));
        assertThat(db.reserve(new SignedJob(job,signature.sign()))).isEqualTo(job.jobId());

        Path fixture=Files.createDirectory(root.resolve("oci-"+name));
        Files.setPosixFilePermissions(fixture,PosixFilePermissions.fromString("rwxr-xr-x"));
        Files.writeString(fixture.resolve("sleep-fixture.sh"),"""
                #!/bin/sh
                set -eu
                test "$(id -u)" = "65534"
                test ! -e /var/run/docker.sock
                sleep 300 &
                sh -c 'sleep 300' &
                echo SYNTHETIC_SEPARATE_BROKER_WORKER_READY
                wait
                """);
        Files.setPosixFilePermissions(fixture.resolve("sleep-fixture.sh"),
                PosixFilePermissions.fromString("rw-r--r--"));
        String container=docker(SyntheticOciSleepProfilePlan.build(fixture,job.jobId()));
        assertThat(container).matches("[0-9a-f]{64}");
        for(int i=0;i<70;i++) {
            if(docker(List.of("docker","logs",container))
                    .contains("SYNTHETIC_SEPARATE_BROKER_WORKER_READY"))break;
            if(i==69)throw new AssertionError("Synthetic fixed OCI did not start");
            Thread.sleep(70);
        }

        Path pub=Files.write(root.resolve(name+"-issuer.der"),
                pulseSigner.getPublic().getEncoded());
        Files.setPosixFilePermissions(pub,PosixFilePermissions.fromString("rw-r--r--"));
        Path verifierPrivate=Files.write(root.resolve(name+"-verifier.p8"),
                verifierSigner.getPrivate().getEncoded());
        Files.setPosixFilePermissions(verifierPrivate,PosixFilePermissions.fromString("rw-------"));
        privileged(List.of("sudo","-n","chown","65534:65534",verifierPrivate.toString()));

        Path brokerPub=Files.write(root.resolve(name+"-broker.der"),
                verifierSigner.getPublic().getEncoded());
        Files.setPosixFilePermissions(brokerPub,PosixFilePermissions.fromString("rw-------"));
        Path journal=Files.createDirectory(root.resolve(name+"-journal"));
        // Configure mode WHILE owned by the CI runner, then hand the
        // private journal to UID 65534. The unprivileged verifier must
        // not need runner/root privileges to write its own history.
        Files.setPosixFilePermissions(journal,PosixFilePermissions.fromString("rwx------"));
        privileged(List.of("sudo","-n","chown","65534:65534",journal.toString()));

        var issuer=new SyntheticSupervisorPulseLab(fences,reservations,pulses,
                transactions,Clock.systemUTC(),pulseSigner.getPrivate());
        return new Fixture(worker,job,container,pub,verifierPrivate,brokerPub,
                root.resolve(name+"-pulse.txt"),journal,root.resolve(name+".sock"),db,issuer);
    }

    @Test
    void signedDbStopThroughUnprivilegedVerifierKillsOnlyPinnedContainer()
            throws Exception {
        runScenario("signed",true);
    }

    @Test
    void lostPublisherStillCausesAuthenticatedFailClosedBrokerKill()
            throws Exception {
        runScenario("silent",false);
    }

    private void runScenario(String name,boolean sendStop) throws Exception {
        Fixture f=setup(name);
        Process broker=null,verifier=null;
        try {
            publish(f,"ACTIVE",1);
            broker=startBroker(f);
            waitSocketAndLimitToVerifier(f.socket());
            verifier=startVerifier(f);
            Thread.sleep(180);
            if(sendStop) {
                assertThat(f.db().stopWorker(f.worker()).epoch()).isEqualTo(2L);
                publish(f,"STOP",2);
            }
            assertThat(verifier.waitFor(9,TimeUnit.SECONDS)).isTrue();
            assertThat(broker.waitFor(10,TimeUnit.SECONDS)).isTrue();
            String verifierLogs=new String(verifier.getInputStream().readAllBytes());
            String brokerLogs=new String(broker.getInputStream().readAllBytes());
            assertThat(verifier.exitValue())
                    .as("unprivileged verifier output: "+verifierLogs).isZero();
            assertThat(broker.exitValue())
                    .as("fixed broker output: "+brokerLogs).isZero();
            assertThat(verifierLogs).contains(sendStop
                    ? "SYNTHETIC_UNPRIVILEGED_STOP_REQUESTED=SIGNED_STOP"
                    : "SYNTHETIC_UNPRIVILEGED_STOP_REQUESTED=LEASE_INVALID");
            assertThat(brokerLogs).contains(
                    "SYNTHETIC_BROKER_KILL_REASON=AUTHENTICATED_UNPRIVILEGED_STOP",
                    "SYNTHETIC_BROKER_TERMINATION_OBSERVED");
            assertThat(docker(List.of("docker","inspect","--format",
                    "{{.State.Running}}:{{.State.Pid}}:{{.State.ExitCode}}",
                    f.container()))).isEqualTo("false:0:137");
            if(sendStop) {
                assertThat(fences.findById(f.worker()).orElseThrow().isStopped()).isTrue();
                assertThat(reservations.findById(f.job().jobId())
                        .orElseThrow().isRevoked()).isTrue();
            } else {
                assertThat(fences.findById(f.worker()).orElseThrow().isStopped()).isFalse();
                assertThat(reservations.findById(f.job().jobId())
                        .orElseThrow().isRevoked()).isFalse();
            }
        } finally {
            if(verifier!=null && verifier.isAlive())verifier.destroyForcibly();
            if(broker!=null && broker.isAlive())broker.destroyForcibly();
            try {docker(List.of("docker","rm","-f",f.container()));}
            catch(Exception ignored){/* disposable CI teardown */}
            // Restore directory OWNERSHIP after both detached OS processes
            // have ended, so JUnit can clean up private nonroot state.
            // Never expose the verifier journal to the runtime or agent.
            privileged(List.of("sudo","-n","chown",
                    System.getProperty("user.name"),f.journalDir().toString()));
        }
    }

    private static void publish(Fixture f,String state,long epoch) throws Exception {
        String signed=f.issuer().publish(f.job().jobId(),f.worker(),
                f.container(),epoch,state);
        Path staging=f.pulseFile().resolveSibling(f.pulseFile().getFileName()+".tmp");
        Files.writeString(staging,signed);
        Files.setPosixFilePermissions(staging,PosixFilePermissions.fromString("rw-r--r--"));
        Files.move(staging,f.pulseFile(),StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static Process startBroker(Fixture f) throws Exception {
        Path binary=Path.of(System.getProperty("aetheris.stage45.broker.bin",""));
        if(!Files.isRegularFile(binary))throw new SecurityException("BROKER_BINARY_MISSING");
        var builder=new ProcessBuilder(binary.toString(),f.socket().toString(),
                f.brokerPublic().toString(),f.worker(),f.job().jobId().toString(),
                f.container(),"1","9").redirectErrorStream(true);
        builder.environment().put("AETHERIS_STAGE45_BROKER_CI","true");
        return builder.start();
    }

    private static Process startVerifier(Fixture f) throws Exception {
        Path binary=Path.of(System.getProperty("aetheris.stage45.verifier.bin",""));
        if(!Files.isRegularFile(binary))throw new SecurityException("VERIFIER_BINARY_MISSING");
        // The GitHub Actions RUNNER_TEMP directory can be private to the
        // runner UID. Copy only the fixed CI verifier executable into
        // the traversable, isolated JUnit fixture before dropping UID.
        Path accessible=f.publisherPublic().resolveSibling(
                f.job().jobId()+"-fixed-verifier");
        Files.copy(binary,accessible);
        Files.setPosixFilePermissions(accessible,
                PosixFilePermissions.fromString("rwxr-xr-x"));
        return new ProcessBuilder("sudo","-n","-u","nobody","env",
                "GITHUB_ACTIONS=true","AETHERIS_STAGE45_BROKER_CI=true",
                accessible.toString(),f.publisherPublic().toString(),f.pulseFile().toString(),
                f.verifierPrivate().toString(),f.socket().toString(),
                f.worker(),f.job().jobId().toString(),f.container(),"1",
                f.journalDir().toString()).redirectErrorStream(true).start();
    }

    private static void waitSocketAndLimitToVerifier(Path socket) throws Exception {
        for(int i=0;i<60;i++) {
            if(Files.exists(socket)) {
                privileged(List.of("sudo","-n","chgrp","65534",socket.toString()));
                Files.setPosixFilePermissions(socket,PosixFilePermissions.fromString("rw-rw----"));
                return;
            }
            Thread.sleep(40);
        }
        throw new AssertionError("Fixed OCI kill broker did not bind private socket");
    }

    private static void privileged(List<String> args) throws Exception {
        var p=new ProcessBuilder(args).redirectErrorStream(true).start();
        if(!p.waitFor(8,TimeUnit.SECONDS) || p.exitValue()!=0)
            throw new IllegalStateException("Disposable CI user isolation fixture failed");
    }

    private static String docker(List<String> argv) throws Exception {
        if(!"docker".equals(argv.getFirst()))throw new SecurityException("CI_FIXED_DOCKER_ONLY");
        var p=new ProcessBuilder(argv).redirectErrorStream(true).start();
        if(!p.waitFor(20,TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("Docker timeout");}
        String output=new String(p.getInputStream().readAllBytes()).trim();
        if(p.exitValue()!=0)throw new IllegalStateException("Docker synthetic failure: "+output);
        return output;
    }
}
