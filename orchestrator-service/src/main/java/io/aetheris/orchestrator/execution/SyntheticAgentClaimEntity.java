package io.aetheris.orchestrator.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Durable single-use association: one job -> exactly one trusted agent.
 * Claim stays after STOP; quota is not automatically reclaimed without
 * independently verified actual workload exit.
 */
@Entity
@Table(name = "aetheris_ci_agent_claim")
public class SyntheticAgentClaimEntity {
    @Id @Column(name = "job_id", nullable = false)
    private UUID jobId;
    @Column(name = "worker_id", nullable = false, length = 80)
    private String workerId;
    @Column(name = "agent_id", nullable = false, length = 80)
    private String agentId;
    @Column(name = "owner_id", nullable = false, length = 80)
    private String ownerId;
    @Column(name = "project_id", nullable = false, length = 80)
    private String projectId;
    @Column(name = "approval_id", nullable = false, length = 80)
    private String approvalId;
    @Column(name = "stop_epoch", nullable = false)
    private long stopEpoch;
    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    protected SyntheticAgentClaimEntity() {}

    public SyntheticAgentClaimEntity(UUID jobId,String workerId,String agentId,
                                     String ownerId,String projectId,
                                     String approvalId,long stopEpoch,Instant issuedAt) {
        this.jobId=java.util.Objects.requireNonNull(jobId);
        this.workerId=workerId;
        this.agentId=agentId;
        this.ownerId=ownerId;
        this.projectId=projectId;
        this.approvalId=approvalId;
        this.stopEpoch=stopEpoch;
        this.issuedAt=issuedAt;
    }

    public UUID getJobId(){return jobId;}
    public String getAgentId(){return agentId;}
    public String getWorkerId(){return workerId;}
    public String getOwnerId(){return ownerId;}
    public String getProjectId(){return projectId;}
    public String getApprovalId(){return approvalId;}
    public long getStopEpoch(){return stopEpoch;}
}
