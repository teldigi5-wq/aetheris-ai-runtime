package io.aetheris.orchestrator.execution;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Non-executing Stage 45.5.9 host-side STOP decision lab.
 *
 * An independent CI test harness may use its decision to terminate a fixed
 * synthetic OCI worker, but this class does NOT execute Docker, issue a
 * command, open an endpoint or provide agent permissions. It is not a
 * Spring bean. It does not trust worker-supplied "I'm healthy" messages.
 */
public final class SyntheticHostStopFenceLab {
    public enum Action { KEEP_SYNTHETIC, MUST_TERMINATE }

    public record Decision(Action action, String reason, String workerId, UUID jobId,
                           long expectedEpoch, long observedEpoch) {
        public boolean terminate() { return action == Action.MUST_TERMINATE; }
    }

    private final SandboxWorkerFenceRepository fences;
    private final SandboxJobReservationRepository reservations;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SyntheticHostStopFenceLab(SandboxWorkerFenceRepository fences,
                                     SandboxJobReservationRepository reservations,
                                     PlatformTransactionManager manager, Clock clock) {
        this.fences = Objects.requireNonNull(fences);
        this.reservations = Objects.requireNonNull(reservations);
        this.tx = new TransactionTemplate(Objects.requireNonNull(manager));
        this.tx.setReadOnly(true);
        this.tx.setTimeout(5);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * A trusted supervisor supplies pinned, synthetic job/worker/epoch.
     * Database failures, unknown jobs, STOP, revocation, expiry and epoch
     * mismatch all request termination. KEEP is an advisory snapshot, NEVER
     * a transferable or race-free permission to start an actual OS process.
     */
    public Decision evaluate(UUID jobId, String workerId, long expectedEpoch) {
        Objects.requireNonNull(jobId, "jobId");
        if (workerId == null || !workerId.matches("[A-Za-z0-9_-]{1,80}")
                || expectedEpoch < 1)
            throw new IllegalArgumentException("Invalid synthetic supervisor scope");
        try {
            Decision decision = tx.execute(status -> {
                var fence = fences.findById(workerId).orElse(null);
                if (fence == null) return terminate(jobId, workerId, expectedEpoch, -1,
                        "MISSING_WORKER_FENCE");
                long epoch = fence.getEpoch();
                if (fence.isStopped()) return terminate(jobId, workerId, expectedEpoch,
                        epoch, "DATABASE_STOP_LATCHED");
                if (epoch != expectedEpoch) return terminate(jobId, workerId, expectedEpoch,
                        epoch, "STALE_STOP_EPOCH");
                var reservation = reservations.findById(jobId).orElse(null);
                if (reservation == null) return terminate(jobId, workerId, expectedEpoch,
                        epoch, "MISSING_JOB_RESERVATION");
                if (!workerId.equals(reservation.getWorkerId())
                        || reservation.getStopEpoch() != expectedEpoch)
                    return terminate(jobId, workerId, expectedEpoch, epoch,
                            "RESERVATION_SCOPE_OR_EPOCH_MISMATCH");
                if (reservation.isRevoked()) return terminate(jobId, workerId, expectedEpoch,
                        epoch, "RESERVATION_REVOKED");
                if (!reservation.getExpiresAt().isAfter(Instant.now(clock)))
                    return terminate(jobId, workerId, expectedEpoch, epoch,
                            "RESERVATION_EXPIRED");
                return new Decision(Action.KEEP_SYNTHETIC, "ACTIVE_SNAPSHOT_NOT_EXECUTION_AUTH",
                        workerId, jobId, expectedEpoch, epoch);
            });
            return Objects.requireNonNull(decision, "Transaction denied");
        } catch (RuntimeException unreadable) {
            // Deliberately suppress exception messages: JDBC error strings
            // may contain endpoints, connection parameters and credentials.
            return terminate(jobId, workerId, expectedEpoch, -1,
                    "DATABASE_UNAVAILABLE_FAIL_CLOSED");
        }
    }

    private static Decision terminate(UUID job, String worker, long expected,
                                      long actual, String reason) {
        return new Decision(Action.MUST_TERMINATE, reason, worker, job, expected, actual);
    }
}
