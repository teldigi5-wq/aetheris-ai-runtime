package io.aetheris.orchestrator.organization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real JDK Ed25519 verification. Signing keys are ephemeral test fixtures,
 * never real owner keys. A valid signature NEVER equals verified owner access.
 */
class EnterpriseOwnerProofPreflightTest {
    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private KeyPair signer;
    private EnterpriseOwnerProofPreflight.ReviewScope stored;

    @BeforeEach
    void setup() throws Exception {
        signer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        stored = new EnterpriseOwnerProofPreflight.ReviewScope(
                "00000000-0000-4000-8000-000000000001",
                "cinematic-laptop", "aetheris-immersive",
                "scroll-experience-engineering",
                "a".repeat(64), "b".repeat(64), "c".repeat(64),
                NOW.minusSeconds(60), NOW.plusSeconds(840));
    }

    private EnterpriseOwnerProofPreflight.SignedReviewProof sign(
            EnterpriseOwnerProofPreflight.ReviewScope scope,
            String purpose, String nonce, Instant issued, Instant expiry,
            PrivateKey key) throws Exception {
        var unsigned = new EnterpriseOwnerProofPreflight.SignedReviewProof(
                scope, purpose, nonce, issued, expiry, "A".repeat(86));
        Signature ed = Signature.getInstance("Ed25519");
        ed.initSign(key);
        ed.update(EnterpriseOwnerProofPreflight.payload(unsigned));
        return new EnterpriseOwnerProofPreflight.SignedReviewProof(
                scope, purpose, nonce, issued, expiry,
                Base64.getUrlEncoder().withoutPadding().encodeToString(ed.sign()));
    }

    private EnterpriseOwnerProofPreflight.SignedReviewProof proof() throws Exception {
        return sign(stored, "REVIEW_STATIC_DESIGN_ONLY", "d".repeat(32),
                NOW.minusSeconds(30), NOW.plusSeconds(120), signer.getPrivate());
    }

    private EnterpriseOwnerProofPreflight.Inspection inspect(
            EnterpriseOwnerProofPreflight.SignedReviewProof proof) {
        return EnterpriseOwnerProofPreflight.inspect(proof, stored, signer.getPublic(), CLOCK);
    }

    @Test
    void authenticEd25519SignatureProvesBytesOnlyNeverOwnerOrActionAuthority() throws Exception {
        var r = inspect(proof());
        assertThat(r.cryptographicSignatureValid()).isTrue();
        assertThat(r.evidenceState())
                .isEqualTo("SIGNATURE_VALID_OWNER_NOT_ATTESTED_REPLAY_NOT_TRACKED");
        assertThat(r.actualOwnerAuthenticated()).isFalse();
        assertThat(r.approvalGranted()).isFalse();
        assertThat(r.nonceConsumed()).isFalse();
        assertThat(r.executionReleased()).isFalse();
        assertThat(r.replayProtectionCertified()).isFalse();
    }

    @Test
    void forgedProofFromDifferentPrivateKeyRejected() throws Exception {
        KeyPair rogue = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var forged = sign(stored, "REVIEW_STATIC_DESIGN_ONLY", "e".repeat(32),
                NOW.minusSeconds(10), NOW.plusSeconds(60), rogue.getPrivate());
        assertThatThrownBy(() -> inspect(forged))
                .hasMessage("E22B_PROOF_SIGNATURE_INVALID");
    }

    @Test
    void changedManifestDigestCannotReuseCorrectSignature() throws Exception {
        var valid = proof();
        var changed = new EnterpriseOwnerProofPreflight.ReviewScope(
                stored.intentId(), stored.projectId(), stored.companyId(), stored.divisionId(),
                stored.briefSha256(), stored.storyboardSha256(), "f".repeat(64),
                stored.intentIssuedAt(), stored.intentExpiresAt());
        var forged = new EnterpriseOwnerProofPreflight.SignedReviewProof(
                changed, valid.purpose(), valid.nonceHex(),
                valid.issuedAt(), valid.expiresAt(), valid.signatureBase64Url());
        assertThatThrownBy(() -> inspect(forged))
                .hasMessage("E22B_PROOF_SCOPE_MISMATCH");
    }

    @Test
    void replayToDifferentProjectAndCompanyRefusedEvenIfSigned() throws Exception {
        var different = new EnterpriseOwnerProofPreflight.ReviewScope(
                stored.intentId(), "wrong-project", "aetheris-finance",
                "risk-portfolio-analytics", stored.briefSha256(), stored.storyboardSha256(),
                stored.manifestSha256(), stored.intentIssuedAt(), stored.intentExpiresAt());
        var signature = sign(different, "REVIEW_STATIC_DESIGN_ONLY", "d".repeat(32),
                NOW.minusSeconds(30), NOW.plusSeconds(120), signer.getPrivate());
        assertThatThrownBy(() -> inspect(signature))
                .hasMessage("E22B_PROOF_SCOPE_MISMATCH");
    }

