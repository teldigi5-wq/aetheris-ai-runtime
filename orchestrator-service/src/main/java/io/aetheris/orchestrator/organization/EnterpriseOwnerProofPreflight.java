package io.aetheris.orchestrator.organization;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.EdECPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * E2.2B-2B: isolated Ed25519 proof-of-possession preflight, NOT owner authentication.
 *
 * A candidate verification key MUST be pinned by a trustworthy owner enrollment
 * protocol in a future slice, never supplied by the requester. This component has
 * no Spring bean, HTTP route, command interface, approval grant, or action executor.
 * Even valid signatures do NOT establish the current user, trust a caller, consume
 * a nonce durably, or authorize any action.
 */
public final class EnterpriseOwnerProofPreflight {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern NONCE = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern SIGNATURE = Pattern.compile("[A-Za-z0-9_-]{86}");
    private static final String PURPOSE = "REVIEW_STATIC_DESIGN_ONLY";
    private static final String DOMAIN = "AETHERIS_E22B_OWNER_PROOF_PREFLIGHT_V1";
    private static final long MAX_PROOF_SECONDS = 300L;

    private EnterpriseOwnerProofPreflight() {}

    public record ReviewScope(String intentId, String projectId, String companyId,
                              String divisionId, String briefSha256, String storyboardSha256,
                              String manifestSha256, Instant intentIssuedAt,
                              Instant intentExpiresAt) {
        public static ReviewScope fromStoredIntent(EnterpriseDesignReviewIntent stored) {
            if (stored == null ||
                    !"PENDING_OWNER_AUTHENTICATION_UNAVAILABLE".equals(stored.getStatus())) {
                throw new IllegalArgumentException("E22B_STORED_INTENT_NOT_PENDING");
            }
            return new ReviewScope(stored.getId(), stored.getProjectId(),
                    stored.getCompanyId(), stored.getDivisionId(),
                    stored.getBriefDigest(), stored.getStoryboardDigest(),
                    stored.getManifestDigest(), stored.getIssuedAt(),
                    stored.getExpiresAt());
        }
    }

    public record SignedReviewProof(ReviewScope claimedScope, String purpose,
                                    String nonceHex, Instant issuedAt,
                                    Instant expiresAt, String signatureBase64Url) {}

    /** All non-authorizing booleans are intentionally hardcoded false. */
    public record Inspection(String evidenceState, String intentId,
                             boolean cryptographicSignatureValid,
                             boolean actualOwnerAuthenticated,
                             boolean approvalGranted, boolean nonceConsumed,
                             boolean executionReleased, boolean replayProtectionCertified) {}

