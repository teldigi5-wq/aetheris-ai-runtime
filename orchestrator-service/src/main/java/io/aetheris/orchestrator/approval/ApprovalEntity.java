package io.aetheris.orchestrator.approval;

import io.aetheris.orchestrator.agent.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aetheris_approvals")
public class ApprovalEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID taskId;

    @Column(nullable = false, length = 120)
    private String actionType;

    @Column(nullable = false, length = 2000)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ApprovalStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant decidedAt;

    @Column(length = 2000)
    private String decisionNote;

    @Version
    private long version;

    protected ApprovalEntity() {
    }

    public ApprovalEntity(UUID id, UUID taskId, String actionType, String summary, RiskLevel riskLevel) {
        this.id = id;
        this.taskId = taskId;
        this.actionType = actionType;
        this.summary = summary;
        this.riskLevel = riskLevel;
        this.status = ApprovalStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTaskId() { return taskId; }
    public String getActionType() { return actionType; }
    public String getSummary() { return summary; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public ApprovalStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecisionNote() { return decisionNote; }
    public long getVersion() { return version; }

    public void decide(boolean approved, String note) {
        if (status != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Approval has already been decided: " + status);
        }
        this.status = approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
        this.decisionNote = note;
        this.decidedAt = Instant.now();
    }
}
