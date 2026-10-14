package io.aetheris.orchestrator.execution;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Stage 45.5.15 NON-EXECUTING / synthetic-only independent STOP witness LAB.
 *
 * No Spring bean, scheduled task, runtime tool, command or deployment path.
 * An out-of-DB signed STOP checkpoint can reject a rolled-back DATABASE
 * snapshot during recovery. The test issuer still shares a CI host/VM with
 * the database; this is not an independently protected audit authority.
 */
public final class SyntheticStopAuditWitnessLab {
    private static final String DOMAIN = "AETHERIS45STOPAUDITV1";
    public enum Verdict {
        STOP_CONFIRMED, MISSING_WITNESS, INVALID_WITNESS,
        DATABASE_ROLLBACK_OR_REVOCATION_LOST, DATABASE_UNAVAILABLE_FAIL_CLOSED
    }
    public record Witness(String workerId, UUID jobId, long stoppedEpoch,
                          long reservedEpoch, long witnessedAt, String signatureHex) {
        public Witness {
            if (workerId == null || !workerId.matches("[A-Za-z0-9_-]{1,80}")
                    || jobId == null || stoppedEpoch < 2 || reservedEpoch < 1
                    || stoppedEpoch <= reservedEpoch || witnessedAt <= 0
                    || signatureHex == null
                    || !signatureHex.matches("[0-9a-f]{128}"))
                throw new IllegalArgumentException("Invalid synthetic STOP checkpoint");
        }
    }
    public record Decision(Verdict verdict, boolean mustTerminate) {}

    private final SandboxWorkerFenceRepository fences;
    private final SandboxJobReservationRepository reservations;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SyntheticStopAuditWitnessLab(SandboxWorkerFenceRepository fences,
                                        SandboxJobReservationRepository reservations,
                                        PlatformTransactionManager manager, Clock clock) {
        this.fences = Objects.requireNonNull(fences);
        this.reservations = Objects.requireNonNull(reservations);
        this.tx = new TransactionTemplate(Objects.requireNonNull(manager));
        this.tx.setReadOnly(true);
        this.tx.setTimeout(5);
        this.clock = Objects.requireNonNull(clock);
    }

    public Witness attestAfterCommittedStop(String worker, UUID job, PrivateKey issuer) {
        requireScope(worker, job);
        Objects.requireNonNull(issuer);
        long[] epochs = Objects.requireNonNull(tx.execute(status -> {
            var fence = fences.findById(worker)
                    .orElseThrow(() -> new SecurityException("AUDIT_STOP_FENCE_MISSING"));
            var reservation = reservations.findById(job)
                    .orElseThrow(() -> new SecurityException("AUDIT_RESERVATION_MISSING"));
            if (!fence.isStopped() || !reservation.isRevoked()
                    || !worker.equals(reservation.getWorkerId())
                    || fence.getEpoch() <= reservation.getStopEpoch())
                throw new SecurityException("AUDIT_STOP_NOT_DURABLY_COMMITTED");
            return new long[]{fence.getEpoch(), reservation.getStopEpoch()};
        }));
        long when = Instant.now(clock).getEpochSecond();
        byte[] payload = canonical(worker, job, epochs[0], epochs[1], when);
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(issuer);
            signature.update(payload);
            return new Witness(worker, job, epochs[0], epochs[1], when,
                    HexFormat.of().formatHex(signature.sign()));
        } catch (GeneralSecurityException e) {
            throw new SecurityException("AUDIT_WITNESS_SIGNATURE_FAILED");
        } finally {
            java.util.Arrays.fill(payload, (byte) 0);
        }
    }

    /** Every outcome requires termination; this read-only recovery check
     * NEVER grants new execution or re-arms a worker. */
    public Decision verifyRecovery(Witness witnessed, PublicKey pinnedIssuer,
                                   String worker, UUID job) {
        requireScope(worker, job);
        if (witnessed == null) return deny(Verdict.MISSING_WITNESS);
        if (pinnedIssuer == null || !worker.equals(witnessed.workerId())
                || !job.equals(witnessed.jobId())
                || witnessed.witnessedAt() > Instant.now(clock).getEpochSecond() + 1
                || !verifySignature(witnessed, pinnedIssuer))
            return deny(Verdict.INVALID_WITNESS);
        try {
            Verdict verdict = tx.execute(status -> {
                var fence = fences.findById(worker).orElse(null);
                var reservation = reservations.findById(job).orElse(null);
                if (fence == null || reservation == null
                        || !worker.equals(reservation.getWorkerId())
                        || !fence.isStopped() || !reservation.isRevoked()
                        || fence.getEpoch() < witnessed.stoppedEpoch()
                        || reservation.getStopEpoch() != witnessed.reservedEpoch()
                        || fence.getEpoch() <= reservation.getStopEpoch())
                    return Verdict.DATABASE_ROLLBACK_OR_REVOCATION_LOST;
                return Verdict.STOP_CONFIRMED;
            });
            return deny(Objects.requireNonNull(verdict));
        } catch (RuntimeException exception) {
            // Never log DB/JDBC exception text: it can contain credentials.
            return deny(Verdict.DATABASE_UNAVAILABLE_FAIL_CLOSED);
        }
    }

    private static boolean verifySignature(Witness witness, PublicKey key) {
        byte[] transcript = canonical(witness.workerId(), witness.jobId(),
                witness.stoppedEpoch(), witness.reservedEpoch(), witness.witnessedAt());
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(transcript);
            return verifier.verify(HexFormat.of().parseHex(witness.signatureHex()));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        } finally {
            java.util.Arrays.fill(transcript, (byte) 0);
        }
    }

    public static String canonicalText(Witness witness) {
        return new String(canonical(witness.workerId(), witness.jobId(),
                witness.stoppedEpoch(), witness.reservedEpoch(), witness.witnessedAt()),
                StandardCharsets.US_ASCII) + witness.signatureHex() + "\n";
    }

    public static Witness parseCanonicalText(String data) {
        if (data == null || data.length() > 1024 || !data.endsWith("\n"))
            throw new IllegalArgumentException("Invalid audit framing");
        String[] parts = data.split("\\|", -1);
        if (parts.length != 7 || !DOMAIN.equals(parts[0]))
            throw new IllegalArgumentException("Invalid audit protocol");
        try {
            Witness result = new Witness(parts[1], UUID.fromString(parts[2]),
                    Long.parseLong(parts[3]), Long.parseLong(parts[4]),
                    Long.parseLong(parts[5]), parts[6].substring(0, parts[6].length() - 1));
            if (!data.equals(canonicalText(result)))
                throw new IllegalArgumentException("Noncanonical audit checkpoint");
            return result;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Invalid audit checkpoint", failure);
        }
    }

    private static byte[] canonical(String worker, UUID job, long epoch, long reserved, long time) {
        return (DOMAIN + "|" + worker + "|" + job + "|" + epoch + "|" + reserved
                + "|" + time + "\n").getBytes(StandardCharsets.US_ASCII);
    }
    private static Decision deny(Verdict verdict) { return new Decision(verdict, true); }
    private static void requireScope(String worker, UUID job) {
        if (worker == null || !worker.matches("[A-Za-z0-9_-]{1,80}") || job == null)
            throw new IllegalArgumentException("Invalid audit scope");
    }
}
