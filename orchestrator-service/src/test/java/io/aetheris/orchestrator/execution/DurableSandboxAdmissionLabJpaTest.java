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
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Actual H2/JPA persistence with synthetic Ed25519 keys. No worker launched.
 * A new DurableSandboxAdmissionLab instance simulates loss of all JVM state.
 */
@SpringBootTest(properties = "aetheris.memory.retention-sweep-ms=3600000")
class DurableSandboxAdmissionLabJpaTest {
    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");
    private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

    @Autowired SandboxWorkerFenceRepository fences;
    @Autowired SandboxJobReservationRepository reservations;
    @Autowired PlatformTransactionManager transactions;

    private static final class Fixture {
        final KeyPair keyPair;
        final Map<UUID, TrustedContext> authorized = new HashMap<>();
        final String workerId = "ciWorker_" + UUID.randomUUID().toString().replace("-", "");
        final SandboxWorkerFenceRepository fences;
        final SandboxJobReservationRepository reservations;
        final PlatformTransactionManager transactions;

        Fixture(SandboxWorkerFenceRepository fences, SandboxJobReservationRepository reservations,
                PlatformTransactionManager transactions) throws Exception {
            this.fences = fences;
            this.reservations = reservations;
            this.transactions = transactions;
            keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        }

        DurableSandboxAdmissionLab freshInstance() {
            return new DurableSandboxAdmissionLab(fences, reservations, transactions,
                    keyPair.getPublic(), FIXED,
                    taskId -> authorized.get(taskId));
        }

        void armLabWorker() {
            var worker = new SandboxWorkerFenceEntity(workerId);
            worker.armSyntheticFixture();
            fences.saveAndFlush(worker);
        }

        JobClaims claims(String owner) {
            long now = NOW.getEpochSecond();
            return new JobClaims(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    owner, "projectSynthetic", workerId, "approvedFixture",
                    Profile.SYNTHETIC_SLEEP, now - 1, now + 20, 500, 128, 32);
        }

        SignedJob sign(JobClaims c) throws Exception {
            var signature = Signature.getInstance("Ed25519");
            signature.initSign(keyPair.getPrivate());
            signature.update(SignedSandboxJobGate.canonical(c));
            return new SignedJob(c, signature.sign());
        }

        SignedJob approved(JobClaims c) throws Exception {
            authorized.put(c.taskId(), new TrustedContext(c.taskId(), c.ownerId(),
                    c.projectId(), c.workerId(), c.approvalGrantId()));
            return sign(c);
        }
    }

    private Fixture fixture() throws Exception {
        return new Fixture(fences, reservations, transactions);
    }

