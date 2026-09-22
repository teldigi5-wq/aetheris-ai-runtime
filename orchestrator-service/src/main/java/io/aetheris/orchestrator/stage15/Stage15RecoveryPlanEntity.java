package io.aetheris.orchestrator.stage15;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stage15_recovery_plans")
public class Stage15RecoveryPlanEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID incidentId;
    @Column(nullable = false) private UUID taskId;
    @Column(nullable = false, length = 80) private String serviceId;
    @Column(nullable = false, length = 48) private String action;
    @Column(nullable = false, length = 48) private String rollbackAction;
    @Column(nullable = false, length = 48) private String verificationPolicy;
    @Column(nullable = false, length = 64) private String planSha256;
    @Column(nullable = false, length = 48) private String status;
    private UUID approvalId;
    @Column(length = 64) private String executionAttestationSha256;
    @Column(length = 64) private String verificationAttestationSha256;
    @Column(nullable = false) private boolean executionObserved;
    @Column(nullable = false) private boolean executedByAetheris;
    @Column(nullable = false) private boolean rollbackRequired;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected Stage15RecoveryPlanEntity() {}

    public Stage15RecoveryPlanEntity(UUID id, UUID incidentId, UUID taskId, String serviceId, String action,
                                     String rollbackAction, String verificationPolicy, String planSha256) {
        this.id = id; this.incidentId = incidentId; this.taskId = taskId; this.serviceId = serviceId;
        this.action = action; this.rollbackAction = rollbackAction; this.verificationPolicy = verificationPolicy;
        this.planSha256 = planSha256; this.status = "PLANNED_AWAITING_APPROVAL";
        this.executionObserved = false; this.executedByAetheris = false; this.rollbackRequired = false;
        this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }

    public void authorize(UUID approvalId) {
        this.approvalId = approvalId;
        this.status = "AUTHORIZED_PENDING_TARGET_EVIDENCE";
        this.executedByAetheris = false;
        this.updatedAt = Instant.now();
    }
    public void recordExecutionEvidence(String sha256, boolean actionObserved) {
        this.executionAttestationSha256 = sha256;
        this.executionObserved = actionObserved;
        this.status = actionObserved ? "TARGET_EXECUTION_EVIDENCE_RECORDED" : "TARGET_REPORTED_EXECUTION_FAILED";
        this.executedByAetheris = false;
        this.updatedAt = Instant.now();
    }
    public void verify(String sha256, boolean recovered) {
        this.verificationAttestationSha256 = sha256;
        this.rollbackRequired = !recovered;
        this.status = recovered ? "VERIFIED_RECOVERED" : "VERIFY_FAILED_ROLLBACK_REQUIRED";
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getIncidentId() { return incidentId; }
    public UUID getTaskId() { return taskId; }
    public String getServiceId() { return serviceId; }
    public String getAction() { return action; }
    public String getRollbackAction() { return rollbackAction; }
    public String getVerificationPolicy() { return verificationPolicy; }
    public String getPlanSha256() { return planSha256; }
    public String getStatus() { return status; }
    public UUID getApprovalId() { return approvalId; }
    public String getExecutionAttestationSha256() { return executionAttestationSha256; }
    public String getVerificationAttestationSha256() { return verificationAttestationSha256; }
    public boolean isExecutionObserved() { return executionObserved; }
    public boolean isExecutedByAetheris() { return executedByAetheris; }
    public boolean isRollbackRequired() { return rollbackRequired; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
