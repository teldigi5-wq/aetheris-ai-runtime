package io.aetheris.orchestrator.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * CI-only supervisor heartbeat evidence. Sequence is durable in synthetic DB,
 * not a production identity or heartbeat transport. No secrets or commands.
 */
@Entity
@Table(name = "aetheris_synthetic_supervisor_pulses")
public class SyntheticSupervisorPulseEntity {
    @Id
    @Column(name = "job_id", nullable = false)
    private UUID jobId;
    @Column(name = "worker_id", nullable = false, length = 80)
    private String workerId;
    @Column(name = "last_sequence", nullable = false)
    private long sequence;
    @Column(name = "last_epoch", nullable = false)
    private long epoch;
    @Column(name = "last_state", nullable = false, length = 12)
    private String state;
    @Column(name = "last_published_at", nullable = false)
    private Instant publishedAt;
    @Version
    @Column(name = "version")
    private Long version;

    protected SyntheticSupervisorPulseEntity() {}

    public SyntheticSupervisorPulseEntity(UUID jobId, String workerId) {
        this.jobId = java.util.Objects.requireNonNull(jobId);
        if (workerId == null || !workerId.matches("[A-Za-z0-9_-]{1,80}"))
            throw new IllegalArgumentException("Invalid CI pulse worker");
        this.workerId = workerId;
        this.state = "CREATED";
        this.publishedAt = Instant.EPOCH;
    }

    public long advance(String worker, long nextEpoch, String nextState, Instant now) {
        if (!workerId.equals(worker) || nextEpoch < epoch
                || (!"ACTIVE".equals(nextState) && !"STOP".equals(nextState))
                || "STOP".equals(state))
            throw new SecurityException("SUPERVISOR_PULSE_SCOPE_OR_TERMINAL_DENIED");
        if (sequence == Long.MAX_VALUE)
            throw new SecurityException("SUPERVISOR_SEQUENCE_EXHAUSTED");
        sequence++;
        epoch = nextEpoch;
        state = nextState;
        publishedAt = java.util.Objects.requireNonNull(now);
        return sequence;
    }

    public UUID getJobId() { return jobId; }
    public String getWorkerId() { return workerId; }
    public long getSequence() { return sequence; }
    public long getEpoch() { return epoch; }
    public String getState() { return state; }
}
