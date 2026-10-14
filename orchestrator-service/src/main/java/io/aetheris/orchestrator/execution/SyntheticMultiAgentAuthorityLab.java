package io.aetheris.orchestrator.execution;

import io.aetheris.orchestrator.execution.SignedSandboxJobGate.JobClaims;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.SignedJob;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.TrustedContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Stage 45.5.16 NON-EXECUTING synthetic multi-agent authorization LAB.
 * Not a Spring bean, HTTP controller, runtime tool, Docker interface,
 * TaskControlService listener or real execution authority.
 *
 * Only fixed SYNTHETIC_SLEEP claims, already signed and durably reserved
 * by the earlier labs, can be accounted for. Agent identity is resolved
 * independently by task ID, NEVER from the caller's claimed agent field.
 * Both worker fence and owner/project budget are row-locked in one DB
 * transaction, so STOP and competing budget claims cannot silently race
 * inside the single disposable DB. Active jobs remain charged until a
 * separately proven exit protocol is implemented and reviewed.
 */
public final class SyntheticMultiAgentAuthorityLab {
    public record AgentGrant(UUID taskId, String agentId, String ownerId,
                             String projectId, String workerId,
                             String approvalGrantId) {
        public AgentGrant {
            Objects.requireNonNull(taskId);
            requireId(agentId);
            requireId(ownerId);
            requireId(projectId);
            requireId(workerId);
            requireId(approvalGrantId);
        }
        public TrustedContext trusted() {
            return new TrustedContext(taskId,ownerId,projectId,workerId,approvalGrantId);
        }
    }

    public record Decision(boolean mustTerminate, String reason) {}
    private final SyntheticAgentQuotaRepository quotas;
    private final SyntheticAgentClaimRepository claims;
    private final SandboxWorkerFenceRepository fences;
    private final SandboxJobReservationRepository reservations;
    private final WorkerSideFenceVerifierLab verifier;
    private final Function<UUID,AgentGrant> authoritativeAgentByTask;
    private final BooleanSupplier emergencyStop;
    private final Clock clock;
    private final TransactionTemplate tx;

    public SyntheticMultiAgentAuthorityLab(
            SyntheticAgentQuotaRepository quotas,
            SyntheticAgentClaimRepository claims,
            SandboxWorkerFenceRepository fences,
            SandboxJobReservationRepository reservations,
            WorkerSideFenceVerifierLab verifier,
            PlatformTransactionManager transactions, Clock clock,
            Function<UUID,AgentGrant> authoritativeAgentByTask,
            BooleanSupplier emergencyStop) {
        this.quotas=Objects.requireNonNull(quotas);
        this.claims=Objects.requireNonNull(claims);
        this.fences=Objects.requireNonNull(fences);
        this.reservations=Objects.requireNonNull(reservations);
        this.verifier=Objects.requireNonNull(verifier);
        this.clock=Objects.requireNonNull(clock);
        this.authoritativeAgentByTask=Objects.requireNonNull(authoritativeAgentByTask);
        this.emergencyStop=Objects.requireNonNull(emergencyStop);
        this.tx=new TransactionTemplate(Objects.requireNonNull(transactions));
        this.tx.setTimeout(10);
    }

    /**
     * This is QUOTA RESERVATION ONLY, not executable launch admission.
     * Claiming another agent ID or using another owner's grant fails even
     * if the caller has a syntactically valid signed job from elsewhere.
     */
    public UUID reserveForAgent(SignedJob signed,String requestedAgentId) {
        Objects.requireNonNull(signed);
        requireId(requestedAgentId);
        if (stopped()) throw new SecurityException("MULTIAGENT_EMERGENCY_STOP");
        var checked=verifier.check(signed); // issuer signature + independent task/owner/grant + durable fence
        JobClaims c=signed.claims();
        if (!checked.jobId().equals(c.jobId()) || !checked.workerId().equals(c.workerId()))
            throw new SecurityException("MULTIAGENT_JOB_CHECK_MISMATCH");
        return Objects.requireNonNull(tx.execute(status -> {
            if (stopped()) throw new SecurityException("MULTIAGENT_EMERGENCY_STOP");
            var fence=fences.lockWorker(c.workerId())
                    .orElseThrow(() -> new SecurityException("MULTIAGENT_FENCE_MISSING"));
            if (fence.isStopped() || fence.getEpoch()!=checked.epoch())
                throw new SecurityException("MULTIAGENT_WORKER_STOP");
            AgentGrant grant=authoritativeAgentByTask.apply(c.taskId());
            if (!matches(grant,c) || !requestedAgentId.equals(grant.agentId()))
                throw new SecurityException("MULTIAGENT_AGENT_OR_OWNER_SCOPE_DENIED");
            var reservation=reservations.findById(c.jobId())
                    .orElseThrow(() -> new SecurityException("MULTIAGENT_JOB_NOT_RESERVED"));
            if (reservation.isRevoked() || !reservation.getWorkerId().equals(c.workerId())
                    || !reservation.getOwnerId().equals(c.ownerId())
                    || !reservation.getProjectId().equals(c.projectId())
                    || !reservation.getApprovalId().equals(c.approvalGrantId())
                    || !reservation.getTaskId().equals(c.taskId())
                    || !reservation.getNonce().equals(c.nonce())
                    || reservation.getStopEpoch()!=fence.getEpoch()
                    || !reservation.getExpiresAt().isAfter(Instant.now(clock)))
                throw new SecurityException("MULTIAGENT_DURABLE_SCOPE_OR_STOP_DENIED");
            var budget=quotas.lockScope(SyntheticAgentQuotaEntity.key(c.ownerId(),c.projectId()))
                    .orElseThrow(() -> new SecurityException("MULTIAGENT_MISSING_BUDGET"));
            if (!c.ownerId().equals(budget.getOwnerId())
                    || !c.projectId().equals(budget.getProjectId()))
                throw new SecurityException("MULTIAGENT_CROSS_OWNER_BUDGET_DENIED");
            if (claims.existsById(c.jobId()))
                throw new SecurityException("MULTIAGENT_SINGLE_USE_CLAIM");
            budget.claim(c.cpuMilli(),c.memoryMiB(),c.processLimit());
            quotas.saveAndFlush(budget);
            claims.saveAndFlush(new SyntheticAgentClaimEntity(
                    c.jobId(),c.workerId(),grant.agentId(),c.ownerId(),
                    c.projectId(),c.approvalGrantId(),fence.getEpoch(),Instant.now(clock)));
            return c.jobId();
        }));
    }

