package io.aetheris.orchestrator.organization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H2-backed, real database integration tests. No mocked owner authentication,
 * no positive permission output and no simulated authority as a substitute.
 */
@SpringBootTest
class EnterpriseDesignReviewIntakeServiceTest {
    @Autowired EnterpriseDesignArtifactService artifacts;
    @Autowired EnterpriseDesignReviewIntakeService review;
    @Autowired EnterpriseDesignArtifactVerifier verifier;
    @Autowired EnterpriseDesignReviewIntentRepository intents;
    @Autowired EnterpriseDesignReviewDenialRepository denials;
    @Autowired ObjectMapper mapper;

    private EnterpriseDesignArtifactService.ArtifactBundle artifact() {
        return artifacts.create(new EnterpriseAdvisoryPlanningService.DesignBrief(
                "laptop-cinematic", "aetheris-immersive", "scroll-experience-engineering",
                "Plan a cinematic 3D laptop launch website with scroll-driven reveal",
                List.of("Reverse scroll scrubs correctly",
                        "Accessible reduced-motion fallback is available",
                        "No production deployment without independent QA")));
    }

    @AfterEach
    void cleanup() {
        denials.deleteAll();
        intents.deleteAll();
    }

    @Test
    void persistsScopedIntentWithStrictExpiryAndNoApproval() {
        var bundle = artifact();
        var receipt = review.submitForUntrustedReview(bundle);
        var persisted = intents.findById(receipt.intentId()).orElseThrow();

        assertThat(receipt.ownerAuthenticated()).isFalse();
        assertThat(receipt.actionAuthorized()).isFalse();
        assertThat(receipt.state()).isEqualTo("PENDING_OWNER_AUTHENTICATION_UNAVAILABLE");
        assertThat(persisted.getId()).isEqualTo(receipt.intentId());
        assertThat(persisted.getProjectId()).isEqualTo("laptop-cinematic");
        assertThat(persisted.getCompanyId()).isEqualTo("aetheris-immersive");
        assertThat(persisted.getDivisionId()).isEqualTo("scroll-experience-engineering");
        assertThat(persisted.getStoryboardDigest()).isEqualTo(bundle.storyboardSha256());
        assertThat(persisted.getManifestDigest()).isEqualTo(bundle.manifestSha256());
        assertThat(persisted.getBriefDigest()).isEqualTo(bundle.briefFingerprint());
        assertThat(receipt.expiresAt()).isEqualTo(persisted.getIssuedAt().plusSeconds(900));
    }

    @Test
    void validArtifactStillCannotAuthorizeAndDenialIsPersisted() {
        var bundle = artifact();
        var receipt = review.submitForUntrustedReview(bundle);
        var decision = review.tryAuthorize(receipt.intentId(), bundle);
        assertThat(decision.reason()).isEqualTo("E22B_OWNER_AUTHENTICATION_NOT_CONNECTED");
        assertThat(decision.actionAuthorized()).isFalse();
        assertThat(decision.trustedOwnerVerified()).isFalse();
        assertThat(decision.approvalConsumed()).isFalse();
        assertThat(decision.executionReleased()).isFalse();
        assertThat(denials.findByIntentIdOrderByIdAsc(receipt.intentId()))
                .singleElement().extracting(EnterpriseDesignReviewDenial::getReason)
                .isEqualTo("E22B_OWNER_AUTHENTICATION_NOT_CONNECTED");
    }

    @Test
    void repeatedAttemptsNeverGrantApprovalOrConsumeAReusableGrant() {
        var bundle = artifact();
        var receipt = review.submitForUntrustedReview(bundle);
        for (int i = 0; i < 5; i++) {
            var denied = review.tryAuthorize(receipt.intentId(), bundle);
            assertThat(denied.actionAuthorized()).isFalse();
            assertThat(denied.approvalConsumed()).isFalse();
        }
        assertThat(denials.findByIntentIdOrderByIdAsc(receipt.intentId())).hasSize(5);
        assertThat(intents.findById(receipt.intentId()).orElseThrow().getStatus())
                .isEqualTo("PENDING_OWNER_AUTHENTICATION_UNAVAILABLE");
    }

