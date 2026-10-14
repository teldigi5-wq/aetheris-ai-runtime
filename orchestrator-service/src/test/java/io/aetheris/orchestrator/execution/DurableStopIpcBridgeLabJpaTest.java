package io.aetheris.orchestrator.execution;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic DB and keys only. No worker commands, no real user data. */
@SpringBootTest(properties = "aetheris.memory.retention-sweep-ms=3600000")
class DurableStopIpcBridgeLabJpaTest {
    @Autowired SandboxWorkerFenceRepository fences;
    @Autowired SandboxJobReservationRepository reservations;
    @Autowired SandboxStopDispatchRepository outbox;
    @Autowired PlatformTransactionManager transactions;

    private record Keys(KeyPair controller, KeyPair worker, KeyPair jobIssuer) {}
    private static Keys keys() throws Exception {
        var gen = KeyPairGenerator.getInstance("Ed25519");
        return new Keys(gen.generateKeyPair(), gen.generateKeyPair(), gen.generateKeyPair());
    }

    private String armWorker() {
        String worker = "ci_" + UUID.randomUUID().toString().replace("-", "");
        var fence = new SandboxWorkerFenceEntity(worker);
        fence.armSyntheticFixture();
        fences.saveAndFlush(fence);
        return worker;
    }

    private DurableSandboxAdmissionLab ledger(Keys keys) {
        return new DurableSandboxAdmissionLab(fences, reservations, transactions,
                keys.jobIssuer().getPublic(), Clock.systemUTC(), ignored -> null);
    }

    private DurableStopIpcBridgeLab bridge(Keys keys,
            DurableStopIpcBridgeLab.StopTransport link) {
        return new DurableStopIpcBridgeLab(ledger(keys), fences, outbox,
                transactions, keys.controller().getPrivate(),
                keys.worker().getPublic(), Clock.systemUTC(), link);
    }

    private DurableStopIpcBridgeLab.StopTransport authorizedWorker(Keys keys) {
        return frame -> {
            assertThat(SandboxWorkerStopWire.verified(frame, keys.controller().getPublic()))
                    .isTrue();
            var request = SandboxWorkerStopWire.parseRequest(frame.message());
            var receipt = new SandboxWorkerStopWire.StopReceipt(request.nonce(),
                    request.workerId(), request.epoch(), SandboxWorkerStopWire.STOP_STATUS);
            return SandboxWorkerStopWire.sign(
                    SandboxWorkerStopWire.receiptBytes(receipt), keys.worker().getPrivate());
        };
    }

    @Test
    void stopFirstThenVerifiedSignedAckAndNewCoordinatorSeesDurableSuccess() throws Exception {
        var k = keys();
        String worker = armWorker();
        var first = bridge(k, authorizedWorker(k));
        var status = first.stopAndDeliver(worker);
        assertThat(status.state()).isEqualTo("SIGNED_WORKER_MARKER_ACKNOWLEDGED_NO_OS_EXIT");
        assertThat(status.epoch()).isEqualTo(2);
        assertThat(fences.findById(worker).orElseThrow().isStopped()).isTrue();
        var stored = outbox.findById(worker).orElseThrow();
        assertThat(stored.isAcknowledged()).isTrue();
        assertThat(stored.getStopEpoch()).isEqualTo(2);
        // No IPC should happen again from a new coordinator object.
        var restarted = bridge(k, frame -> {
            throw new AssertionError("Already acknowledged STOP must not redeliver");
        });
        assertThat(restarted.snapshot(worker).state()).isEqualTo(status.state());
        assertThat(restarted.retryDelivery(worker).state()).isEqualTo(status.state());
    }

    @Test
    void unavailableWorkerLeavesDurableStopPendingAndReconnectEventuallyAcknowledges()
            throws Exception {
        var k = keys();
        String worker = armWorker();
        var offline = bridge(k, frame -> { throw new IOException("synthetic-disconnected"); });
        var pending = offline.stopAndDeliver(worker);
        assertThat(pending.state()).isEqualTo("STOP_PERSISTED_DELIVERY_PENDING");
        var nonce = outbox.findById(worker).orElseThrow().getNonce();
        assertThat(outbox.findById(worker).orElseThrow().isAcknowledged()).isFalse();
        assertThat(fences.findById(worker).orElseThrow().isStopped()).isTrue();
        // Simulated controller JVM restart with same DB, signer, pinned worker.
        var reconnect = bridge(k, authorizedWorker(k));
        assertThat(reconnect.retryDelivery(worker).state())
                .isEqualTo("SIGNED_WORKER_MARKER_ACKNOWLEDGED_NO_OS_EXIT");
        assertThat(outbox.findById(worker).orElseThrow().getNonce()).isEqualTo(nonce);
        assertThat(outbox.findById(worker).orElseThrow().isAcknowledged()).isTrue();
    }

