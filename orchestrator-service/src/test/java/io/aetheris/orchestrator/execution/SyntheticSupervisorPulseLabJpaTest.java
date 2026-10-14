package io.aetheris.orchestrator.execution;

import io.aetheris.orchestrator.execution.SignedSandboxJobGate.JobClaims;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.Profile;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.SignedJob;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.TrustedContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "aetheris.memory.retention-sweep-ms=3600000")
class SyntheticSupervisorPulseLabJpaTest {
    @Autowired SandboxWorkerFenceRepository fences;
    @Autowired SandboxJobReservationRepository reservations;
    @Autowired SyntheticSupervisorPulseRepository pulses;
    @Autowired PlatformTransactionManager transactions;

    private record Fixture(String worker, JobClaims job, DurableSandboxAdmissionLab db,
                           SyntheticSupervisorPulseLab issuer) {}
    private Fixture setup() throws Exception {
        String worker = "ci_" + UUID.randomUUID().toString().replace("-","");
        var fence = new SandboxWorkerFenceEntity(worker);
        fence.armSyntheticFixture();
        fences.saveAndFlush(fence);
        long now = Instant.now().getEpochSecond();
        var job = new JobClaims(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "syntheticOwner","syntheticProject",worker,"syntheticGrant",
                Profile.SYNTHETIC_SLEEP, now-1, now+40,500,128,32);
        KeyPair jobKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair heartbeatKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var trusted = new TrustedContext(job.taskId(), job.ownerId(),job.projectId(),
                worker,job.approvalGrantId());
        var db = new DurableSandboxAdmissionLab(fences,reservations,transactions,
                jobKey.getPublic(),Clock.systemUTC(),
                id -> id.equals(job.taskId())?trusted:null);
        var signature = Signature.getInstance("Ed25519");
        signature.initSign(jobKey.getPrivate());
        signature.update(SignedSandboxJobGate.canonical(job));
        assertThat(db.reserve(new SignedJob(job, signature.sign()))).isEqualTo(job.jobId());
        var issuer = new SyntheticSupervisorPulseLab(fences,reservations,pulses,
                transactions,Clock.systemUTC(),heartbeatKey.getPrivate());
        return new Fixture(worker,job,db,issuer);
    }

    @Test
    void persistedSequencesIncreaseAndSignedStopIsTerminal() throws Exception {
        var f=setup();
        String id="a".repeat(64);
        var first=f.issuer().publish(f.job().jobId(),f.worker(),id,1,"ACTIVE");
        var second=f.issuer().publish(f.job().jobId(),f.worker(),id,1,"ACTIVE");
        assertThat(first).contains("|1|1|");
        assertThat(second).contains("|1|2|");
        assertThat(pulses.findById(f.job().jobId()).orElseThrow().getSequence())
                .isEqualTo(2L);
        assertThatThrownBy(() -> f.issuer().publish(
                f.job().jobId(),f.worker(),id,2,"STOP"))
                .isInstanceOf(SecurityException.class);
        assertThat(f.db().stopWorker(f.worker()).epoch()).isEqualTo(2L);
        var stopped=f.issuer().publish(f.job().jobId(),f.worker(),id,2,"STOP");
        assertThat(stopped).contains("|2|3|").contains("|STOP\n");
        assertThat(pulses.findById(f.job().jobId()).orElseThrow().getState())
                .isEqualTo("STOP");
        assertThatThrownBy(() -> f.issuer().publish(
                f.job().jobId(),f.worker(),id,2,"STOP"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> f.issuer().publish(
                f.job().jobId(),f.worker(),id,1,"ACTIVE"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void crossWorkerOrStaleEpochAndMissingReservationFailClosed() throws Exception {
        var f=setup();
        String id="a".repeat(64);
        assertThatThrownBy(() -> f.issuer().publish(
                f.job().jobId(),"differentWorker",id,1,"ACTIVE"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> f.issuer().publish(
                f.job().jobId(),f.worker(),id,3,"ACTIVE"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> f.issuer().publish(
                UUID.randomUUID(),f.worker(),id,1,"ACTIVE"))
                .isInstanceOf(SecurityException.class);
        assertThat(pulses.findById(f.job().jobId())).isEmpty();
    }
}
