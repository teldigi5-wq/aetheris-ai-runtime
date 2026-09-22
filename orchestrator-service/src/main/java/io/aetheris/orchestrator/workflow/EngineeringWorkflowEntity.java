package io.aetheris.orchestrator.workflow;

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
@Table(name = "aetheris_engineering_workflows")
public class EngineeringWorkflowEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EngineeringWorkflowPhase phase;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected EngineeringWorkflowEntity() {
    }

    public EngineeringWorkflowEntity(UUID id, UUID taskId, EngineeringWorkflowPhase phase) {
        this.id = id;
        this.taskId = taskId;
        this.phase = phase;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTaskId() { return taskId; }
    public EngineeringWorkflowPhase getPhase() { return phase; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void moveTo(EngineeringWorkflowPhase next) {
        this.phase = next;
        this.updatedAt = Instant.now();
    }
}
