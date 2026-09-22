package io.aetheris.orchestrator.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aetheris_task_events")
public class TaskEventJournalEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID taskId;

    @Column(nullable = false)
    private Instant eventAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskState state;

    @Column(length = 120)
    private String agentId;

    @Column(nullable = false, length = 8000)
    private String message;

    @Column(nullable = false, length = 16000)
    private String metadataJson;

    protected TaskEventJournalEntity() {
    }

    public TaskEventJournalEntity(UUID id, UUID taskId, Instant eventAt, TaskState state, String agentId, String message, String metadataJson) {
        this.id = id;
        this.taskId = taskId;
        this.eventAt = eventAt;
        this.state = state;
        this.agentId = agentId;
        this.message = message;
        this.metadataJson = metadataJson;
    }

    public UUID getId() { return id; }
    public UUID getTaskId() { return taskId; }
    public Instant getEventAt() { return eventAt; }
    public TaskState getState() { return state; }
    public String getAgentId() { return agentId; }
    public String getMessage() { return message; }
    public String getMetadataJson() { return metadataJson; }
}
