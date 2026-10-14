package io.aetheris.orchestrator.execution;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Stage 45.5.2 synthetic transactional STOP outbox. There is one irreversible
 * STOP per worker fence. Never stores signing keys, task data or commands.
 */
@Entity
@Table(name = "aetheris_sandbox_stop_outbox")
public class SandboxStopDispatchEntity {
    @Id @Column(name = "worker_id", nullable = false, length = 80)
    private String workerId;
    @Column(name = "stop_epoch", nullable = false)
    private long stopEpoch;
    @Column(name = "nonce", nullable = false, unique = true)
    private UUID nonce;
    @Column(name = "acknowledged", nullable = false)
    private boolean acknowledged;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;
    @Version @Column(name = "version")
    private Long version;

    protected SandboxStopDispatchEntity() {}

    public SandboxStopDispatchEntity(String workerId, long stopEpoch, UUID nonce, Instant now) {
        if (workerId == null || !workerId.matches("[A-Za-z0-9_-]{1,80}") || stopEpoch < 1)
            throw new IllegalArgumentException("Invalid durable STOP outbox scope");
        this.workerId = workerId;
        this.stopEpoch = stopEpoch;
        this.nonce = Objects.requireNonNull(nonce, "nonce");
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    public void acknowledge(Instant when) {
        if (!acknowledged) {
            acknowledged = true;
            acknowledgedAt = Objects.requireNonNull(when, "when");
        }
    }

    public String getWorkerId() { return workerId; }
    public long getStopEpoch() { return stopEpoch; }
    public UUID getNonce() { return nonce; }
    public boolean isAcknowledged() { return acknowledged; }
}
