package io.aetheris.orchestrator.execution;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Test-created, non-Spring-component, fixed synthetic heartbeat issuer.
 * Validates persistent DB STOP/reservation state before each signed pulse.
 * Never launches a process, authorizes agent commands or exports keys.
 */
public final class SyntheticSupervisorPulseLab {
    public record Pulse(UUID jobId, String workerId, String containerId, long epoch,
                        long sequence, long issuedAt, long expiresAt, String state) {
        public Pulse {
            Objects.requireNonNull(jobId);
            if (workerId == null || !workerId.matches("[A-Za-z0-9_-]{1,80}")
                    || containerId == null || !containerId.matches("[0-9a-f]{64}")
                    || epoch < 1 || sequence < 1
                    || issuedAt < 1 || expiresAt <= issuedAt
                    || expiresAt - issuedAt > 4
                    || (!"ACTIVE".equals(state) && !"STOP".equals(state)))
                throw new IllegalArgumentException("Invalid synthetic supervisor pulse");
        }
    }

    public static final String DOMAIN = "AETHERIS45HOSTWATCH1";

    private final SandboxWorkerFenceRepository fences;
    private final SandboxJobReservationRepository reservations;
    private final SyntheticSupervisorPulseRepository pulses;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final PrivateKey signingKey;

    public SyntheticSupervisorPulseLab(SandboxWorkerFenceRepository fences,
                                       SandboxJobReservationRepository reservations,
                                       SyntheticSupervisorPulseRepository pulses,
                                       PlatformTransactionManager transactions,
                                       Clock clock, PrivateKey signingKey) {
        this.fences = Objects.requireNonNull(fences);
        this.reservations = Objects.requireNonNull(reservations);
        this.pulses = Objects.requireNonNull(pulses);
        tx = new TransactionTemplate(Objects.requireNonNull(transactions));
        tx.setTimeout(8);
        this.clock = Objects.requireNonNull(clock);
        this.signingKey = Objects.requireNonNull(signingKey);
    }

    /**
     * Stop must be durable before STOP pulse. ACTIVE requires a current
     * unrevoked job and matching DB epoch under the per-worker write lock.
     * Pulse is persisted in a separate DB transaction before it is signed,
     * so a crash after commit simply leads to watcher timeout (fail closed).
     */
    public String publish(UUID jobId, String worker, String containerId,
                          long expectedEpoch, String state) {
        Pulse pulse = Objects.requireNonNull(tx.execute(t -> {
            var fence = fences.lockWorker(worker)
                    .orElseThrow(() -> new SecurityException("HOST_WATCH_FENCE_MISSING"));
            var reservation = reservations.findById(jobId)
                    .orElseThrow(() -> new SecurityException("HOST_WATCH_JOB_MISSING"));
            if (!worker.equals(reservation.getWorkerId())
                    || fence.getEpoch() != expectedEpoch)
                throw new SecurityException("HOST_WATCH_SCOPE_OR_EPOCH_DENIED");
            if ("ACTIVE".equals(state)) {
                if (fence.isStopped() || reservation.isRevoked()
                        || reservation.getStopEpoch() != expectedEpoch
                        || !reservation.getExpiresAt().isAfter(Instant.now(clock)))
                    throw new SecurityException("HOST_WATCH_ACTIVE_DENIED");
            } else if ("STOP".equals(state)) {
                if (!fence.isStopped() || !reservation.isRevoked()
                        || expectedEpoch <= reservation.getStopEpoch())
                    throw new SecurityException("HOST_WATCH_UNCOMMITTED_STOP");
            } else throw new SecurityException("HOST_WATCH_UNKNOWN_STATE");

            var row = pulses.lockPulse(jobId).orElse(null);
            if (row == null) row = new SyntheticSupervisorPulseEntity(jobId, worker);
            long seq = row.advance(worker, expectedEpoch, state, Instant.now(clock));
            pulses.saveAndFlush(row);
            long issued = Instant.now(clock).getEpochSecond();
            return new Pulse(jobId, worker, containerId, expectedEpoch, seq,
                    issued, issued + 3, state);
        }));
        String payload = DOMAIN + "|" + pulse.workerId() + "|" + pulse.jobId()
                + "|" + pulse.containerId() + "|" + pulse.epoch() + "|"
                + pulse.sequence() + "|" + pulse.issuedAt() + "|"
                + pulse.expiresAt() + "|" + pulse.state() + "\n";
        try {
            var signer = Signature.getInstance("Ed25519");
            signer.initSign(signingKey);
            signer.update(payload.getBytes(StandardCharsets.US_ASCII));
            return payload + HexFormat.of().formatHex(signer.sign()) + "\n";
        } catch (GeneralSecurityException ex) {
            throw new SecurityException("HOST_WATCH_SIGNING_FAILED", ex);
        }
    }
}
