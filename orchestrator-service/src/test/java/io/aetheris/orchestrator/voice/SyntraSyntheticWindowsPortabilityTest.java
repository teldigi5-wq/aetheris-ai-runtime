package io.aetheris.orchestrator.voice;

import org.junit.jupiter.api.Test;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Synthetic child JVM smoke tests on GitHub-hosted Windows, no devices. */
class SyntraSyntheticWindowsPortabilityTest {
    private static void windowsOnly() {
        assumeTrue(System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT).contains("win"));
    }
    private static byte[] voiced() {
        byte[] result = new byte[640];
        for (int i=0; i<result.length; i+=2) result[i+1] = 16;
        return result;
    }

    @Test void portableSyntheticWorkerRunsOnWindowsWithoutMicrophone() {
        windowsOnly();
        try (var boundary = new SyntraSyntheticProcessSpeechBoundary(
                () -> true, () -> false, 5000)) {
            assertThat(boundary.beginSyntheticSession(true))
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.SESSION_READY);
            var voice = boundary.inspectSyntheticPcm(voiced(), 16000);
            assertThat(voice.outcome()).isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.VOICE);
            var silence = boundary.inspectSyntheticPcm(new byte[640], 16000);
            assertThat(silence.outcome()).isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.SILENCE);
            assertThat(boundary.microphoneIntegrated()).isFalse();
            assertThat(boundary.desktopOperationsAuthorized()).isFalse();
            assertThat(boundary.recordingEnabled()).isFalse();
            assertThat(ProcessHandle.of(boundary.latestChildPidForTest())
                    .map(ProcessHandle::isAlive).orElse(false)).isFalse();
        }
    }

    @Test void windowsSyntheticStallTriggersForcedChildTermination() {
        windowsOnly();
        try (var boundary = SyntraSyntheticProcessSpeechBoundary.forTest(
                () -> true, () -> false, 1200, "STALL")) {
            boundary.beginSyntheticSession(true);
            long started = System.nanoTime();
            var reply = boundary.inspectSyntheticPcm(voiced(), 16000);
            assertThat(reply.outcome()).isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.TIMEOUT_LATCHED);
            assertThat(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-started)).isLessThan(10);
            assertThat(boundary.stopped()).isTrue();
            assertThat(ProcessHandle.of(boundary.latestChildPidForTest())
                    .map(ProcessHandle::isAlive).orElse(false)).isFalse();
        }
    }

    @Test void linuxLimitsLabIsNotClaimedOnWindows() {
        windowsOnly();
        assertThat(SyntraSyntheticHostLimits.linuxLabAvailable()).isFalse();
        assertThatThrownBy(() -> SyntraSyntheticProcessSpeechBoundary.forLinuxLimitsTest(
                () -> true, () -> false, 3000, "NORMAL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LINUX_RESOURCE_LAB_NOT_SUPPORTED");
    }
}
