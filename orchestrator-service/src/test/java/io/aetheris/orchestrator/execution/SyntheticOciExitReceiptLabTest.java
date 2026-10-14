package io.aetheris.orchestrator.execution;

import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyntheticOciExitReceiptLabTest {
    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String CONTAINER = "a".repeat(64);

    private static SyntheticOciExitReceiptLab.Observation stopped() {
        return new SyntheticOciExitReceiptLab.Observation(
                UUID.randomUUID(), "workerA", 2, CONTAINER, NOW.getEpochSecond(),
                137, false, 0, true, true);
    }

    @Test
    void pinnedSignerBindsExactJobWorkerEpochAndContainer() throws Exception {
        var trusted = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var observed = stopped();
        var receipt = SyntheticOciExitReceiptLab.signObservedExit(
                observed, trusted.getPrivate());
        assertThat(SyntheticOciExitReceiptLab.verify(receipt, trusted.getPublic(),
                observed.jobId(), observed.workerId(), observed.stopEpoch(),
                observed.containerId(), CLOCK)).isTrue();
        assertThat(SyntheticOciExitReceiptLab.verify(receipt, other.getPublic(),
                observed.jobId(), observed.workerId(), observed.stopEpoch(),
                observed.containerId(), CLOCK)).isFalse();
        assertThat(SyntheticOciExitReceiptLab.verify(receipt, trusted.getPublic(),
                UUID.randomUUID(), observed.workerId(), observed.stopEpoch(),
                observed.containerId(), CLOCK)).isFalse();
        assertThat(SyntheticOciExitReceiptLab.verify(receipt, trusted.getPublic(),
                observed.jobId(), "otherWorker", observed.stopEpoch(),
                observed.containerId(), CLOCK)).isFalse();
        assertThat(SyntheticOciExitReceiptLab.verify(receipt, trusted.getPublic(),
                observed.jobId(), observed.workerId(), 99L,
                observed.containerId(), CLOCK)).isFalse();
        assertThat(SyntheticOciExitReceiptLab.verify(receipt, trusted.getPublic(),
                observed.jobId(), observed.workerId(), observed.stopEpoch(),
                "b".repeat(64), CLOCK)).isFalse();
    }

    @Test
    void cannotSignRunningProcessOrUnrevokedDatabaseEpoch() throws Exception {
        var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var good = stopped();
        var stillRunning = new SyntheticOciExitReceiptLab.Observation(
                good.jobId(), good.workerId(), good.stopEpoch(), good.containerId(),
                good.observedAt(), 137, true, 1, false, true);
        var noDatabaseStop = new SyntheticOciExitReceiptLab.Observation(
                good.jobId(), good.workerId(), good.stopEpoch(), good.containerId(),
                good.observedAt(), 137, false, 0, true, false);
        assertThatThrownBy(() -> SyntheticOciExitReceiptLab.signObservedExit(
                stillRunning, key.getPrivate())).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> SyntheticOciExitReceiptLab.signObservedExit(
                noDatabaseStop, key.getPrivate())).isInstanceOf(SecurityException.class);
    }

    @Test
    void staleAndTamperedEvidenceIsDenied() throws Exception {
        var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var good = stopped();
        var signed = SyntheticOciExitReceiptLab.signObservedExit(good, key.getPrivate());
        var staleClock = Clock.fixed(NOW.plusSeconds(301), ZoneOffset.UTC);
        assertThat(SyntheticOciExitReceiptLab.verify(signed, key.getPublic(),
                good.jobId(), good.workerId(), good.stopEpoch(),
                good.containerId(), staleClock)).isFalse();
        var mutated = new SyntheticOciExitReceiptLab.Observation(
                good.jobId(), good.workerId(), good.stopEpoch(),
                good.containerId(), good.observedAt(), 137, true, 2, false, true);
        assertThat(SyntheticOciExitReceiptLab.verify(
                new SyntheticOciExitReceiptLab.SignedReceipt(mutated, signed.signature()),
                key.getPublic(), good.jobId(), good.workerId(),
                good.stopEpoch(), good.containerId(), CLOCK)).isFalse();
        var exported = signed.signature();
        exported[0] ^= 1;
        assertThat(SyntheticOciExitReceiptLab.verify(signed, key.getPublic(),
                good.jobId(), good.workerId(), good.stopEpoch(),
                good.containerId(), CLOCK)).isTrue();
    }
}
