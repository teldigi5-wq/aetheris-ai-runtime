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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "aetheris.memory.retention-sweep-ms=3600000")
class SyntheticHostStopFenceLabJpaTest {
    @Autowired SandboxWorkerFenceRepository fences;
    @Autowired SandboxJobReservationRepository reservations;
    @Autowired PlatformTransactionManager transactions;

    private record Fixture(String workerId, JobClaims claims,
                           DurableSandboxAdmissionLab admission) {}

    private Fixture fixture(long expiresInSeconds) throws Exception {
        String worker = "ci_" + UUID.randomUUID().toString().replace("-", "");
        var fence = new SandboxWorkerFenceEntity(worker);
        fence.armSyntheticFixture();
        fences.saveAndFlush(fence);
        long now = Instant.now().getEpochSecond();
        var claims = new JobClaims(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ciOwner", "ciProject", worker, "ciGrant",
                Profile.SYNTHETIC_SLEEP, now - 1, now + expiresInSeconds,
                500, 128, 32);
        KeyPair key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var scope = new TrustedContext(claims.taskId(), claims.ownerId(),
                claims.projectId(), worker, claims.approvalGrantId());
        var admission = new DurableSandboxAdmissionLab(fences, reservations,
                transactions, key.getPublic(), Clock.systemUTC(),
                task -> task.equals(claims.taskId()) ? scope : null);
        var signer = Signature.getInstance("Ed25519");
        signer.initSign(key.getPrivate());
        signer.update(SignedSandboxJobGate.canonical(claims));
        assertThat(admission.reserve(new SignedJob(claims, signer.sign())))
                .isEqualTo(claims.jobId());
        return new Fixture(worker, claims, admission);
    }

    private SyntheticHostStopFenceLab supervisor() {
        return new SyntheticHostStopFenceLab(fences, reservations, transactions,
                Clock.systemUTC());
    }

    @Test
    void independentSupervisorAllowsOnlyLiveSyntheticSnapshot() throws Exception {
        var fixture = fixture(25);
        var result = supervisor().evaluate(fixture.claims().jobId(), fixture.workerId(), 1L);
        assertThat(result.terminate()).isFalse();
        assertThat(result.reason()).isEqualTo("ACTIVE_SNAPSHOT_NOT_EXECUTION_AUTH");
        assertThat(result.observedEpoch()).isEqualTo(1L);
    }

    @Test
    void committedStopAndRevokedReservationDemandKillOnRestart() throws Exception {
        var fixture = fixture(25);
        assertThat(supervisor().evaluate(fixture.claims().jobId(),
                fixture.workerId(), 1L).terminate()).isFalse();
        fixture.admission().stopWorker(fixture.workerId());
        var fresh = supervisor().evaluate(fixture.claims().jobId(), fixture.workerId(), 1L);
        assertThat(fresh.terminate()).isTrue();
        assertThat(fresh.reason()).isEqualTo("DATABASE_STOP_LATCHED");
        assertThat(fresh.observedEpoch()).isEqualTo(2L);
        assertThat(reservations.findById(fixture.claims().jobId())
                .orElseThrow().isRevoked()).isTrue();
    }

    @Test
    void staleEpochWrongWorkerAndMissingJobFailClosed() throws Exception {
        var fixture = fixture(25);
        assertThat(supervisor().evaluate(fixture.claims().jobId(),
                fixture.workerId(), 2L).reason()).isEqualTo("STALE_STOP_EPOCH");
        var another = fixture(25);
        assertThat(supervisor().evaluate(fixture.claims().jobId(),
                another.workerId(), 1L).reason())
                .isEqualTo("RESERVATION_SCOPE_OR_EPOCH_MISMATCH");
        assertThat(supervisor().evaluate(UUID.randomUUID(),
                fixture.workerId(), 1L).reason()).isEqualTo("MISSING_JOB_RESERVATION");
        assertThat(supervisor().evaluate(fixture.claims().jobId(),
                "unregistered_ci", 1L).reason()).isEqualTo("MISSING_WORKER_FENCE");
    }

    @Test
    void expiredReservationDeniesWorkerEvenIfFenceNotStopped() throws Exception {
        var fixture = fixture(3);
        Clock future = Clock.offset(Clock.systemUTC(), java.time.Duration.ofSeconds(10));
        var futureSupervisor = new SyntheticHostStopFenceLab(fences, reservations,
                transactions, future);
        var result = futureSupervisor.evaluate(fixture.claims().jobId(), fixture.workerId(), 1L);
        assertThat(result.terminate()).isTrue();
        assertThat(result.reason()).isEqualTo("RESERVATION_EXPIRED");
        assertThat(fences.findById(fixture.workerId()).orElseThrow().isStopped()).isFalse();
    }

    @Test
    void backendUnavailableNeverGrantsContinuedWork() throws Exception {
        var fixture = fixture(25);
        var failed = mock(SandboxWorkerFenceRepository.class);
        when(failed.findById(fixture.workerId()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException(
                        "synthetic disconnected PostgreSQL"));
        var supervisor = new SyntheticHostStopFenceLab(failed, reservations,
                transactions, Clock.systemUTC());
        var decision = supervisor.evaluate(fixture.claims().jobId(), fixture.workerId(), 1L);
        assertThat(decision.terminate()).isTrue();
        assertThat(decision.reason()).isEqualTo("DATABASE_UNAVAILABLE_FAIL_CLOSED");
    }
}
