package io.aetheris.orchestrator.organization;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A real H2-stored intent is the source of truth, never caller identity headers. */
@SpringBootTest
class EnterpriseOwnerProofStoredIntentTest {
    @Autowired EnterpriseDesignReviewIntakeService intake;
    @Autowired EnterpriseDesignArtifactService artifacts;
    @Autowired EnterpriseDesignReviewIntentRepository intents;
    @Autowired EnterpriseDesignReviewDenialRepository denials;

    @AfterEach
    void cleanup() {
        denials.deleteAll();
        intents.deleteAll();
    }

    @Test
    void verifiedSignatureMatchesActualPersistedCompanyAndDesignDigestsButStillCannotApprove()
            throws Exception {
        var bundle = artifacts.create(new EnterpriseAdvisoryPlanningService.DesignBrief(
                "cinematic-laptop", "aetheris-immersive", "scroll-experience-engineering",
                "Prepare cinematic laptop storyboard with reverse scroll",
                List.of("Reduced-motion alternative", "Independent browser QA")));
        var receipt = intake.submitForUntrustedReview(bundle);
        var actual = intents.findById(receipt.intentId()).orElseThrow();
        var trustedScope = EnterpriseOwnerProofPreflight.ReviewScope.fromStoredIntent(actual);
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var issued = actual.getIssuedAt().plusSeconds(40);
        var expires = issued.plusSeconds(120);
        var unsigned = new EnterpriseOwnerProofPreflight.SignedReviewProof(
                trustedScope, "REVIEW_STATIC_DESIGN_ONLY", "1".repeat(32),
                issued, expires, "A".repeat(86));
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keys.getPrivate());
        signer.update(EnterpriseOwnerProofPreflight.payload(unsigned));
        var signed = new EnterpriseOwnerProofPreflight.SignedReviewProof(
                trustedScope, unsigned.purpose(), unsigned.nonceHex(), issued, expires,
                Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));

        var preflight = EnterpriseOwnerProofPreflight.inspect(
                signed, trustedScope, keys.getPublic(),
                Clock.fixed(issued.plusSeconds(1), ZoneOffset.UTC));
        assertThat(preflight.cryptographicSignatureValid()).isTrue();
        assertThat(preflight.actualOwnerAuthenticated()).isFalse();
        assertThat(preflight.approvalGranted()).isFalse();
        assertThat(preflight.executionReleased()).isFalse();
        assertThat(preflight.replayProtectionCertified()).isFalse();

        // Prior durable control still *always* denies this same unchanged artifact.
        var denial = intake.tryAuthorize(receipt.intentId(), bundle);
        assertThat(denial.reason()).isEqualTo("E22B_OWNER_AUTHENTICATION_NOT_CONNECTED");
        assertThat(denial.actionAuthorized()).isFalse();
        assertThat(denials.findByIntentIdOrderByIdAsc(receipt.intentId())).hasSize(1);
    }

    @Test
    void rejectsCallerSuppliedFakeScopeInsteadOfTrustingStoredIntent() throws Exception {
        var bundle = artifacts.create(new EnterpriseAdvisoryPlanningService.DesignBrief(
                "owner-scope-test", "aetheris-immersive", "frame-sequence-animation",
                "Prepare a static frame story", List.of("Non-authorized design review")));
        var receipt = intake.submitForUntrustedReview(bundle);
        var stored = EnterpriseOwnerProofPreflight.ReviewScope.fromStoredIntent(
                intents.findById(receipt.intentId()).orElseThrow());
        var falsified = new EnterpriseOwnerProofPreflight.ReviewScope(
                stored.intentId(), stored.projectId(), "aetheris-finance", stored.divisionId(),
                stored.briefSha256(), stored.storyboardSha256(), stored.manifestSha256(),
                stored.intentIssuedAt(), stored.intentExpiresAt());
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var issued = stored.intentIssuedAt().plusSeconds(30);
        var unsigned = new EnterpriseOwnerProofPreflight.SignedReviewProof(
                falsified, "REVIEW_STATIC_DESIGN_ONLY", "2".repeat(32),
                issued, issued.plusSeconds(100), "A".repeat(86));
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keys.getPrivate());
        signer.update(EnterpriseOwnerProofPreflight.payload(unsigned));
        var fake = new EnterpriseOwnerProofPreflight.SignedReviewProof(
                falsified, unsigned.purpose(), unsigned.nonceHex(),
                unsigned.issuedAt(), unsigned.expiresAt(),
                Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
        assertThatThrownBy(() -> EnterpriseOwnerProofPreflight.inspect(
                fake, stored, keys.getPublic(),
                Clock.fixed(issued.plusSeconds(1), ZoneOffset.UTC)))
                .hasMessage("E22B_PROOF_SCOPE_MISMATCH");
    }
}
