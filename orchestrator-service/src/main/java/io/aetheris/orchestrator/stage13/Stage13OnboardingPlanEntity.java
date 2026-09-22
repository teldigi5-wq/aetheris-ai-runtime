package io.aetheris.orchestrator.stage13;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aetheris_stage13_onboarding_plans")
public class Stage13OnboardingPlanEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID taskId;
    @Column(nullable = false, length = 80) private String providerId;
    @Column(nullable = false, length = 48) private String status;
    @Column(length = 120) private String credentialAlias;
    private UUID approvalId;
    @Column(length = 64) private String evidenceSha256;
    @Column(nullable = false) private boolean rollbackAvailable;
    @Column(nullable = false) private boolean externalActionAttempted;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected Stage13OnboardingPlanEntity() {}

    public Stage13OnboardingPlanEntity(UUID id, UUID taskId, String providerId, String credentialAlias) {
        this.id = id;
        this.taskId = taskId;
        this.providerId = providerId;
        this.credentialAlias = credentialAlias;
        this.status = "EVIDENCE_REQUIRED";
        this.rollbackAvailable = false;
        this.externalActionAttempted = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTaskId() { return taskId; }
    public String getProviderId() { return providerId; }
    public String getStatus() { return status; }
    public String getCredentialAlias() { return credentialAlias; }
    public UUID getApprovalId() { return approvalId; }
    public String getEvidenceSha256() { return evidenceSha256; }
    public boolean isRollbackAvailable() { return rollbackAvailable; }
    public boolean isExternalActionAttempted() { return externalActionAttempted; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void markAwaitingApproval() {
        status = "AWAITING_OWNER_APPROVAL";
        updatedAt = Instant.now();
    }

    public void authorize(UUID approvalId) {
        this.approvalId = approvalId;
        this.status = "CONTROLLED_TEST_AUTHORIZED_NOT_EXECUTED";
        this.externalActionAttempted = false;
        this.updatedAt = Instant.now();
    }

    public void recordEvidence(String status, String evidenceSha256, boolean rollbackAvailable) {
        this.status = status;
        this.evidenceSha256 = evidenceSha256;
        this.rollbackAvailable = rollbackAvailable;
        this.externalActionAttempted = false;
        this.updatedAt = Instant.now();
    }
}
