package io.aetheris.orchestrator.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Only durable IDs and retry metadata. Never persist event messages, owner
 * tokens, commandText, tool outputs, credentials or event metadata here.
 */
@Entity
@Table(name = "aetheris_task_journal_outbox",
       indexes = @Index(name = "idx_aetheris_outbox_ready", columnList = "state,next_attempt_at,lease_until"),
       uniqueConstraints = @UniqueConstraint(name = "uq_aetheris_outbox_journal", columnNames = "journal_event_id"))
public class TaskJournalOutboxEntity {
    public enum State { PENDING, IN_FLIGHT, HANDOFF_ATTEMPTED, PARKED }

    @Id
    private UUID id;

    @Column(name = "journal_event_id", nullable = false, updatable = false)
    private UUID journalEventId;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private State state;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "lease_token")
    private UUID leaseToken;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Version
    private long version;

    protected TaskJournalOutboxEntity() {}

    public TaskJournalOutboxEntity(UUID id, UUID journalEventId, UUID taskId, Instant createdAt) {
        if (id == null || journalEventId == null || taskId == null || createdAt == null)
            throw new IllegalArgumentException("Outbox identity fields required");
        this.id = id;
        this.journalEventId = journalEventId;
        this.taskId = taskId;
        this.createdAt = createdAt;
        this.nextAttemptAt = createdAt;
        this.state = State.PENDING;
    }

    public UUID getId() { return id; }
    public UUID getJournalEventId() { return journalEventId; }
    public UUID getTaskId() { return taskId; }
    public State getState() { return state; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public UUID getLeaseToken() { return leaseToken; }
    public int getAttempts() { return attempts; }

    void park() {
        state = State.PARKED;
        leaseUntil = null;
        leaseToken = null;
    }

    void claim(Instant now, Instant deadline, UUID nonce) {
        state = State.IN_FLIGHT;
        leaseToken = nonce;
        leaseUntil = deadline;
        attempts++;
    }

    boolean ownsLease(UUID nonce, Instant now) {
        return nonce != null && nonce.equals(leaseToken) &&
                state == State.IN_FLIGHT && leaseUntil != null &&
                now.isBefore(leaseUntil);
    }

    void handoffAttempted() {
        state = State.HANDOFF_ATTEMPTED;
        leaseToken = null;
        leaseUntil = null;
    }

    void scheduleRetry(Instant next) {
        state = State.PENDING;
        nextAttemptAt = next;
        leaseToken = null;
        leaseUntil = null;
    }
}