    /** Permanent scope-level STOP in the synthetic quota ledger, even
     * if a caller later releases a volatile emergency flag. Does NOT kill
     * OS processes; an independently authenticated host broker is required.
     */
    public void latchOwnerProjectStop(String owner,String project) {
        String key=SyntheticAgentQuotaEntity.key(owner,project);
        Objects.requireNonNull(tx.execute(status -> {
            var quota=quotas.lockScope(key)
                    .orElseThrow(() -> new SecurityException("MULTIAGENT_MISSING_BUDGET"));
            quota.latchStop();
            quotas.saveAndFlush(quota);
            return Boolean.TRUE;
        }));
    }

    /** Read-only reconciliation, never a transferable RUN grant.
     * All observed STOP, lost approval, DB outage, missing row or expired
     * reservations demand termination for the separately trusted broker.
     */
    public Decision inspectExisting(UUID jobId) {
        if (jobId==null) return deny("MULTIAGENT_UNKNOWN_JOB");
        if (stoppedOrUnknown()) return deny("MULTIAGENT_EMERGENCY_OR_UNKNOWN_STOP");
        try {
            return Objects.requireNonNull(tx.execute(status -> {
                var claim=claims.findById(jobId).orElse(null);
                if (claim==null) return deny("MULTIAGENT_CLAIM_MISSING");
                var quota=quotas.findById(
                        SyntheticAgentQuotaEntity.key(claim.getOwnerId(),claim.getProjectId()))
                        .orElse(null);
                if (quota==null || quota.isStopped())
                    return deny("MULTIAGENT_SCOPE_STOP");
                var fence=fences.findById(claim.getWorkerId()).orElse(null);
                var reservation=reservations.findById(jobId).orElse(null);
                if (fence==null || fence.isStopped()
                        || fence.getEpoch()!=claim.getStopEpoch()
                        || reservation==null || reservation.isRevoked()
                        || !claim.getWorkerId().equals(reservation.getWorkerId())
                        || !claim.getOwnerId().equals(reservation.getOwnerId())
                        || !claim.getProjectId().equals(reservation.getProjectId())
                        || !claim.getApprovalId().equals(reservation.getApprovalId())
                        || reservation.getStopEpoch()!=claim.getStopEpoch()
                        || !reservation.getExpiresAt().isAfter(Instant.now(clock)))
                    return deny("MULTIAGENT_DURABLE_STOP_OR_EXPIRED");
                AgentGrant grant=authoritativeAgentByTask.apply(reservation.getTaskId());
                if (grant==null || !claim.getAgentId().equals(grant.agentId())
                        || !reservation.getTaskId().equals(grant.taskId())
                        || !claim.getOwnerId().equals(grant.ownerId())
                        || !claim.getProjectId().equals(grant.projectId())
                        || !claim.getWorkerId().equals(grant.workerId())
                        || !claim.getApprovalId().equals(grant.approvalGrantId()))
                    return deny("MULTIAGENT_REVOKED_AGENT_GRANT");
                return new Decision(false,"CHECK_ONLY_NOT_LAUNCH_AUTHORITY");
            }));
        } catch (RuntimeException unavailable) {
            return deny("MULTIAGENT_AUTHORITY_OR_DB_UNAVAILABLE");
        }
    }

    private boolean stopped() {
        try{return emergencyStop.getAsBoolean();}
        catch (RuntimeException missing) {
            throw new SecurityException("MULTIAGENT_EMERGENCY_SIGNAL_UNAVAILABLE");
        }
    }
    private boolean stoppedOrUnknown() {
        try {return emergencyStop.getAsBoolean();}
        catch (RuntimeException missing) {return true;}
    }
    private static boolean matches(AgentGrant grant,JobClaims c) {
        return grant!=null && grant.taskId().equals(c.taskId())
                && grant.ownerId().equals(c.ownerId())
                && grant.projectId().equals(c.projectId())
                && grant.workerId().equals(c.workerId())
                && grant.approvalGrantId().equals(c.approvalGrantId());
    }
    private static Decision deny(String why){return new Decision(true,why);}
    private static void requireId(String id) {
        if(id==null||!id.matches("[A-Za-z0-9_-]{1,80}"))
            throw new IllegalArgumentException("Invalid synthetic agent identifier");
    }
}
