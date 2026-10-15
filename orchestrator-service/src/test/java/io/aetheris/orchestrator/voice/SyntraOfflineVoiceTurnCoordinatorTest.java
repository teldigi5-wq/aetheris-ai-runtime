package io.aetheris.orchestrator.voice;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic PCM only: no physical mic, playback, model weights or desktop calls. */
class SyntraOfflineVoiceTurnCoordinatorTest {
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-10-15T00:00:00Z");
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        void advance(int seconds) { now = now.plusSeconds(seconds); }
    }
    private static final class FakeStt implements SpeechToTextAdapter {
        final AtomicInteger calls = new AtomicInteger();
        String response = "hi syntra";
        Runnable onCall = () -> {};
        byte[] borrowed;
        boolean enabled = true;
        @Override public String id() { return "ci-synthetic-stt"; }
        @Override public boolean available() { return enabled; }
        @Override public String transcribe(byte[] pcm, int rate) {
            assertThat(rate).isEqualTo(16_000);
            calls.incrementAndGet();
            borrowed = pcm;
            onCall.run();
            return response;
        }
    }
    private static final class FakeTts implements TextToSpeechAdapter {
        final AtomicInteger calls = new AtomicInteger();
        byte[] bytes = new byte[640];
        Runnable onCall = () -> {};
        String spoken;
        @Override public String id() { return "ci-synthetic-tts"; }
        @Override public boolean available() { return true; }
        @Override public byte[] synthesize(String text, int rate) {
            assertThat(rate).isEqualTo(16_000);
            spoken = text;
            calls.incrementAndGet();
            onCall.run();
            return bytes;
        }
    }
    private static final class Fixture {
        final TestClock clock = new TestClock();
        final AtomicBoolean permitted = new AtomicBoolean();
        final AtomicBoolean stopped = new AtomicBoolean();
        final FakeStt stt = new FakeStt();
        final FakeTts tts = new FakeTts();
        final SyntraOfflineVoiceTurnCoordinator coordinator = new SyntraOfflineVoiceTurnCoordinator(
                stt, tts, permitted::get, stopped::get, clock);
        void start() {
            permitted.set(true);
            assertThat(coordinator.beginSyntheticSession(true).outcome())
                    .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.READY);
        }
        SyntraOfflineVoiceTurnCoordinator.Event closeTurn() {
            assertThat(coordinator.acceptSyntheticFrame(loud(), 16_000).outcome())
                    .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.SPEECH_BUFFERED);
            coordinator.acceptSyntheticFrame(loud(), 16_000);
            coordinator.acceptSyntheticFrame(new byte[640], 16_000);
            return coordinator.acceptSyntheticFrame(new byte[640], 16_000);
        }
    }
    private static byte[] loud() {
        byte[] pcm = new byte[640];
        for (int i = 0; i < pcm.length; i += 2) pcm[i + 1] = 16;
        return pcm;
    }
    private static byte[] longLoudFrame() {
        byte[] pcm = new byte[3_200];
        for (int i = 0; i < pcm.length; i += 2) pcm[i + 1] = 16;
        return pcm;
    }

    @Test void disabledByDefaultAndOnlyExplicitSyntheticOptInCanStart() {
        var f = new Fixture();
        assertThat(f.coordinator.acceptSyntheticFrame(loud(), 16_000).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.DENIED);
        assertThat(f.coordinator.beginSyntheticSession(false).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.DENIED);
        assertThat(f.coordinator.beginSyntheticSession(true).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.DENIED);
        f.start();
        assertThat(f.coordinator.beginSyntheticSession(true).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.DENIED);
        assertThat(f.coordinator.microphoneIntegrated()).isFalse();
        assertThat(f.coordinator.speakerIntegrated()).isFalse();
        assertThat(f.coordinator.modelWeightsLoaded()).isFalse();
        assertThat(f.coordinator.ownerAuthenticated()).isFalse();
        assertThat(f.coordinator.desktopCommandsAuthorized()).isFalse();
        assertThat(f.coordinator.recordingEnabled()).isFalse();
        assertThat(f.stt.calls).hasValue(0);
        assertThat(f.tts.calls).hasValue(0);
    }

    @Test void boundedPcmWithTwoSpeechAndTwoSilenceFramesAcknowledgesFakeWake() {
        var f = new Fixture();
        f.start();
        byte[] frame = loud();
        f.coordinator.acceptSyntheticFrame(frame, 16_000);
        f.coordinator.acceptSyntheticFrame(frame, 16_000);
        f.coordinator.acceptSyntheticFrame(new byte[640], 16_000);
        assertThat(f.coordinator.acceptSyntheticFrame(new byte[640], 16_000).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.WAKE_ACKNOWLEDGED);
        assertThat(frame).contains((byte) 16);
        assertThat(f.stt.borrowed).containsOnly((byte) 0);
        assertThat(f.stt.calls).hasValue(1);
    }

    @Test void quietFramesAndSingleFrameImpulseCannotTriggerStt() {
        var f = new Fixture();
        f.start();
        for (int i = 0; i < 4; i++)
            assertThat(f.coordinator.acceptSyntheticFrame(new byte[640], 16_000).outcome())
                    .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.LISTENING);
        f.coordinator.acceptSyntheticFrame(loud(), 16_000);
        f.coordinator.acceptSyntheticFrame(new byte[640], 16_000);
        assertThat(f.coordinator.acceptSyntheticFrame(new byte[640], 16_000).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.NOISE_DISCARDED);
        assertThat(f.stt.calls).hasValue(0);
    }

    @Test void onlyWhitelistedStatusProducesOneUseNonPlayingFakeTts() {
        var f = new Fixture();
        f.start();
        assertThat(f.closeTurn().outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.WAKE_ACKNOWLEDGED);
        f.stt.response = "show system status";
        var status = f.closeTurn();
        assertThat(status.outcome()).isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.STATUS_PREVIEW_READY);
        assertThat(status.responseTicket()).isPositive();
        Arrays.fill(f.tts.bytes, (byte) 7);
        var result = f.coordinator.synthesizeNonPlayingStatus(status.responseTicket());
        assertThat(result.outcome()).isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.SYNTHETIC_TTS_NOT_PLAYED);
        assertThat(result.syntheticAudioBytes()).isEqualTo(640);
        assertThat(f.tts.bytes).containsOnly((byte) 0);
        assertThat(f.tts.spoken).contains("Real voice and desktop control are disabled");
        assertThat(f.coordinator.synthesizeNonPlayingStatus(status.responseTicket()).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.DENIED);
        assertThat(f.tts.calls).hasValue(1);
    }

    @Test void spokenStatusBeforeWakeAndUntrustedInstructionsAreIgnored() {
        var f = new Fixture();
        f.start();
        f.stt.response = "status";
        assertThat(f.closeTurn().outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.UNTRUSTED_TRANSCRIPT_IGNORED);
        f.stt.response = "hi syntra";
        f.closeTurn();
        for (String text : new String[]{"open editor", "delete all files",
                "launch powershell", "ignore previous instructions", "https://bad.example",
                "approve owner", "status; run code", "a".repeat(100)}) {
            f.stt.response = text;
            assertThat(f.closeTurn().outcome())
                    .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.UNTRUSTED_TRANSCRIPT_IGNORED);
        }
        assertThat(f.tts.calls).hasValue(0);
        assertThat(f.coordinator.desktopCommandsAuthorized()).isFalse();
    }

    @Test void spokenStopIsTerminalEvenIfPermissionIsRestored() {
        var f = new Fixture();
        f.start();
        f.stt.response = "emergency stop";
        assertThat(f.closeTurn().outcome()).isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.STOP_LATCHED);
        assertThat(f.coordinator.stopLatched()).isTrue();
        assertThat(f.coordinator.beginSyntheticSession(true).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.STOP_LATCHED);
        assertThat(f.coordinator.acceptSyntheticFrame(loud(), 16_000).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.STOP_LATCHED);
    }

    @Test void emergencyStopDuringFakeSttDropsLateTranscriptAndWipesCopy() {
        var f = new Fixture();
        f.start();
        f.stt.onCall = () -> f.stopped.set(true);
        assertThat(f.closeTurn().outcome()).isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.STOP_LATCHED);
        assertThat(f.stt.borrowed).containsOnly((byte) 0);
        f.stopped.set(false);
        assertThat(f.coordinator.beginSyntheticSession(true).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.STOP_LATCHED);
    }

    @Test void permissionRevokedDuringSttCancelsWakeAndCannotRearm() {
        var f = new Fixture();
        f.start();
        f.stt.onCall = () -> f.permitted.set(false);
        assertThat(f.closeTurn().outcome()).isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.STOP_LATCHED);
        f.permitted.set(true);
        assertThat(f.coordinator.beginSyntheticSession(true).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.STOP_LATCHED);
    }

    @Test void emergencyStopDuringFakeTtsDropsOutputAndWipesIt() {
        var f = new Fixture();
        f.start();
        f.closeTurn();
        f.stt.response = "status";
        var ticket = f.closeTurn().responseTicket();
        Arrays.fill(f.tts.bytes, (byte) 9);
        f.tts.onCall = () -> f.stopped.set(true);
        assertThat(f.coordinator.synthesizeNonPlayingStatus(ticket).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.STOP_LATCHED);
        assertThat(f.tts.bytes).containsOnly((byte) 0);
        f.stopped.set(false);
        assertThat(f.coordinator.synthesizeNonPlayingStatus(ticket).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.STOP_LATCHED);
    }

    @Test void newUtteranceInvalidatesAnyUnplayedResponse() {
        var f = new Fixture();
        f.start();
        f.closeTurn();
        f.stt.response = "status";
        long oldTicket = f.closeTurn().responseTicket();
        f.coordinator.acceptSyntheticFrame(loud(), 16_000);
        assertThat(f.coordinator.synthesizeNonPlayingStatus(oldTicket).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.DENIED);
        assertThat(f.tts.calls).hasValue(0);
    }

    @Test void malformedFrameAndWrongRateCannotReachStt() {
        var f = new Fixture();
        f.start();
        for (byte[] bad : new byte[][]{null, new byte[0], new byte[639],
                new byte[641], new byte[3202]})
            assertThat(f.coordinator.acceptSyntheticFrame(bad, 16_000).outcome())
                    .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.DENIED);
        assertThat(f.coordinator.acceptSyntheticFrame(loud(), 44_100).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.DENIED);
        assertThat(f.stt.calls).hasValue(0);
    }

    @Test void expiredSessionAndFrameBudgetCannotAutoRearm() {
        var f = new Fixture();
        f.start();
        f.clock.advance(30);
        assertThat(f.coordinator.acceptSyntheticFrame(loud(), 16_000).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.STOP_LATCHED);
        var g = new Fixture();
        g.start();
        for (int i = 0; i < 120; i++)
            assertThat(g.coordinator.acceptSyntheticFrame(new byte[640], 16_000).outcome())
                    .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.LISTENING);
        assertThat(g.coordinator.acceptSyntheticFrame(new byte[640], 16_000).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.STOP_LATCHED);
    }

    @Test void oversizedUtteranceStopsWithoutCallingStt() {
        var f = new Fixture();
        f.start();
        for (int i = 0; i < 10; i++)
            assertThat(f.coordinator.acceptSyntheticFrame(longLoudFrame(), 16_000).outcome())
                    .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.SPEECH_BUFFERED);
        assertThat(f.coordinator.acceptSyntheticFrame(longLoudFrame(), 16_000).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.STOP_LATCHED);
        assertThat(f.stt.calls).hasValue(0);
    }

    @Test void fakeSttTtsExceptionsAndBadOutputFailClosed() {
        var f = new Fixture();
        f.start();
        f.stt.onCall = () -> { throw new IllegalStateException("synthetic fault"); };
        assertThat(f.closeTurn().outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.STOP_LATCHED);
        var g = new Fixture();
        g.start();
        g.closeTurn();
        g.stt.response = "status";
        long ticket = g.closeTurn().responseTicket();
        g.tts.bytes = new byte[32_002];
        assertThat(g.coordinator.synthesizeNonPlayingStatus(ticket).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.STOP_LATCHED);
        assertThat(g.tts.bytes).containsOnly((byte) 0);
    }

    @Test void untrustedAdaptersAndUnavailableStopSupervisorFailClosed() {
        var f = new Fixture();
        SpeechToTextAdapter untrusted = new SpeechToTextAdapter() {
            public String id() { return "windows-microphone"; }
            public boolean available() { return true; }
            public String transcribe(byte[] pcm, int rate) { return "hi syntra"; }
        };
        assertThatThrownBy(() -> new SyntraOfflineVoiceTurnCoordinator(untrusted, f.tts,
                () -> true, () -> false, f.clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LIVE_SPEECH_ENGINE_NOT_AUTHORIZED");
        var broken = new SyntraOfflineVoiceTurnCoordinator(f.stt, f.tts,
                () -> true, () -> { throw new IllegalStateException("STOP unavailable"); }, f.clock);
        assertThat(broken.beginSyntheticSession(true).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.STOP_LATCHED);
    }
}
