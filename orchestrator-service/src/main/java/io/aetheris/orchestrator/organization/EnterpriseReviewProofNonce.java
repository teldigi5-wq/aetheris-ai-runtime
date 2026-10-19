package io.aetheris.orchestrator.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A global nonce reservation for review proof byte inspections, never an approval.
 * The primary key is intentionally global to block reuse across all review intents.
 * Deny-only evidence: a saved row does not authenticate the signer or authorize tools.
 */
@Entity
@Table(name = "enterprise_review_proof_nonces")
public class EnterpriseReviewProofNonce {
    @Id
    @Column(name = "nonce_hex", length = 32, nullable = false, updatable = false)
    private String nonceHex;

    @Column(name = "intent_id", length = 36, nullable = false, updatable = false)
    private String intentId;

    @Column(name = "project_id", length = 80, nullable = false, updatable = false)
    private String projectId;

    @Column(name = "proof_sha256", length = 64, nullable = false, updatable = false)
    private String proofSha256;

    @Column(name = "observed_at", nullable = false, updatable = false)
    private Instant observedAt;

    @Column(name = "proof_expires_at", nullable = false, updatable = false)
    private Instant proofExpiresAt;

    @Column(name = "evidence_state", length = 64, nullable = false, updatable = false)
    private String evidenceState;

    protected EnterpriseReviewProofNonce() {}

    EnterpriseReviewProofNonce(String nonceHex, String intentId, String projectId,
                               String proofSha256, Instant observedAt, Instant proofExpiresAt) {
        this.nonceHex = nonceHex;
        this.intentId = intentId;
        this.projectId = projectId;
        this.proofSha256 = proofSha256;
        this.observedAt = observedAt;
        this.proofExpiresAt = proofExpiresAt;
        this.evidenceState = "SIGNED_BYTES_SEEN_OWNER_UNVERIFIED";
    }

    public String getNonceHex() { return nonceHex; }
    public String getIntentId() { return intentId; }
    public String getProjectId() { return projectId; }
    public String getProofSha256() { return proofSha256; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getProofExpiresAt() { return proofExpiresAt; }
    public String getEvidenceState() { return evidenceState; }
}