    @Test
    void forgedOrMisaddressedWorkerAckNeverMarksDeliveryConfirmed() throws Exception {
        var k = keys();
        String worker = armWorker();
        var outsider = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var forged = bridge(k, frame -> {
            var original = SandboxWorkerStopWire.parseRequest(frame.message());
            var receipt = new SandboxWorkerStopWire.StopReceipt(original.nonce(),
                    worker, original.epoch(), SandboxWorkerStopWire.STOP_STATUS);
            return SandboxWorkerStopWire.sign(SandboxWorkerStopWire.receiptBytes(receipt),
                    outsider.getPrivate());
        });
        assertThat(forged.stopAndDeliver(worker).state())
                .isEqualTo("STOP_PERSISTED_ACK_UNVERIFIED");
        assertThat(outbox.findById(worker).orElseThrow().isAcknowledged()).isFalse();

        var wrongNonce = bridge(k, frame -> {
            var original = SandboxWorkerStopWire.parseRequest(frame.message());
            var receipt = new SandboxWorkerStopWire.StopReceipt(UUID.randomUUID(),
                    worker, original.epoch(), SandboxWorkerStopWire.STOP_STATUS);
            return SandboxWorkerStopWire.sign(SandboxWorkerStopWire.receiptBytes(receipt),
                    k.worker().getPrivate());
        });
        assertThat(wrongNonce.retryDelivery(worker).state())
                .isEqualTo("STOP_PERSISTED_ACK_UNVERIFIED");
        assertThat(outbox.findById(worker).orElseThrow().isAcknowledged()).isFalse();
        assertThat(bridge(k, authorizedWorker(k)).retryDelivery(worker).state())
                .isEqualTo("SIGNED_WORKER_MARKER_ACKNOWLEDGED_NO_OS_EXIT");
    }

    @Test
    void dbStopCannotBeImpliedByAckAndMissingFenceDeniesBeforeTransport() throws Exception {
        var k = keys();
        String missing = "missing_" + UUID.randomUUID().toString().replace("-", "");
        var coordinator = bridge(k, frame -> {
            throw new AssertionError("Transport must never run before durable DB STOP");
        });
        assertThatThrownBy(() -> coordinator.stopAndDeliver(missing))
                .isInstanceOf(SecurityException.class).hasMessageContaining("FENCE_MISSING");
        assertThat(outbox.findById(missing)).isEmpty();

        String armed = armWorker();
        assertThatThrownBy(() -> coordinator.retryDelivery(armed))
                .isInstanceOf(SecurityException.class).hasMessageContaining("STOP_NOT_PERSISTED");
        assertThat(outbox.findById(armed)).isEmpty();
    }

    @Test
    void missingOutboxAfterCommittedStopCanBeRebuiltWithoutRearming() throws Exception {
        var k = keys();
        String worker = armWorker();
        var ledger = ledger(k);
        var receipt = ledger.stopWorker(worker);
        assertThat(receipt.status()).contains("OS_TERMINATION_UNVERIFIED");
        assertThat(outbox.findById(worker)).isEmpty();
        assertThat(bridge(k, authorizedWorker(k)).retryDelivery(worker).state())
                .isEqualTo("SIGNED_WORKER_MARKER_ACKNOWLEDGED_NO_OS_EXIT");
    }

    @Test
    void stopOfOneWorkerCannotAcknowledgeOrAffectAnotherWorkersFence() throws Exception {
        var k = keys();
        String workerA = armWorker(), workerB = armWorker();
        assertThat(bridge(k, authorizedWorker(k)).stopAndDeliver(workerA).state())
                .isEqualTo("SIGNED_WORKER_MARKER_ACKNOWLEDGED_NO_OS_EXIT");
        assertThat(fences.findById(workerB).orElseThrow().isStopped()).isFalse();
        assertThat(outbox.findById(workerB)).isEmpty();
    }
}
