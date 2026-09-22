package io.aetheris.orchestrator.task;

import io.aetheris.orchestrator.policy.OperationMode;
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
@Table(name = "aetheris_tasks")
public class TaskEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 8000)
    private String commandText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskState state;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OperationMode mode;

    @Column(length = 120)
    private String activeAgentId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected TaskEntity() {
    }

    public TaskEntity(UUID id, String title, String commandText, OperationMode mode) {
        this.id = id;
        this.title = title;
        this.commandText = commandText;
        this.mode = mode;
        this.state = TaskState.QUEUED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getCommandText() { return commandText; }
    public TaskState getState() { return state; }
    public OperationMode getMode() { return mode; }
    public String getActiveAgentId() { return activeAgentId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void transitionTo(TaskState nextState, String agentId) {
        this.state = nextState;
        this.activeAgentId = agentId;
        this.updatedAt = Instant.now();
    }
}