    @Test
    void rejectsExpiredProofEvenWithValidSignature() throws Exception {
        var old = sign(stored, "REVIEW_STATIC_DESIGN_ONLY", "d".repeat(32),
                NOW.minusSeconds(50), NOW.minusSeconds(1), signer.getPrivate());
        assertThatThrownBy(() -> inspect(old))
                .hasMessage("E22B_PROOF_EXPIRED_OR_OUTSIDE_INTENT");
    }

    @Test
    void rejectsProofFromFutureEvenWithValidSignature() throws Exception {
        var future = sign(stored, "REVIEW_STATIC_DESIGN_ONLY", "d".repeat(32),
                NOW.plusSeconds(2), NOW.plusSeconds(40), signer.getPrivate());
        assertThatThrownBy(() -> inspect(future))
                .hasMessage("E22B_PROOF_EXPIRED_OR_OUTSIDE_INTENT");
    }

    @Test
    void rejectsOverFiveMinuteProofWindow() throws Exception {
        var overlong = sign(stored, "REVIEW_STATIC_DESIGN_ONLY", "d".repeat(32),
                NOW.minusSeconds(15), NOW.plusSeconds(286), signer.getPrivate());
        assertThatThrownBy(() -> inspect(overlong))
                .hasMessage("E22B_PROOF_EXPIRED_OR_OUTSIDE_INTENT");
    }

    @Test
    void refusesProductionPublishingAsWrongPurposeEvenIfProperlySigned() throws Exception {
        assertThatThrownBy(() -> sign(stored, "PUBLISH_PRODUCTION", "d".repeat(32),
                NOW.minusSeconds(30), NOW.plusSeconds(120), signer.getPrivate()))
                .hasMessage("E22B_PROOF_FORMAT_INVALID");
    }

    @Test
    void rejectsUnpinnedKeyAndWrongCurve() throws Exception {
        var p = proof();
        assertThatThrownBy(() -> EnterpriseOwnerProofPreflight.inspect(
                p, stored, null, CLOCK)).hasMessage("E22B_ED25519_PIN_REQUIRED");
        KeyPair wrongCurve = KeyPairGenerator.getInstance("Ed448").generateKeyPair();
        assertThatThrownBy(() -> EnterpriseOwnerProofPreflight.inspect(
                p, stored, wrongCurve.getPublic(), CLOCK))
                .hasMessage("E22B_ED25519_PIN_REQUIRED");
    }

    @Test
    void rejectsInvalidNonceAndMalformedSignature() throws Exception {
        var valid = proof();
        var nonce = new EnterpriseOwnerProofPreflight.SignedReviewProof(
                valid.claimedScope(), valid.purpose(), "../evil",
                valid.issuedAt(), valid.expiresAt(), valid.signatureBase64Url());
        assertThatThrownBy(() -> inspect(nonce))
                .hasMessage("E22B_PROOF_NONCE_INVALID");
        var signature = new EnterpriseOwnerProofPreflight.SignedReviewProof(
                valid.claimedScope(), valid.purpose(), valid.nonceHex(),
                valid.issuedAt(), valid.expiresAt(), "not-base64");
        assertThatThrownBy(() -> inspect(signature))
                .hasMessage("E22B_PROOF_SIGNATURE_ENCODING_INVALID");
    }

    @Test
    void identicalProofRepeatStillNeverProducesPositiveApproval() throws Exception {
        var token = proof();
        var r1 = inspect(token);
        var r2 = inspect(token);
        assertThat(r1).isEqualTo(r2);
        assertThat(r1.approvalGranted()).isFalse();
        assertThat(r2.approvalGranted()).isFalse();
        assertThat(r1.replayProtectionCertified()).isFalse();
        assertThat(r2.nonceConsumed()).isFalse();
    }

    @Test
    void rejectsDifferentStoredReviewIntentEvenWithSameArtifactHashes() throws Exception {
        var correct = proof();
        var another = new EnterpriseOwnerProofPreflight.ReviewScope(
                "00000000-0000-4000-8000-000000000002",
                stored.projectId(), stored.companyId(), stored.divisionId(),
                stored.briefSha256(), stored.storyboardSha256(), stored.manifestSha256(),
                stored.intentIssuedAt(), stored.intentExpiresAt());
        assertThatThrownBy(() -> EnterpriseOwnerProofPreflight.inspect(
                correct, another, signer.getPublic(), CLOCK))
                .hasMessage("E22B_PROOF_SCOPE_MISMATCH");
    }
}
