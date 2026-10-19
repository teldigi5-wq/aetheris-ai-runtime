package io.aetheris.orchestrator.organization;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * E2.2B-1: durable intake and fail-closed denial of an untrusted review request.
 *
 * This DOES NOT authenticate the human owner, issue approval credentials, grant
 * agent action rights, verify the initiator, or implement replay-safe APPROVAL.
 * No approval/consumption API is present. Every attempt returns authorized=false,
 * and the negative decision is recorded in a database transaction.
 *
 * Do not expose this internal service over HTTP or connect it to tool execution.
 */
@Service
public class EnterpriseDesignReviewIntakeService {
    private static final String PENDING = "PENDING_OWNER_AUTHENTICATION_UNAVAILABLE";
    private final EnterpriseDesignArtifactVerifier verifier;
    private final EnterpriseDesignReviewIntentRepository intents;
    private final EnterpriseDesignReviewDenialRepository denials;
    private final Clock clock;

    @Autowired
    public EnterpriseDesignReviewIntakeService(EnterpriseDesignArtifactVerifier verifier,
            EnterpriseDesignReviewIntentRepository intents,
            EnterpriseDesignReviewDenialRepository denials) {
        this(verifier, intents, denials, Clock.systemUTC());
    }

    EnterpriseDesignReviewIntakeService(EnterpriseDesignArtifactVerifier verifier,
            EnterpriseDesignReviewIntentRepository intents,
            EnterpriseDesignReviewDenialRepository denials, Clock clock) {
        this.verifier = verifier;
        this.intents = intents;
        this.denials = denials;
        this.clock = clock;
    }

    @Transactional
    public ReviewReceipt submitForUntrustedReview(
            EnterpriseDesignArtifactService.ArtifactBundle artifact) {
        // A valid static contract is still not an authenticated approval request.
        verifier.verify(artifact);
        // PostgreSQL and H2 persist TIMESTAMP at microsecond precision.\n        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant expiry = now.plus(15, ChronoUnit.MINUTES);
        EnterpriseDesignReviewIntent intent = new EnterpriseDesignReviewIntent(
                UUID.randomUUID().toString(), artifact.projectId(), artifact.companyId(),
                artifact.divisionId(), artifact.briefFingerprint(),
                artifact.storyboardSha256(), artifact.manifestSha256(), now, expiry);
        intents.saveAndFlush(intent);
        return new ReviewReceipt(intent.getId(), intent.getProjectId(),
                intent.getCompanyId(), intent.getDivisionId(), expiry,
                PENDING, false, false);
    }

    /**
     * This method intentionally cannot grant approval. It persists a refusal,
     * even when the artifact matches, because no verified owner principal or
     * single-use token consumer is integrated.
     */
    @Transactional
    public DenialResult tryAuthorize(String intentId,
            EnterpriseDesignArtifactService.ArtifactBundle candidate) {
        if (intentId == null || !canonicalUuid(intentId)) {
            throw new IllegalArgumentException("E22B_REVIEW_ID_INVALID");
        }
        EnterpriseDesignReviewIntent intent = intents.findById(intentId)
                .orElseThrow(() -> new IllegalArgumentException("E22B_REVIEW_INTENT_UNKNOWN"));

        String reason;
        if (!PENDING.equals(intent.getStatus())) {
            reason = "E22B_STATUS_NOT_AUTHORIZABLE";
        } else if (!clock.instant().isBefore(intent.getExpiresAt())) {
            reason = "E22B_INTENT_EXPIRED";
        } else if (candidate == null) {
            reason = "E22B_ARTIFACT_ABSENT";
        } else {
            try {
                verifier.verify(candidate);
                boolean same = intent.getProjectId().equals(candidate.projectId())
                        && intent.getCompanyId().equals(candidate.companyId())
                        && intent.getDivisionId().equals(candidate.divisionId())
                        && intent.getBriefDigest().equals(candidate.briefFingerprint())
                        && intent.getStoryboardDigest().equals(candidate.storyboardSha256())
                        && intent.getManifestDigest().equals(candidate.manifestSha256());
                reason = same ? "E22B_OWNER_AUTHENTICATION_NOT_CONNECTED"
                              : "E22B_SCOPE_OR_CONTENT_CHANGED";
            } catch (RuntimeException invalid) {
                reason = "E22B_UNVERIFIED_ARTIFACT";
            }
        }

        denials.saveAndFlush(new EnterpriseDesignReviewDenial(
                intent.getId(), reason, clock.instant()));
        return new DenialResult(intentId, reason, false,
                false, false, false, PENDING);
    }

    private static boolean canonicalUuid(String value) {
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public record ReviewReceipt(String intentId, String projectId,
                                String companyId, String divisionId,
                                Instant expiresAt, String state,
                                boolean ownerAuthenticated, boolean actionAuthorized) {}

    public record DenialResult(String intentId, String reason,
                               boolean actionAuthorized, boolean approvalConsumed,
                               boolean trustedOwnerVerified, boolean executionReleased,
                               String storedIntentState) {}
}
