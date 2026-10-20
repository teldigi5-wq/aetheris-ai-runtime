package io.aetheris.orchestrator.skills;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Database-global nonce reservation for signed CANDIDATE skill proof inspections.
 * No owner identity, durable approval, lease to execute or model calls follow from this row.
 * Retention must keep nonces across the entire accepted replay horizon.
 */
@Entity
@Table(name = "enterprise_skill_candidate_proof_nonces")
public class SkillProofNonceEntity {
    @Id
    @Column(name = "nonce_hex", nullable = false, length = 32, updatable = false)
    private String nonceHex;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "agent_id", nullable = false, length = 64, updatable = false)
    private String agentId;

    @Column(name = "skill_id", nullable = false, length = 64, updatable = false)
    private String skillId;

    @Column(name = "proof_sha256", nullable = false, length = 64, updatable = false)
    private String proofSha256;

    @Column(name = "observed_at", nullable = false, updatable = false)
    private Instant observedAt;

    @Column(name = "proof_expires_at", nullable = false, updatable = false)
    private Instant proofExpiresAt;

    @Column(name = "evidence_state", nullable = false, length = 80, updatable = false)
    private String evidenceState;

    protected SkillProofNonceEntity() {}

    SkillProofNonceEntity(String nonceHex, UUID taskId, String agentId,
            String skillId, String proofSha256, Instant observedAt, Instant proofExpiresAt) {
        this.nonceHex = nonceHex;
        this.taskId = taskId;
        this.agentId = agentId;
        this.skillId = skillId;
        this.proofSha256 = proofSha256;
        this.observedAt = observedAt;
        this.proofExpiresAt = proofExpiresAt;
        this.evidenceState = "CANDIDATE_SIGNATURE_INSPECTED_NOT_OWNER_AUTHENTICATED";
    }

    public String getNonceHex() { return nonceHex; }
    public UUID getTaskId() { return taskId; }
    public String getAgentId() { return agentId; }
    public String getSkillId() { return skillId; }
    public String getProofSha256() { return proofSha256; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getProofExpiresAt() { return proofExpiresAt; }
    public String getEvidenceState() { return evidenceState; }
}
