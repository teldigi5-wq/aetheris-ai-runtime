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

    /**
     * Trusted owner binding for future authenticated APIs. Legacy and
     * internally-created tasks remain unowned and must never be exposed
     * to an arbitrary caller by guessing a UUID or setting a header.
     */
    @Column(name = "owner_user_id", updatable = false)
    private Long ownerUserId;

    @Column(name = "owner_project_id", updatable = false)
    private UUID ownerProjectId;

    // Nullable for legacy tasks. Incremented only while the task row is
    // PESSIMISTIC_WRITE locked by the same DB transaction as journal+outbox.
    @Column(name = "last_journal_sequence")
    private Long lastJournalSequence;

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

    // Not wired to any HTTP request. Only a future *verified server-side*
    // principal resolver may call a trusted owning creation path.
    TaskEntity(UUID id, String title, String commandText, OperationMode mode,
               Long trustedOwnerUserId, UUID trustedProjectId) {
        this(id, title, commandText, mode);
        if (trustedOwnerUserId == null) {
            throw new IllegalArgumentException("Verified task owner is required");
        }
        this.ownerUserId = trustedOwnerUserId;
        this.ownerProjectId = trustedProjectId;
    }

    public Long getOwnerUserId() { return ownerUserId; }
    public UUID getOwnerProjectId() { return ownerProjectId; }

    public Long getLastJournalSequence() { return lastJournalSequence; }

    long allocateCommittedJournalSequence() {
        long prior = lastJournalSequence == null ? 0L : lastJournalSequence;
        if (prior == Long.MAX_VALUE) throw new IllegalStateException("JOURNAL_SEQUENCE_EXHAUSTED");
        lastJournalSequence = prior + 1L;
        return lastJournalSequence;
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
