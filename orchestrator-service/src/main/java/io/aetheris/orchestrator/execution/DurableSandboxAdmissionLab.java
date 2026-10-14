package io.aetheris.orchestrator.execution;

import io.aetheris.orchestrator.execution.SignedSandboxJobGate.JobClaims;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.SignedJob;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.TrustedContext;
import org.springframework.dao.DataIntegrityViolationException;
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
 * Stage 45.4 NON-EXECUTING durability lab, constructed explicitly only by
 * synthetic tests. Not a Spring service, tool adapter, controller or worker.
 * No production issuer or identity is available here.
 *
 * The worker fence row MUST have been created by a trusted offline procedure;
 * absence of that row always denies. New fence rows start STOPPED. No rearm
 * method exists in this class. Successful reservation is NOT authorization
 * to launch an OS process: a separately verified worker/STOP broker is absent.
 */
public final class DurableSandboxAdmissionLab {
    private final SandboxWorkerFenceRepository fences;
    private final SandboxJobReservationRepository reservations;
    private final PublicKey issuer;
    private final Clock clock;
    private final Function<UUID, TrustedContext> authoritativeContext;
    private final TransactionTemplate tx;

    public DurableSandboxAdmissionLab(SandboxWorkerFenceRepository fences,
                                      SandboxJobReservationRepository reservations,
                                      PlatformTransactionManager transactionManager,
                                      PublicKey issuer, Clock clock,
                                      Function<UUID, TrustedContext> authoritativeContext) {
        this.fences = Objects.requireNonNull(fences, "fences");
        this.reservations = Objects.requireNonNull(reservations, "reservations");
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        if (!"EdDSA".equalsIgnoreCase(issuer.getAlgorithm())
                && !"Ed25519".equalsIgnoreCase(issuer.getAlgorithm()))
            throw new IllegalArgumentException("Ed25519 issuer public key required");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.authoritativeContext = Objects.requireNonNull(authoritativeContext, "authoritativeContext");
        tx = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        tx.setTimeout(10);
    }

    /**
     * Ed25519 signature checked before DB access, then transactional worker
     * row lock serializes reservation with STOP. Unique DB constraints on
     * nonce and job ID reject replays across JVM instances and worker IDs.
     */
    public UUID reserve(SignedJob signed) {
        Objects.requireNonNull(signed, "signed");
        JobClaims c = signed.claims();
        validateSignature(signed);
        try {
            return Objects.requireNonNull(tx.execute(status -> {
                SandboxWorkerFenceEntity fence = fences.lockWorker(c.workerId())
                        .orElseThrow(() -> new SecurityException("SANDBOX_FENCE_MISSING"));
                if (fence.isStopped()) throw new SecurityException("SANDBOX_STOP_LATCHED");
                long now = Instant.now(clock).getEpochSecond();
                if (c.issuedAt() > now || c.expiresAt() <= now)
                    throw new SecurityException("SANDBOX_LEASE_EXPIRED");
                TrustedContext trusted = authoritativeContext.apply(c.taskId());
                if (trusted == null || !Objects.equals(trusted.taskId(), c.taskId())
                        || !Objects.equals(trusted.ownerId(), c.ownerId())
                        || !Objects.equals(trusted.projectId(), c.projectId())
                        || !Objects.equals(trusted.workerId(), c.workerId())
                        || !Objects.equals(trusted.approvalGrantId(), c.approvalGrantId()))
                    throw new SecurityException("SANDBOX_OWNER_SCOPE_DENIED");
                if (reservations.existsById(c.jobId()) || reservations.existsByNonce(c.nonce()))
                    throw new SecurityException("SANDBOX_REPLAY_DENIED");
                reservations.saveAndFlush(new SandboxJobReservationEntity(
                        c, fence.getEpoch(), Instant.now(clock)));
                return c.jobId();
            }));
        } catch (DataIntegrityViolationException collision) {
            throw new SecurityException("SANDBOX_REPLAY_OR_LEDGER_INTEGRITY_DENIED");
        }
    }

    /**
     * Atomic persistence fence and all-reservations revocation. Does NOT
     * claim that external processes were killed; there is no process backend.
     */
    public StopReceipt stopWorker(String workerId) {
        requireId(workerId);
        return Objects.requireNonNull(tx.execute(status -> {
            SandboxWorkerFenceEntity fence = fences.lockWorker(workerId)
                    .orElseThrow(() -> new SecurityException("SANDBOX_FENCE_MISSING"));
            fence.latchStop();
            fences.saveAndFlush(fence);
            int revoked = reservations.revokeWorkerReservations(workerId);
            return new StopReceipt(workerId, fence.getEpoch(), revoked,
                    "STOP_PERSISTED_OS_TERMINATION_UNVERIFIED");
        }));
    }

    /** Read-only status hint, NOT a race-free check permitting execution. */
    public boolean reservationVisible(UUID jobId) {
        if (jobId == null) return false;
        return Boolean.TRUE.equals(tx.execute(status ->
                reservations.findById(jobId).map(reservation ->
                        !reservation.isRevoked() &&
                                fences.findById(reservation.getWorkerId())
                                        .map(f -> !f.isStopped()
                                                && f.getEpoch() == reservation.getStopEpoch())
                                        .orElse(false)).orElse(false)));
    }

    private void validateSignature(SignedJob signed) {
        JobClaims c = signed.claims();
        byte[] transcript = SignedSandboxJobGate.canonical(c);
        byte[] signatureBytes = signed.signature();
        if (signatureBytes.length != 64) throw new SecurityException("SANDBOX_SIGNATURE_DENIED");
        long now = Instant.now(clock).getEpochSecond();
        if (c.issuedAt() > now || c.expiresAt() <= now)
            throw new SecurityException("SANDBOX_LEASE_EXPIRED");
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(issuer);
            verifier.update(transcript);
            if (!verifier.verify(signatureBytes))
                throw new SecurityException("SANDBOX_SIGNATURE_DENIED");
        } catch (GeneralSecurityException ex) {
            throw new SecurityException("SANDBOX_SIGNATURE_DENIED");
        } finally {
            java.util.Arrays.fill(signatureBytes, (byte) 0);
            java.util.Arrays.fill(transcript, (byte) 0);
        }
    }

    private static void requireId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,80}"))
            throw new IllegalArgumentException("Invalid worker ID");
    }

    public record StopReceipt(String workerId, long epoch, int revokedRows, String status) {}
}
