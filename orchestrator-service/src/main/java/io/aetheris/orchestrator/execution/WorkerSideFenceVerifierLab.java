package io.aetheris.orchestrator.execution;

import io.aetheris.orchestrator.execution.SignedSandboxJobGate.JobClaims;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.SignedJob;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.TrustedContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Worker-side independent decision check, Stage 45.5.5 LAB ONLY.
 *
 * Does not start a process, approve a task, connect to a worker or expose
 * endpoints. A future isolated worker needs to run this logic under its OWN
 * verified identity using protected DB access, never believe a controller's
 * claimed "approved" result. Still NOT a race-free launch authority:
 * a STOP can commit immediately after this short database transaction.
 */
public final class WorkerSideFenceVerifierLab {
    private final SandboxWorkerFenceRepository fences;
    private final SandboxJobReservationRepository reservations;
    private final TransactionTemplate tx;
    private final PublicKey pinnedIssuerKey;
    private final Clock clock;
    private final Function<UUID, TrustedContext> independentAuthority;

    public WorkerSideFenceVerifierLab(SandboxWorkerFenceRepository fences,
                                      SandboxJobReservationRepository reservations,
                                      PlatformTransactionManager transactionManager,
                                      PublicKey pinnedIssuerKey,
                                      Clock clock,
                                      Function<UUID, TrustedContext> independentAuthority) {
        this.fences = Objects.requireNonNull(fences, "fences");
        this.reservations = Objects.requireNonNull(reservations, "reservations");
        tx = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        tx.setTimeout(10);
        this.pinnedIssuerKey = Objects.requireNonNull(pinnedIssuerKey);
        if (!"Ed25519".equalsIgnoreCase(pinnedIssuerKey.getAlgorithm())
                && !"EdDSA".equalsIgnoreCase(pinnedIssuerKey.getAlgorithm()))
            throw new IllegalArgumentException("Ed25519 public key required");
        this.clock = Objects.requireNonNull(clock);
        this.independentAuthority = Objects.requireNonNull(independentAuthority);
    }

    /**
     * Deny unless issuer, independently sourced approval, nonce, task, owner,
     * project, worker, grant, signed expiry, durable STOP epoch and database
     * tombstone all agree. The returned result is CHECK_ONLY, not RUN access.
     */
    public CheckResult check(SignedJob signed) {
        Objects.requireNonNull(signed, "signed");
        JobClaims c = signed.claims();
        verifySignature(signed);
        return Objects.requireNonNull(tx.execute(status -> {
            var fence = fences.lockWorker(c.workerId())
                    .orElseThrow(() -> new SecurityException("WORKER_FENCE_MISSING"));
            if (fence.isStopped()) throw new SecurityException("WORKER_STOP_ENGAGED");
            var row = reservations.findById(c.jobId())
                    .orElseThrow(() -> new SecurityException("WORKER_RESERVATION_MISSING"));

            long now = Instant.now(clock).getEpochSecond();
            if (c.issuedAt() > now || c.expiresAt() <= now
                    || row.getExpiresAt().getEpochSecond() <= now)
                throw new SecurityException("WORKER_LEASE_EXPIRED");

            TrustedContext trusted = independentAuthority.apply(c.taskId());
            if (trusted == null
                    || !Objects.equals(trusted.taskId(), c.taskId())
                    || !Objects.equals(trusted.ownerId(), c.ownerId())
                    || !Objects.equals(trusted.projectId(), c.projectId())
                    || !Objects.equals(trusted.workerId(), c.workerId())
                    || !Objects.equals(trusted.approvalGrantId(), c.approvalGrantId()))
                throw new SecurityException("WORKER_APPROVAL_UNVERIFIED");

            if (row.isRevoked()
                    || row.getStopEpoch() != fence.getEpoch()
                    || !Objects.equals(row.getNonce(), c.nonce())
                    || !Objects.equals(row.getTaskId(), c.taskId())
                    || !Objects.equals(row.getWorkerId(), c.workerId())
                    || !Objects.equals(row.getOwnerId(), c.ownerId())
                    || !Objects.equals(row.getProjectId(), c.projectId())
                    || !Objects.equals(row.getApprovalId(), c.approvalGrantId())
                    || row.getExpiresAt().getEpochSecond() != c.expiresAt())
                throw new SecurityException("WORKER_FENCE_OR_SCOPE_MISMATCH");
            return new CheckResult(c.jobId(), c.workerId(), fence.getEpoch(),
                    "LAB_ONLY_CHECK_NOT_LAUNCH_AUTHORITY");
        }));
    }

    private void verifySignature(SignedJob signed) {
        byte[] transcript = SignedSandboxJobGate.canonical(signed.claims());
        byte[] signature = signed.signature();
        if (signature.length != 64) throw new SecurityException("WORKER_BAD_JOB_SIGNATURE");
        long now = Instant.now(clock).getEpochSecond();
        JobClaims c = signed.claims();
        if (c.issuedAt() > now || c.expiresAt() <= now)
            throw new SecurityException("WORKER_LEASE_EXPIRED");
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(pinnedIssuerKey);
            verifier.update(transcript);
            if (!verifier.verify(signature))
                throw new SecurityException("WORKER_BAD_JOB_SIGNATURE");
        } catch (GeneralSecurityException ex) {
            throw new SecurityException("WORKER_BAD_JOB_SIGNATURE", ex);
        } finally {
            java.util.Arrays.fill(transcript, (byte) 0);
            java.util.Arrays.fill(signature, (byte) 0);
        }
    }

    public record CheckResult(UUID jobId, String workerId, long epoch, String status) {}
}
