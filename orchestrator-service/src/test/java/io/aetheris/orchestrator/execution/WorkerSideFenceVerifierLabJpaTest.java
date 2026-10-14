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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "aetheris.memory.retention-sweep-ms=3600000")
class WorkerSideFenceVerifierLabJpaTest {
    @Autowired SandboxWorkerFenceRepository fences;
    @Autowired SandboxJobReservationRepository reservations;
    @Autowired PlatformTransactionManager transactions;

    private static final Clock CLOCK = Clock.systemUTC();

    private final class Fixture {
        final String workerId = "ci_" + UUID.randomUUID().toString().replace("-", "");
        final KeyPair signer;
        final Map<UUID, TrustedContext> authority = new HashMap<>();

        Fixture() throws Exception {
            signer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            var fence = new SandboxWorkerFenceEntity(workerId);
            fence.armSyntheticFixture();
            fences.saveAndFlush(fence);
        }

        JobClaims claim(String owner) {
            long now = Instant.now(CLOCK).getEpochSecond();
            return new JobClaims(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    owner, "projectA", workerId, "grantA", Profile.SYNTHETIC_SLEEP,
                    now - 1, now + 30, 500, 128, 32);
        }

        SignedJob sign(JobClaims c) throws Exception {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(signer.getPrivate());
            signature.update(SignedSandboxJobGate.canonical(c));
            return new SignedJob(c, signature.sign());
        }

        SignedJob reserve(JobClaims c) throws Exception {
            authority.put(c.taskId(), new TrustedContext(c.taskId(), c.ownerId(),
                    c.projectId(), c.workerId(), c.approvalGrantId()));
            SignedJob s = sign(c);
            var controller = new DurableSandboxAdmissionLab(
                    fences, reservations, transactions, signer.getPublic(), CLOCK,
                    authority::get);
            assertThat(controller.reserve(s)).isEqualTo(c.jobId());
            return s;
        }

        WorkerSideFenceVerifierLab worker() {
            return new WorkerSideFenceVerifierLab(
                    fences, reservations, transactions, signer.getPublic(), CLOCK,
                    authority::get);
        }
    }

    @Test
    void separatelyInstantiatedVerifierConfirmsOnlyPersistedSignedAndScopedGrant()
            throws Exception {
        var f = new Fixture();
        var c = f.claim("ownerA");
        var signed = f.reserve(c);
        var result = f.worker().check(signed);
        assertThat(result.status()).isEqualTo("LAB_ONLY_CHECK_NOT_LAUNCH_AUTHORITY");
        assertThat(result.jobId()).isEqualTo(c.jobId());
        assertThat(result.workerId()).isEqualTo(f.workerId);
        assertThat(result.epoch()).isEqualTo(1L);
    }

    @Test
    void crossOwnerProjectAndApprovalChangesFailDespiteValidSignature() throws Exception {
        var f = new Fixture();
        var c = f.claim("ownerA");
        var original = f.reserve(c);
        f.authority.put(c.taskId(), new TrustedContext(c.taskId(),
                "ownerB", c.projectId(), c.workerId(), c.approvalGrantId()));
        assertThatThrownBy(() -> f.worker().check(original))
                .isInstanceOf(SecurityException.class).hasMessageContaining("APPROVAL");

        f.authority.put(c.taskId(), new TrustedContext(c.taskId(),
                c.ownerId(), "differentProject", c.workerId(), c.approvalGrantId()));
        assertThatThrownBy(() -> f.worker().check(original))
                .isInstanceOf(SecurityException.class).hasMessageContaining("APPROVAL");

        f.authority.put(c.taskId(), new TrustedContext(c.taskId(),
                c.ownerId(), c.projectId(), c.workerId(), "revokedGrant"));
        assertThatThrownBy(() -> f.worker().check(original))
                .isInstanceOf(SecurityException.class).hasMessageContaining("APPROVAL");
        assertThat(reservations.findById(c.jobId())).isPresent();
    }

    @Test
    void workerChecksDbReservationNotOnlyAValidSignature() throws Exception {
        var f = new Fixture();
        var reserved = f.claim("ownerA");
        var original = f.reserve(reserved);
        var replacement = new JobClaims(reserved.jobId(), reserved.taskId(),
                UUID.randomUUID(), reserved.ownerId(), reserved.projectId(),
                reserved.workerId(), reserved.approvalGrantId(), reserved.profile(),
                reserved.issuedAt(), reserved.expiresAt(), 500, 128, 32);
        SignedJob signedDifferentNonce = f.sign(replacement);
        assertThatThrownBy(() -> f.worker().check(signedDifferentNonce))
                .isInstanceOf(SecurityException.class).hasMessageContaining("FENCE_OR_SCOPE");
        assertThat(f.worker().check(original).jobId()).isEqualTo(reserved.jobId());
    }

    @Test
    void stopAndDurableRevocationInvalidateFreshWorkerChecks() throws Exception {
        var f = new Fixture();
        var c = f.claim("ownerA");
        var signed = f.reserve(c);
        var workerBefore = f.worker();
        assertThat(workerBefore.check(signed).epoch()).isEqualTo(1L);
        var controller = new DurableSandboxAdmissionLab(
                fences, reservations, transactions, f.signer.getPublic(), CLOCK,
                f.authority::get);
        var stop = controller.stopWorker(f.workerId);
        assertThat(stop.epoch()).isEqualTo(2);
        assertThatThrownBy(() -> f.worker().check(signed))
                .isInstanceOf(SecurityException.class).hasMessageContaining("STOP");
        assertThat(reservations.findById(c.jobId()).orElseThrow().isRevoked()).isTrue();
    }

    @Test
    void fakeSignerAndMissingReservationCanNeverProduceWorkerCheckResult()
            throws Exception {
        var f = new Fixture();
        var c = f.claim("ownerA");
        f.authority.put(c.taskId(), new TrustedContext(c.taskId(), c.ownerId(),
                c.projectId(), c.workerId(), c.approvalGrantId()));
        assertThatThrownBy(() -> f.worker().check(f.sign(c)))
                .isInstanceOf(SecurityException.class).hasMessageContaining("RESERVATION_MISSING");
        var valid = f.reserve(f.claim("ownerA"));
        var outsider = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var signature = Signature.getInstance("Ed25519");
        signature.initSign(outsider.getPrivate());
        signature.update(SignedSandboxJobGate.canonical(valid.claims()));
        assertThatThrownBy(() -> f.worker().check(
                new SignedJob(valid.claims(), signature.sign())))
                .isInstanceOf(SecurityException.class).hasMessageContaining("BAD_JOB_SIGNATURE");
    }
}
