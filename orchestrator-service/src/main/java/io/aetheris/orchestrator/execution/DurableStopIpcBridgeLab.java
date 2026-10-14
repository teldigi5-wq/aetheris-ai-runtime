package io.aetheris.orchestrator.execution;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Stage 45.5.2 TEST-ONLY integration: persist STOP before attempting IPC;
 * authenticate the worker-signed reply, then persist its limited receipt.
 *
 * Not a Spring component, endpoint, task executor, STOP scheduler or
 * production service. The worker executes NO TASKS: an acknowledgement
 * proves only its local synthetic STOP marker, never process-tree exit.
 */
public final class DurableStopIpcBridgeLab {
    @FunctionalInterface
    public interface StopTransport {
        SandboxWorkerStopWire.SignedFrame exchange(SandboxWorkerStopWire.SignedFrame signed)
                throws IOException;
    }

    public record Status(String workerId, long epoch, String state) {}

    private final DurableSandboxAdmissionLab ledger;
    private final SandboxWorkerFenceRepository fences;
    private final SandboxStopDispatchRepository outbox;
    private final TransactionTemplate tx;
    private final PrivateKey controllerSigner;
    private final PublicKey pinnedWorker;
    private final Clock clock;
    private final StopTransport transport;

    public DurableStopIpcBridgeLab(DurableSandboxAdmissionLab ledger,
                                   SandboxWorkerFenceRepository fences,
                                   SandboxStopDispatchRepository outbox,
                                   PlatformTransactionManager transactions,
                                   PrivateKey controllerSigner,
                                   PublicKey pinnedWorker,
                                   Clock clock,
                                   StopTransport transport) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.fences = Objects.requireNonNull(fences, "fences");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.tx = new TransactionTemplate(Objects.requireNonNull(transactions, "transactions"));
        this.tx.setTimeout(10);
        this.controllerSigner = Objects.requireNonNull(controllerSigner, "controllerSigner");
        this.pinnedWorker = Objects.requireNonNull(pinnedWorker, "pinnedWorker");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    /** Persist irreversible STOP and revoke reservations, then attempt IPC. */
    public Status stopAndDeliver(String workerId) {
        // Exception means the STOP may NOT have committed. Never claim success.
        ledger.stopWorker(workerId);
        return retryDelivery(workerId);
    }

    /**
     * Safe across a fresh coordinator instance sharing the same DB.
     * Reconstructs a missing outbox row after a crash between DB STOP and
     * outbox insertion. Never arms a stopped worker.
     */
    public Status retryDelivery(String workerId) {
        SandboxStopDispatchEntity pending = Objects.requireNonNull(tx.execute(status -> {
            SandboxWorkerFenceEntity fence = fences.lockWorker(workerId)
                    .orElseThrow(() -> new SecurityException("SANDBOX_FENCE_MISSING"));
            if (!fence.isStopped())
                throw new SecurityException("SANDBOX_STOP_NOT_PERSISTED");
            SandboxStopDispatchEntity existing = outbox.findById(workerId).orElse(null);
            if (existing != null) {
                if (existing.getStopEpoch() != fence.getEpoch())
                    throw new SecurityException("SANDBOX_STOP_EPOCH_CONFLICT");
                return existing;
            }
            return outbox.saveAndFlush(new SandboxStopDispatchEntity(
                    workerId, fence.getEpoch(), UUID.randomUUID(), Instant.now(clock)));
        }));

        if (pending.isAcknowledged())
            return new Status(workerId, pending.getStopEpoch(),
                    "SIGNED_WORKER_MARKER_ACKNOWLEDGED_NO_OS_EXIT");

        var issued = Instant.now(clock).getEpochSecond();
        var request = new SandboxWorkerStopWire.StopRequest(
                pending.getNonce(), workerId, issued - 1, issued + 15, pending.getStopEpoch());
        SandboxWorkerStopWire.SignedFrame reply;
        try {
            var signed = SandboxWorkerStopWire.sign(
                    SandboxWorkerStopWire.requestBytes(request), controllerSigner);
            reply = transport.exchange(signed);
            if (!SandboxWorkerStopWire.verified(reply, pinnedWorker))
                return new Status(workerId, pending.getStopEpoch(),
                        "STOP_PERSISTED_ACK_UNVERIFIED");
            var receipt = SandboxWorkerStopWire.parseReceipt(reply.message());
            if (!receipt.nonce().equals(pending.getNonce())
                    || !receipt.workerId().equals(workerId)
                    || receipt.epoch() != pending.getStopEpoch())
                return new Status(workerId, pending.getStopEpoch(),
                        "STOP_PERSISTED_ACK_UNVERIFIED");
        } catch (IOException | SecurityException rejected) {
            // A connection failure or forged response NEVER undoes DB STOP.
            return new Status(workerId, pending.getStopEpoch(),
                    "STOP_PERSISTED_DELIVERY_PENDING");
        }

        // Independent commit occurs only AFTER cryptographic verification.
        // Re-lock same worker fence to reject epoch changes while persisting.
        return Objects.requireNonNull(tx.execute(status -> {
            SandboxWorkerFenceEntity fence = fences.lockWorker(workerId)
                    .orElseThrow(() -> new SecurityException("SANDBOX_FENCE_MISSING"));
            if (!fence.isStopped() || fence.getEpoch() != pending.getStopEpoch())
                throw new SecurityException("SANDBOX_STOP_EPOCH_CONFLICT");
            SandboxStopDispatchEntity current = outbox.findById(workerId)
                    .orElseThrow(() -> new SecurityException("SANDBOX_OUTBOX_MISSING"));
            if (!current.getNonce().equals(pending.getNonce())
                    || current.getStopEpoch() != pending.getStopEpoch())
                throw new SecurityException("SANDBOX_STOP_OUTBOX_MISMATCH");
            current.acknowledge(Instant.now(clock));
            outbox.saveAndFlush(current);
            return new Status(workerId, current.getStopEpoch(),
                    "SIGNED_WORKER_MARKER_ACKNOWLEDGED_NO_OS_EXIT");
        }));
    }

    /** Read-only hint: no claim about an actual host process. */
    public Status snapshot(String workerId) {
        return Objects.requireNonNull(tx.execute(status -> {
            SandboxWorkerFenceEntity fence = fences.findById(workerId)
                    .orElseThrow(() -> new SecurityException("SANDBOX_FENCE_MISSING"));
            if (!fence.isStopped()) throw new SecurityException("SANDBOX_STOP_NOT_PERSISTED");
            boolean acknowledged = outbox.findById(workerId)
                    .filter(o -> o.isAcknowledged() && o.getStopEpoch() == fence.getEpoch())
                    .isPresent();
            return new Status(workerId, fence.getEpoch(), acknowledged
                    ? "SIGNED_WORKER_MARKER_ACKNOWLEDGED_NO_OS_EXIT"
                    : "STOP_PERSISTED_DELIVERY_PENDING");
        }));
    }
}