    @Test
    void changedProjectScopeOrArtifactHashFailsClosed() {
        var original = artifact();
        var receipt = review.submitForUntrustedReview(original);
        var other = artifacts.create(new EnterpriseAdvisoryPlanningService.DesignBrief(
                "different-project", "aetheris-software", "web-frontend-engineering",
                "Plan a static website", List.of("Independent browser review")));
        var denied = review.tryAuthorize(receipt.intentId(), other);
        assertThat(denied.reason()).isEqualTo("E22B_SCOPE_OR_CONTENT_CHANGED");
        assertThat(denied.actionAuthorized()).isFalse();
    }

    @Test
    void forgedOwnerApprovalFlagWithRecomputedDigestIsDenied() throws Exception {
        var original = artifact();
        var receipt = review.submitForUntrustedReview(original);
        ObjectNode modified = (ObjectNode) mapper.readTree(original.manifestJson());
        modified.put("ownerApprovalGranted", true);
        String tamperedJson = mapper.writeValueAsString(modified);
        var forged = new EnterpriseDesignArtifactService.ArtifactBundle(
                original.schemaVersion(), original.evidenceClass(), original.projectId(),
                original.companyId(), original.divisionId(), original.briefFingerprint(),
                original.storyboardSvg(), original.storyboardSha256(),
                tamperedJson, EnterpriseDesignArtifactService.sha256(tamperedJson),
                false, false, false, false);
        var denied = review.tryAuthorize(receipt.intentId(), forged);
        assertThat(denied.reason()).isEqualTo("E22B_UNVERIFIED_ARTIFACT");
        assertThat(denied.actionAuthorized()).isFalse();
    }

    @Test
    void expiredIntentRefusedBeforeConsideringArtifact() {
        var bundle = artifact();
        var receipt = review.submitForUntrustedReview(bundle);
        Clock future = Clock.fixed(receipt.expiresAt().plusSeconds(1), ZoneOffset.UTC);
        var later = new EnterpriseDesignReviewIntakeService(verifier, intents, denials, future);
        var decision = later.tryAuthorize(receipt.intentId(), bundle);
        assertThat(decision.reason()).isEqualTo("E22B_INTENT_EXPIRED");
        assertThat(decision.executionReleased()).isFalse();
    }

    @Test
    void missingArtifactCannotTurnReceiptIntoExecutableApproval() {
        var bundle = artifact();
        var receipt = review.submitForUntrustedReview(bundle);
        assertThat(review.tryAuthorize(receipt.intentId(), null).reason())
                .isEqualTo("E22B_ARTIFACT_ABSENT");
        assertThat(intents.findById(receipt.intentId()).orElseThrow().getStatus())
                .isEqualTo("PENDING_OWNER_AUTHENTICATION_UNAVAILABLE");
    }

    @Test
    void invalidIdsCannotProbeDatabaseOrCreateDenialLogs() {
        assertThatThrownBy(() -> review.tryAuthorize("../other-company", artifact()))
                .hasMessage("E22B_REVIEW_ID_INVALID");
        assertThatThrownBy(() -> review.tryAuthorize(
                "00000000-0000-4000-8000-000000000000", artifact()))
                .hasMessage("E22B_REVIEW_INTENT_UNKNOWN");
        assertThat(denials.count()).isZero();
    }

    @Test
    void malformedArtifactRejectedBeforePersistence() {
        var bundle = artifact();
        var corrupted = new EnterpriseDesignArtifactService.ArtifactBundle(
                bundle.schemaVersion(), bundle.evidenceClass(), bundle.projectId(),
                bundle.companyId(), bundle.divisionId(), bundle.briefFingerprint(),
                bundle.storyboardSvg() + "tampered", bundle.storyboardSha256(),
                bundle.manifestJson(), bundle.manifestSha256(),
                false, false, false, false);
        assertThatThrownBy(() -> review.submitForUntrustedReview(corrupted))
                .hasMessage("E22_ARTIFACT_DIGEST_MISMATCH");
        assertThat(intents.count()).isZero();
    }
}
