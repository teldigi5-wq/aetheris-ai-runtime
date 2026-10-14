package io.aetheris.orchestrator.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Owner-specific durable checkpoint. A checkpoint records a CLIENT-DECLARED
 * acknowledgement, not proof of desktop delivery or task execution.
 * No bearer token, event payload, email, or desktop/device secret is stored.
 */
@Entity
@Table(name = "aetheris_owner_task_checkpoints",
       indexes = @Index(name = "idx_aetheris_checkpoint_owner_task",
                        columnList = "owner_user_id,task_id"))
public class OwnerTaskCheckpointEntity {
    @Id
    private UUID id;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private Long ownerUserId;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence;

    @Column(name = "last_event_id")
    private UUID lastEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected OwnerTaskCheckpointEntity() {}

    public OwnerTaskCheckpointEntity(UUID id, long ownerUserId, UUID taskId, Instant createdAt) {
        if (id == null || ownerUserId <= 0 || taskId == null || createdAt == null) {
            throw new IllegalArgumentException("Checkpoint requires verified account and task");
        }
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.taskId = taskId;
        this.lastSequence = 0L;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public UUID getTaskId() { return taskId; }
    public long getLastSequence() { return lastSequence; }
    public UUID getLastEventId() { return lastEventId; }

    void acknowledge(long sequence, UUID eventId, Instant updatedAt) {
        if (sequence != lastSequence + 1 || eventId == null || updatedAt == null)
            throw new IllegalArgumentException("Noncontiguous checkpoint");
        lastSequence = sequence;
        lastEventId = eventId;
        this.updatedAt = updatedAt;
    }
}
