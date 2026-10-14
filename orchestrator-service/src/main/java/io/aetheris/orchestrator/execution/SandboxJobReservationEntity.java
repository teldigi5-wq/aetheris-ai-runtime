package io.aetheris.orchestrator.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable synthetic reservation/tombstone. Retain nonce and jobId indefinitely
 * unless a separately audited retention process proves it safe to retire them.
 * Stores no ciphertext, secret keys, command arguments or user content.
 */
@Entity
@Table(name = "aetheris_sandbox_job_reservations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_sandbox_job_nonce", columnNames = "nonce")
        },
        indexes = {
                @Index(name = "idx_sandbox_job_worker", columnList = "worker_id")
        })
public class SandboxJobReservationEntity {
    @Id
    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "nonce", nullable = false, unique = true)
    private UUID nonce;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "worker_id", nullable = false, length = 80)
    private String workerId;

    @Column(name = "owner_id", nullable = false, length = 80)
    private String ownerId;

    @Column(name = "project_id", nullable = false, length = 80)
    private String projectId;

    @Column(name = "approval_id", nullable = false, length = 80)
    private String approvalId;

    @Column(name = "stop_epoch", nullable = false)
    private long stopEpoch;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    protected SandboxJobReservationEntity() {}

    public SandboxJobReservationEntity(SignedSandboxJobGate.JobClaims c, long epoch, Instant now) {
        jobId = c.jobId();
        nonce = c.nonce();
        taskId = c.taskId();
        workerId = c.workerId();
        ownerId = c.ownerId();
        projectId = c.projectId();
        approvalId = c.approvalGrantId();
        stopEpoch = epoch;
        reservedAt = now;
        expiresAt = Instant.ofEpochSecond(c.expiresAt());
    }

    public void revoke() { revoked = true; }
    public UUID getJobId() { return jobId; }
    public UUID getNonce() { return nonce; }
    public UUID getTaskId() { return taskId; }
    public String getWorkerId() { return workerId; }
    public String getOwnerId() { return ownerId; }
    public String getProjectId() { return projectId; }
    public String getApprovalId() { return approvalId; }
    public Instant getExpiresAt() { return expiresAt; }
    public long getStopEpoch() { return stopEpoch; }
    public boolean isRevoked() { return revoked; }
}
