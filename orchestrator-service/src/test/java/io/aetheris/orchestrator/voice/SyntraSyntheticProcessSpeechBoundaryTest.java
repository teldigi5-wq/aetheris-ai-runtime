package io.aetheris.orchestrator.voice;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real child JVM processes, but SYNTHETIC data ONLY: no devices/models. */
class SyntraSyntheticProcessSpeechBoundaryTest {
    private static byte[] loud() {
        byte[] frame = new byte[640];
        for (int i = 0; i < frame.length; i += 2) frame[i + 1] = 16;
        return frame;
    }

    private static SyntraSyntheticProcessSpeechBoundary newBoundary(int waitMs) {
        return new SyntraSyntheticProcessSpeechBoundary(() -> true, () -> false, waitMs);
    }

    private static SyntraSyntheticProcessSpeechBoundary fault(String mode, int waitMs) {
        return SyntraSyntheticProcessSpeechBoundary.forTest(
                () -> true, () -> false, waitMs, mode);
    }

    private static void assertChildNotAlive(long pid) {
        assertThat(pid).isPositive();
        assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false))
                .isFalse();
    }

    private static long awaitChild(SyntraSyntheticProcessSpeechBoundary boundary)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        long pid;
        while ((pid = boundary.activeChildPidForTest()) == 0 &&
                System.nanoTime() < deadline) Thread.sleep(10);
        return pid;
    }

    @Test void disabledByDefaultAndExplicitSyntheticOptInOnly() {
        try (var boundary = newBoundary(3000)) {
            assertThat(boundary.inspectSyntheticPcm(loud(), 16_000).outcome())
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.DENIED);
            assertThat(boundary.beginSyntheticSession(false))
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.DENIED);
            assertThat(boundary.beginSyntheticSession(true))
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.SESSION_READY);
            assertThat(boundary.beginSyntheticSession(true))
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.DENIED);
            assertThat(boundary.microphoneIntegrated()).isFalse();
            assertThat(boundary.localSttModelLoaded()).isFalse();
            assertThat(boundary.speakerIntegrated()).isFalse();
            assertThat(boundary.recordingEnabled()).isFalse();
            assertThat(boundary.ownerAuthenticated()).isFalse();
            assertThat(boundary.desktopOperationsAuthorized()).isFalse();
        }
    }

    @Test void separateChildJvmScoresDeterministicSyntheticVoiceAndSilence() {
        try (var boundary = newBoundary(3000)) {
            boundary.beginSyntheticSession(true);
            byte[] original = loud();
            var speech = boundary.inspectSyntheticPcm(original,16_000);
            assertThat(speech.outcome())
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.VOICE);
            assertThat(speech.bytesReviewed()).isEqualTo(640);
            assertThat(original).contains((byte) 16);
            long firstPid = boundary.latestChildPidForTest();
            assertChildNotAlive(firstPid);

            var silence = boundary.inspectSyntheticPcm(new byte[640],16_000);
            assertThat(silence.outcome())
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.SILENCE);
            assertThat(silence.bytesReviewed()).isEqualTo(640);
            assertChildNotAlive(boundary.latestChildPidForTest());
            assertThat(boundary.stopped()).isFalse();
        }
    }

    @Test void arbitraryFixtureBytesCannotProduceCommandsOrModelText() {
        try (var boundary = newBoundary(3000)) {
            boundary.beginSyntheticSession(true);
            byte[] arbitrary = new byte[640];
            Arrays.fill(arbitrary, (byte) 127);
            var result = boundary.inspectSyntheticPcm(arbitrary,16_000);
            assertThat(result.outcome()).isIn(
                    SyntraSyntheticProcessSpeechBoundary.Outcome.VOICE,
                    SyntraSyntheticProcessSpeechBoundary.Outcome.SILENCE);
            assertThat(result.bytesReviewed()).isEqualTo(640);
            assertThat(arbitrary).containsOnly((byte)127);
        }
    }

    @Test void invalidFramesAndWrongSampleRateNeverSpawnChild() {
        try (var boundary = newBoundary(3000)) {
            boundary.beginSyntheticSession(true);
            for (byte[] input : new byte[][]{null, new byte[0],
                    new byte[639], new byte[641], new byte[3_202]}) {
                assertThat(boundary.inspectSyntheticPcm(input,16_000).outcome())
                        .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.DENIED);
            }
            assertThat(boundary.inspectSyntheticPcm(loud(),44_100).outcome())
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.DENIED);
            assertThat(boundary.latestChildPidForTest()).isZero();
        }
    }

    @Test void missingFixturePermissionRefusesSessionAndRevocationClosesExisting() {
        var allowed = new AtomicBoolean(false);
        try (var boundary = new SyntraSyntheticProcessSpeechBoundary(
                allowed::get, () -> false, 3000)) {
            assertThat(boundary.beginSyntheticSession(true))
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.DENIED);
            allowed.set(true);
            assertThat(boundary.beginSyntheticSession(true))
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.SESSION_READY);
            allowed.set(false);
            assertThat(boundary.inspectSyntheticPcm(loud(),16_000).outcome())
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.STOP_LATCHED);
            allowed.set(true);
            assertThat(boundary.beginSyntheticSession(true))
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.STOP_LATCHED);
        }
    }

    @Test void unavailableExternalSupervisorFailsClosedIrreversibly() {
        try (var boundary = new SyntraSyntheticProcessSpeechBoundary(
                () -> true,
                () -> { throw new IllegalStateException("supervisor missing"); },
                1000)) {
            assertThat(boundary.beginSyntheticSession(true))
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.STOP_LATCHED);
            assertThat(boundary.stopped()).isTrue();
            assertThat(boundary.latestChildPidForTest()).isZero();
        }
    }

    @Test void childTimeoutForciblyTerminatesStalledProcess() {
        try (var boundary = fault("STALL", 250)) {
            boundary.beginSyntheticSession(true);
            long started = System.nanoTime();
            var result = boundary.inspectSyntheticPcm(loud(),16_000);
            long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
            assertThat(result.outcome())
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.TIMEOUT_LATCHED);
            assertThat(millis).isLessThan(4000);
            assertChildNotAlive(boundary.latestChildPidForTest());
            assertThat(boundary.stopped()).isTrue();
            assertThat(boundary.inspectSyntheticPcm(loud(),16_000).outcome())
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.STOP_LATCHED);
        }
    }

    @Test void stopNowKillsActiveStalledChildWithoutWaitingForItsSleep() throws Exception {
        ExecutorService callers = Executors.newSingleThreadExecutor();
        try (var boundary = fault("STALL", 3000)) {
            boundary.beginSyntheticSession(true);
            Future<SyntraSyntheticProcessSpeechBoundary.Result> pending =
                    callers.submit(() -> boundary.inspectSyntheticPcm(loud(),16_000));
            long pid = awaitChild(boundary);
            assertThat(pid).isPositive();
            long began = System.nanoTime();
            assertThat(boundary.stopNow())
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.STOP_LATCHED);
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began))
                    .isLessThan(1000);
            assertThat(pending.get(4,TimeUnit.SECONDS).outcome())
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.STOP_LATCHED);
            assertChildNotAlive(pid);
            assertThat(boundary.stopped()).isTrue();
        } finally { callers.shutdownNow(); }
    }

    @Test void concurrentFrameRejectedRatherThanQueuedWhenChildInFlight() throws Exception {
        ExecutorService callers = Executors.newSingleThreadExecutor();
        try (var boundary = fault("STALL", 3000)) {
            boundary.beginSyntheticSession(true);
            var first = callers.submit(() -> boundary.inspectSyntheticPcm(loud(),16_000));
            assertThat(awaitChild(boundary)).isPositive();
            var second = boundary.inspectSyntheticPcm(loud(),16_000);
            assertThat(second.outcome())
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.BUSY_DENIED);
            boundary.stopNow();
            assertThat(first.get(4,TimeUnit.SECONDS).outcome())
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.STOP_LATCHED);
        } finally { callers.shutdownNow(); }
    }

    @Test void malformedChildProtocolIsRejectedAndSessionLatched() {
        try (var boundary = fault("MALFORMED", 3000)) {
            boundary.beginSyntheticSession(true);
            assertThat(boundary.inspectSyntheticPcm(loud(),16_000).outcome())
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.WORKER_REJECTED_LATCHED);
            assertChildNotAlive(boundary.latestChildPidForTest());
            assertThat(boundary.beginSyntheticSession(true))
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.STOP_LATCHED);
        }
    }

    @Test void childOutputFloodIsBoundedAndFailsClosed() {
        try (var boundary = fault("FLOOD", 3000)) {
            boundary.beginSyntheticSession(true);
            assertThat(boundary.inspectSyntheticPcm(loud(),16_000).outcome())
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.WORKER_REJECTED_LATCHED);
            assertChildNotAlive(boundary.latestChildPidForTest());
            assertThat(boundary.stopped()).isTrue();
        }
    }

    @Test void nonzeroWorkerExitFailsClosed() {
        try (var boundary = fault("FAIL", 3000)) {
            boundary.beginSyntheticSession(true);
            assertThat(boundary.inspectSyntheticPcm(loud(),16_000).outcome())
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.WORKER_REJECTED_LATCHED);
            assertChildNotAlive(boundary.latestChildPidForTest());
            assertThat(boundary.stopped()).isTrue();
        }
    }

    @Test void explicitCloseAndRepeatedStopPreventRestart() {
        var boundary = newBoundary(3000);
        boundary.beginSyntheticSession(true);
        boundary.close();
        boundary.close();
        assertThat(boundary.stopNow())
                .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.STOP_LATCHED);
        assertThat(boundary.inspectSyntheticPcm(loud(),16_000).outcome())
                .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.STOP_LATCHED);
        assertThat(boundary.beginSyntheticSession(true))
                .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.STOP_LATCHED);
    }

    @Test void badTimeoutAndNullDependenciesCannotInitializeWorker() {
        assertThatThrownBy(() -> new SyntraSyntheticProcessSpeechBoundary(
                () -> true, () -> false, 99))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SyntraSyntheticProcessSpeechBoundary(
                () -> true, () -> false, 5001))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SyntraSyntheticProcessSpeechBoundary(
                null, () -> false, 3000))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SyntraSyntheticProcessSpeechBoundary(
                () -> true, null, 3000))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void externalStopBeforeChildLaunchBlocksAllWork() {
        var stop = new AtomicBoolean(true);
        try (var boundary = new SyntraSyntheticProcessSpeechBoundary(
                () -> true, stop::get, 3000)) {
            assertThat(boundary.beginSyntheticSession(true))
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.STOP_LATCHED);
            stop.set(false);
            assertThat(boundary.inspectSyntheticPcm(loud(),16_000).outcome())
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.STOP_LATCHED);
            assertThat(boundary.latestChildPidForTest()).isZero();
        }
    }
}
