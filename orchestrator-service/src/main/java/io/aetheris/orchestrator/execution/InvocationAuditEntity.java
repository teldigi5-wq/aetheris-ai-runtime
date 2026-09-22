package io.aetheris.orchestrator.execution;

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
@Table(name = "aetheris_invocation_audit")
public class InvocationAuditEntity {

    @Id
    private UUID id;

    private UUID taskId;

    @Column(length = 120)
    private String agentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private InvocationKind kind;

    @Column(nullable = false, length = 240)
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private InvocationStatus status;

    @Column(nullable = false, length = 16000)
    private String metadataJson;

    @Column(length = 4000)
    private String detail;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant completedAt;

    @Version
    private long version;

    protected InvocationAuditEntity() {
    }

    public InvocationAuditEntity(UUID id, UUID taskId, String agentId, InvocationKind kind, String targetId, String metadataJson) {
        this.id = id;
        this.taskId = taskId;
        this.agentId = agentId;
        this.kind = kind;
        this.targetId = targetId;
        this.metadataJson = metadataJson;
        this.status = InvocationStatus.STARTED;
        this.startedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTaskId() { return taskId; }
    public String getAgentId() { return agentId; }
    public InvocationKind getKind() { return kind; }
    public String getTargetId() { return targetId; }
    public InvocationStatus getStatus() { return status; }
    public String getMetadataJson() { return metadataJson; }
    public String getDetail() { return detail; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getVersion() { return version; }

    public void finish(InvocationStatus status, String detail, String metadataJson) {
        if (status == InvocationStatus.STARTED) throw new IllegalArgumentException("Terminal invocation status is required");
        this.status = status;
        this.detail = detail;
        this.metadataJson = metadataJson;
        this.completedAt = Instant.now();
    }
}
