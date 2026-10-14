package io.aetheris.orchestrator.execution;

import io.aetheris.orchestrator.execution.SignedSandboxJobGate.JobClaims;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.Profile;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.SignedJob;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.TrustedContext;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignedSandboxJobGateTest {
    private static final Instant NOW = Instant.parse("2026-10-14T09:00:00Z");
    private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

    private static KeyPair keys() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static JobClaims claim(long issued, long expiration) {
        return new JobClaims(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "ownerA", "projectA", "isolatedWorkerA", "approvedGrantA",
                Profile.SYNTHETIC_SLEEP, issued, expiration, 500, 128, 32);
    }

    private static JobClaims valid() {
        long now = NOW.getEpochSecond();
        return claim(now - 1, now + 30);
    }

    private static TrustedContext trusted(JobClaims c) {
        return new TrustedContext(c.taskId(), c.ownerId(), c.projectId(),
                c.workerId(), c.approvalGrantId());
    }

    private static SignedJob sign(KeyPair signer, JobClaims c) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(signer.getPrivate());
        signature.update(SignedSandboxJobGate.canonical(c));
        return new SignedJob(c, signature.sign());
    }

    @Test
    void admitsOnlyValidSignedSingleUseOwnerBoundJob() throws Exception {
        var kp = keys();
        var gate = new SignedSandboxJobGate(kp.getPublic(), FIXED);
        var claims = valid();
        var request = sign(kp, claims);
        assertThat(gate.verifyAndAdmit(request, trusted(claims))).isEqualTo(claims.jobId());
        assertThat(gate.isAdmitted(claims.jobId())).isTrue();
        assertThatThrownBy(() -> gate.verifyAndAdmit(request, trusted(claims)))
                .isInstanceOf(SecurityException.class).hasMessageContaining("REPLAY");
        gate.complete(claims.jobId());
        assertThat(gate.isAdmitted(claims.jobId())).isFalse();
        assertThatThrownBy(() -> gate.verifyAndAdmit(request, trusted(claims)))
                .isInstanceOf(SecurityException.class).hasMessageContaining("REPLAY");
    }

    @Test
    void mismatchedOwnerProjectWorkerTaskOrApprovalAlwaysDenies() throws Exception {
        var kp = keys();
        var gate = new SignedSandboxJobGate(kp.getPublic(), FIXED);
        var claims = valid();
        var request = sign(kp, claims);
        var ctx = trusted(claims);
        var wrongContexts = new TrustedContext[]{
                new TrustedContext(ctx.taskId(), "ownerB", ctx.projectId(), ctx.workerId(), ctx.approvalGrantId()),
                new TrustedContext(ctx.taskId(), ctx.ownerId(), "projectB", ctx.workerId(), ctx.approvalGrantId()),
                new TrustedContext(ctx.taskId(), ctx.ownerId(), ctx.projectId(), "workerB", ctx.approvalGrantId()),
                new TrustedContext(UUID.randomUUID(), ctx.ownerId(), ctx.projectId(), ctx.workerId(), ctx.approvalGrantId()),
                new TrustedContext(ctx.taskId(), ctx.ownerId(), ctx.projectId(), ctx.workerId(), "missingApproval")
        };
        for (var wrong : wrongContexts) {
            assertThatThrownBy(() -> gate.verifyAndAdmit(request, wrong))
                    .isInstanceOf(SecurityException.class).hasMessageContaining("OWNER_SCOPE_DENIED");
        }
        assertThat(gate.verifyAndAdmit(request, ctx)).isEqualTo(claims.jobId());
    }

    @Test
    void tamperedClaimsWrongSignerAndBadSignatureNeverAdmit() throws Exception {
        var signer = keys();
        var outsider = keys();
        var gate = new SignedSandboxJobGate(signer.getPublic(), FIXED);
        var original = valid();
        var signed = sign(signer, original);
        var changed = new JobClaims(original.jobId(), original.taskId(), original.nonce(),
                original.ownerId(), original.projectId(), original.workerId(),
                original.approvalGrantId(), original.profile(), original.issuedAt(),
                original.expiresAt(), 500, 128, 32);
        // A changed owner without a new signature cannot pass even if a caller
        // falsely claims that owner to be trusted.
        var forged = new JobClaims(changed.jobId(), changed.taskId(), changed.nonce(),
                "ownerB", changed.projectId(), changed.workerId(), changed.approvalGrantId(),
                changed.profile(), changed.issuedAt(), changed.expiresAt(), 500, 128, 32);
        assertThatThrownBy(() -> gate.verifyAndAdmit(
                new SignedJob(forged, signed.signature()), trusted(forged)))
                .isInstanceOf(SecurityException.class).hasMessageContaining("SIGNATURE");
        assertThatThrownBy(() -> gate.verifyAndAdmit(sign(outsider, original), trusted(original)))
                .isInstanceOf(SecurityException.class).hasMessageContaining("SIGNATURE");
        assertThatThrownBy(() -> gate.verifyAndAdmit(
                new SignedJob(original, new byte[64]), trusted(original)))
                .isInstanceOf(SecurityException.class).hasMessageContaining("SIGNATURE");
        assertThat(gate.isAdmitted(original.jobId())).isFalse();
    }

    @Test
    void expiredFutureOrOverlongLeaseDenied() throws Exception {
        var signer = keys();
        var gate = new SignedSandboxJobGate(signer.getPublic(), FIXED);
        long now = NOW.getEpochSecond();
        for (var claims : new JobClaims[]{
                claim(now - 120, now - 1), claim(now + 1, now + 30),
                claim(now - 100, now + 2)
        }) {
            // A >60 second lease fails already when constructing the signed
            // transcript, before being passed to admission.
            if (claims.expiresAt() - claims.issuedAt() > 60) {
                assertThatThrownBy(() -> sign(signer, claims))
                        .isInstanceOf(IllegalArgumentException.class);
            } else {
                var signed = sign(signer, claims);
                assertThatThrownBy(() -> gate.verifyAndAdmit(signed, trusted(claims)))
                        .isInstanceOf(SecurityException.class).hasMessageContaining("LEASE");
            }
        }
    }

    @Test
    void stopRevokesAllActiveAdmissionsAndCannotBeReleased() throws Exception {
        var signer = keys();
        var gate = new SignedSandboxJobGate(signer.getPublic(), FIXED);
        var a = valid();
        var b = valid();
        gate.verifyAndAdmit(sign(signer, a), trusted(a));
        gate.verifyAndAdmit(sign(signer, b), trusted(b));
        gate.engageStop();
        assertThat(gate.isStopped()).isTrue();
        assertThat(gate.isAdmitted(a.jobId())).isFalse();
        assertThat(gate.isAdmitted(b.jobId())).isFalse();
        assertThatThrownBy(() -> gate.verifyAndAdmit(sign(signer, valid()), trusted(a)))
                .isInstanceOf(SecurityException.class).hasMessageContaining("STOP");
        gate.engageStop();
        assertThat(gate.isStopped()).isTrue();
    }

    @Test
    void rejectUnboundedOrUserSelectedProfileAndNeverAllowArguments() {
        var claims = valid();
        assertThatThrownBy(() -> SignedSandboxJobGate.canonical(new JobClaims(
                claims.jobId(), claims.taskId(), claims.nonce(),
                claims.ownerId(), claims.projectId(), claims.workerId(),
                claims.approvalGrantId(), claims.profile(), claims.issuedAt(),
                claims.expiresAt(), 2000, 128, 32)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SignedSandboxJobGate.canonical(new JobClaims(
                claims.jobId(), claims.taskId(), claims.nonce(),
                "owner with spaces", claims.projectId(), claims.workerId(),
                claims.approvalGrantId(), claims.profile(), claims.issuedAt(),
                claims.expiresAt(), 500, 128, 32)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void signatureArrayIsDefensivelyCopied() throws Exception {
        var kp = keys();
        var c = valid();
        byte[] source = sign(kp, c).signature();
        var wrapped = new SignedJob(c, source);
        source[0] ^= 127;
        byte[] exported = wrapped.signature();
        exported[1] ^= 127;
        var gate = new SignedSandboxJobGate(kp.getPublic(), FIXED);
        assertThat(gate.verifyAndAdmit(wrapped, trusted(c))).isEqualTo(c.jobId());
    }
}