    /**
     * Verifies that a proof's bytes were signed by the provided key, and that
     * its exact project, artifact hashes, expiry and purpose match a persisted
     * review intent. No owner key provisioning or trust is inferred.
     */
    public static Inspection inspect(SignedReviewProof proof, ReviewScope stored,
                                     PublicKey pinnedCandidateKey, Clock clock) {
        require(proof != null && stored != null && clock != null, "E22B_PROOF_INPUT_INVALID");
        require(pinnedCandidateKey instanceof EdECPublicKey edKey
                && "Ed25519".equals(edKey.getParams().getName()),
                "E22B_ED25519_PIN_REQUIRED");
        validate(stored);
        require(stored.equals(proof.claimedScope()), "E22B_PROOF_SCOPE_MISMATCH");
        require(PURPOSE.equals(proof.purpose()), "E22B_PROOF_PURPOSE_REJECTED");
        require(proof.nonceHex() != null && NONCE.matcher(proof.nonceHex()).matches(),
                "E22B_PROOF_NONCE_INVALID");
        require(proof.issuedAt() != null && proof.expiresAt() != null,
                "E22B_PROOF_TIME_INVALID");
        Instant now = clock.instant();
        require(!now.isBefore(proof.issuedAt())
                && now.isBefore(proof.expiresAt())
                && !now.isBefore(stored.intentIssuedAt())
                && now.isBefore(stored.intentExpiresAt())
                && !proof.issuedAt().isBefore(stored.intentIssuedAt())
                && !proof.expiresAt().isAfter(stored.intentExpiresAt())
                && proof.expiresAt().isAfter(proof.issuedAt())
                && proof.expiresAt().getEpochSecond() - proof.issuedAt().getEpochSecond()
                       <= MAX_PROOF_SECONDS,
                "E22B_PROOF_EXPIRED_OR_OUTSIDE_INTENT");
        require(proof.signatureBase64Url() != null
                && SIGNATURE.matcher(proof.signatureBase64Url()).matches(),
                "E22B_PROOF_SIGNATURE_ENCODING_INVALID");
        final byte[] signature;
        try {
            signature = Base64.getUrlDecoder().decode(proof.signatureBase64Url());
            require(signature.length == 64, "E22B_PROOF_SIGNATURE_ENCODING_INVALID");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("E22B_PROOF_SIGNATURE_ENCODING_INVALID", e);
        }
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(pinnedCandidateKey);
            verifier.update(payload(proof));
            require(verifier.verify(signature), "E22B_PROOF_SIGNATURE_INVALID");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("E22B_PROOF_SIGNATURE_INVALID", e);
        }
        return new Inspection("SIGNATURE_VALID_OWNER_NOT_ATTESTED_REPLAY_NOT_TRACKED",
                stored.intentId(), true, false, false, false, false, false);
    }

    /**
     * Canonical, injection-safe signing material. Every non-timestamp value is
     * restricted to ASCII slugs, exact UUID, hex digest, nonce or fixed purpose.
     * Domain separator and all scope fields are signed, preventing reuse of an
     * unrelated content hash for a different action or organization.
     */
    static byte[] payload(SignedReviewProof proof) {
        require(proof != null && proof.claimedScope() != null,
                "E22B_PROOF_INPUT_INVALID");
        ReviewScope s = proof.claimedScope();
        validate(s);
        require(PURPOSE.equals(proof.purpose()) && proof.nonceHex() != null
                && NONCE.matcher(proof.nonceHex()).matches()
                && proof.issuedAt() != null && proof.expiresAt() != null,
                "E22B_PROOF_FORMAT_INVALID");
        String canonical = String.join("\n", DOMAIN, PURPOSE,
                s.intentId(), s.projectId(), s.companyId(), s.divisionId(),
                s.briefSha256(), s.storyboardSha256(), s.manifestSha256(),
                Long.toString(s.intentIssuedAt().getEpochSecond()),
                Long.toString(s.intentIssuedAt().getNano()),
                Long.toString(s.intentExpiresAt().getEpochSecond()),
                Long.toString(s.intentExpiresAt().getNano()),
                proof.nonceHex(), Long.toString(proof.issuedAt().getEpochSecond()),
                Long.toString(proof.issuedAt().getNano()),
                Long.toString(proof.expiresAt().getEpochSecond()),
                Long.toString(proof.expiresAt().getNano()), "");
        return canonical.getBytes(StandardCharsets.US_ASCII);
    }

    private static void validate(ReviewScope s) {
        require(s != null && s.intentId() != null && UUID.matcher(s.intentId()).matches()
                && slug(s.projectId()) && slug(s.companyId()) && slug(s.divisionId())
                && sha(s.briefSha256()) && sha(s.storyboardSha256()) && sha(s.manifestSha256())
                && s.intentIssuedAt() != null && s.intentExpiresAt() != null
                && s.intentExpiresAt().equals(s.intentIssuedAt().plusSeconds(900)),
                "E22B_PERSISTED_SCOPE_INVALID");
    }

    private static boolean slug(String s) {
        return s != null && s.length() <= 80 && SLUG.matcher(s).matches();
    }
    private static boolean sha(String s) {
        return s != null && SHA256.matcher(s).matches();
    }
    private static void require(boolean condition, String error) {
        if (!condition) throw new IllegalArgumentException(error);
    }
}
