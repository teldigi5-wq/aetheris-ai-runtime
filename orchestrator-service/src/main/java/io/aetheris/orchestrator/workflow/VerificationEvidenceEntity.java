package io.aetheris.orchestrator.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aetheris_verification_evidence")
public class VerificationEvidenceEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID workflowId;

    @Column(nullable = false)
    private UUID taskId;

    @Column(nullable = false, length = 32)
    private String phase;

    @Column(nullable = false, length = 120)
    private String agentId;

    @Column(nullable = false, length = 120)
    private String evidenceType;

    @Column(nullable = false)
    private boolean passed;

    @Column(nullable = false, length = 2000)
    private String summary;

    @Column(nullable = false, length = 8000)
    private String detail;

    @Column(nullable = false)
    private Instant createdAt;

    protected VerificationEvidenceEntity() {
    }

    public VerificationEvidenceEntity(UUID id, UUID workflowId, UUID taskId, String phase, String agentId, String evidenceType, boolean passed, String summary, String detail) {
        this.id = id;
        this.workflowId = workflowId;
        this.taskId = taskId;
        this.phase = phase;
        this.agentId = agentId;
        this.evidenceType = evidenceType;
        this.passed = passed;
        this.summary = summary;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getWorkflowId() { return workflowId; }
    public UUID getTaskId() { return taskId; }
    public String getPhase() { return phase; }
    public String getAgentId() { return agentId; }
    public String getEvidenceType() { return evidenceType; }
    public boolean isPassed() { return passed; }
    public String getSummary() { return summary; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