    @Test
    void durableTombstoneRejectsReplayAfterJVMCoordinatorRestart() throws Exception {
        var f = fixture();
        f.armLabWorker();
        var c = f.claims("ownerA");
        var request = f.approved(c);

        assertThat(f.freshInstance().reserve(request)).isEqualTo(c.jobId());
        assertThat(f.freshInstance().reservationVisible(c.jobId())).isTrue();
        assertThatThrownBy(() -> f.freshInstance().reserve(request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("REPLAY");
        assertThat(reservations.findById(c.jobId())).isPresent();
        assertThat(reservations.findById(c.jobId()).orElseThrow().getNonce()).isEqualTo(c.nonce());
    }

    @Test
    void freshJobIdWithPreviouslyConsumedNonceIsDeniedPersistently() throws Exception {
        var f = fixture();
        f.armLabWorker();
        var a = f.claims("ownerA");
        f.freshInstance().reserve(f.approved(a));
        var b = f.claims("ownerA");
        b = new JobClaims(b.jobId(), b.taskId(), a.nonce(), b.ownerId(), b.projectId(),
                b.workerId(), b.approvalGrantId(), b.profile(), b.issuedAt(), b.expiresAt(),
                b.cpuMilli(), b.memoryMiB(), b.processLimit());
        var replay = f.approved(b);
        assertThatThrownBy(() -> f.freshInstance().reserve(replay))
                .isInstanceOf(SecurityException.class).hasMessageContaining("REPLAY");
        assertThat(reservations.findById(b.jobId())).isEmpty();
    }

    @Test
    void stopIsDurableIdempotentAndNeverClaimsOSProcessWasKilled() throws Exception {
        var f = fixture();
        f.armLabWorker();
        var a = f.claims("ownerA");
        var b = f.claims("ownerB");
        f.freshInstance().reserve(f.approved(a));
        f.freshInstance().reserve(f.approved(b));
        var stop = f.freshInstance().stopWorker(f.workerId);
        assertThat(stop.status()).isEqualTo("STOP_PERSISTED_OS_TERMINATION_UNVERIFIED");
        assertThat(stop.revokedRows()).isEqualTo(2);
        assertThat(stop.epoch()).isEqualTo(2L);
        assertThat(f.freshInstance().reservationVisible(a.jobId())).isFalse();
        assertThat(f.freshInstance().reservationVisible(b.jobId())).isFalse();
        assertThat(reservations.findById(a.jobId()).orElseThrow().isRevoked()).isTrue();
        assertThat(fences.findById(f.workerId).orElseThrow().isStopped()).isTrue();
        var repeated = f.freshInstance().stopWorker(f.workerId);
        assertThat(repeated.epoch()).isEqualTo(stop.epoch());
        assertThat(repeated.revokedRows()).isZero();
        assertThatThrownBy(() -> f.freshInstance().reserve(f.approved(f.claims("ownerA"))))
                .isInstanceOf(SecurityException.class).hasMessageContaining("STOP_LATCHED");
    }

    @Test
    void missingOrUnarmedWorkerFenceDeniesAllSignedJobs() throws Exception {
        var f = fixture();
        var request = f.approved(f.claims("ownerA"));
        assertThatThrownBy(() -> f.freshInstance().reserve(request))
                .isInstanceOf(SecurityException.class).hasMessageContaining("FENCE_MISSING");
        fences.saveAndFlush(new SandboxWorkerFenceEntity(f.workerId));
        assertThatThrownBy(() -> f.freshInstance().reserve(request))
                .isInstanceOf(SecurityException.class).hasMessageContaining("STOP_LATCHED");
        assertThat(reservations.findById(request.claims().jobId())).isEmpty();
    }

    @Test
    void unauthorisedOwnerOrForgedSignatureNeverPersistsRow() throws Exception {
        var f = fixture();
        f.armLabWorker();
        var c = f.claims("ownerA");
        var signed = f.approved(c);
        f.authorized.put(c.taskId(), new TrustedContext(c.taskId(),
                "ownerB", c.projectId(), c.workerId(), c.approvalGrantId()));
        assertThatThrownBy(() -> f.freshInstance().reserve(signed))
                .isInstanceOf(SecurityException.class).hasMessageContaining("OWNER_SCOPE_DENIED");
        f.authorized.put(c.taskId(), new TrustedContext(c.taskId(),
                c.ownerId(), c.projectId(), c.workerId(), c.approvalGrantId()));

        var outsider = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var sig = Signature.getInstance("Ed25519");
        sig.initSign(outsider.getPrivate());
        sig.update(SignedSandboxJobGate.canonical(c));
        var forged = new SignedJob(c, sig.sign());
        assertThatThrownBy(() -> f.freshInstance().reserve(forged))
                .isInstanceOf(SecurityException.class).hasMessageContaining("SIGNATURE");
        assertThat(reservations.findById(c.jobId())).isEmpty();
        assertThat(f.freshInstance().reserve(signed)).isEqualTo(c.jobId());
    }

    @Test
    void stoppingOneWorkerDoesNotStopDifferentOwnersSeparateWorker() throws Exception {
        var first = fixture();
        var second = fixture();
        first.armLabWorker();
        second.armLabWorker();
        var a = first.claims("ownerA");
        var b = second.claims("ownerB");
        first.freshInstance().reserve(first.approved(a));
        second.freshInstance().reserve(second.approved(b));
        first.freshInstance().stopWorker(first.workerId);
        assertThat(first.freshInstance().reservationVisible(a.jobId())).isFalse();
        assertThat(second.freshInstance().reservationVisible(b.jobId())).isTrue();
    }
}
