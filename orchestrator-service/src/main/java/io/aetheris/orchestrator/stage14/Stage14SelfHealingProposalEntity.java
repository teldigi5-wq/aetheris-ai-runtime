package io.aetheris.orchestrator.stage14;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stage14_self_healing_proposals")
public class Stage14SelfHealingProposalEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID incidentId;
    @Column(nullable = false)
    private UUID taskId;
    @Column(nullable = false, length = 48)
    private String action;
    @Column(nullable = false, length = 160)
    private String target;
    @Column(nullable = false, length = 1200)
    private String rationale;
    @Column(nullable = false, length = 40)
    private String status;
    private UUID approvalId;
    @Column(nullable = false)
    private boolean executed;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Stage14SelfHealingProposalEntity() {}

    public Stage14SelfHealingProposalEntity(UUID id, UUID incidentId, UUID taskId, String action, String target, String rationale) {
        this.id = id;
        this.incidentId = incidentId;
        this.taskId = taskId;
        this.action = action;
        this.target = target;
        this.rationale = rationale;
        this.status = "PROPOSED_AWAITING_APPROVAL";
        this.executed = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void authorize(UUID approvalId) {
        this.approvalId = approvalId;
        this.status = "APPROVED_NOT_EXECUTED";
        this.executed = false;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getIncidentId() { return incidentId; }
    public UUID getTaskId() { return taskId; }
    public String getAction() { return action; }
    public String getTarget() { return target; }
    public String getRationale() { return rationale; }
    public String getStatus() { return status; }
    public UUID getApprovalId() { return approvalId; }
    public boolean isExecuted() { return executed; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
