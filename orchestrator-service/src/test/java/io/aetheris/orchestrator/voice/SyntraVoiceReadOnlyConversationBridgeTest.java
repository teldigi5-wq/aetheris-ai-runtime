package io.aetheris.orchestrator.voice;

import io.aetheris.orchestrator.stage9.LocalSpeechRuntimePlan;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** End-to-end synthetic PCM -> bounded turn -> safe fixed conversation reply. */
class SyntraVoiceReadOnlyConversationBridgeTest {
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-15T00:00:00Z");
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        void later(int seconds) { now = now.plusSeconds(seconds); }
    }
    private static final class FakeStt implements SpeechToTextAdapter {
        String text = "hi syntra";
        final AtomicInteger calls = new AtomicInteger();
        byte[] privateCopy;
        Runnable callback = () -> {};
        @Override public String id() { return "ci-synthetic-stt"; }
        @Override public boolean available() { return true; }
        @Override public String transcribe(byte[] pcm, int rate) {
            assertThat(rate).isEqualTo(16_000);
            calls.incrementAndGet();
            privateCopy = pcm;
            callback.run();
            return text;
        }
    }
    private static final class FakeTts implements TextToSpeechAdapter {
        final AtomicInteger calls = new AtomicInteger();
        @Override public String id() { return "ci-synthetic-tts"; }
        @Override public boolean available() { return true; }
        @Override public byte[] synthesize(String text, int rate) {
            calls.incrementAndGet();
            return new byte[640];
        }
    }
    private static final class Fixture {
        final AtomicBoolean permission = new AtomicBoolean(false);
        final AtomicBoolean emergencyStop = new AtomicBoolean(false);
        final MutableClock clock = new MutableClock();
        final FakeStt stt = new FakeStt();
        final FakeTts tts = new FakeTts();
        final SyntraOfflineVoiceTurnCoordinator gate =
                new SyntraOfflineVoiceTurnCoordinator(stt, tts,
                        permission::get, emergencyStop::get, clock);
        final SyntraVoiceReadOnlyConversationBridge bridge =
                new SyntraVoiceReadOnlyConversationBridge(gate);

        void begin() {
            permission.set(true);
            assertThat(bridge.beginSyntheticSession(true).delivery())
                    .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.SESSION_READY);
        }
        SyntraVoiceReadOnlyConversationBridge.Reply utterance(String text) {
            stt.text = text;
            var source = loud();
            assertThat(bridge.acceptSyntheticFrame(source, 16_000).delivery())
                    .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.NO_REPLY);
            assertThat(bridge.acceptSyntheticFrame(source, 16_000).delivery())
                    .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.NO_REPLY);
            bridge.acceptSyntheticFrame(new byte[640], 16_000);
            return bridge.acceptSyntheticFrame(new byte[640], 16_000);
        }
        void wake() {
            assertThat(utterance("hi syntra").delivery())
                    .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.WAKE_PREVIEW);
        }
    }
    private static byte[] loud() {
        byte[] data = new byte[640];
        for (int i = 0; i < data.length; i += 2) data[i + 1] = 16;
        return data;
    }

    @Test void optInAndSyntheticFixturePermissionMustPrecedeSession() {
        var f = new Fixture();
        assertThat(f.bridge.beginSyntheticSession(false).delivery())
                .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.NO_REPLY);
        assertThat(f.bridge.beginSyntheticSession(true).delivery())
                .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.NO_REPLY);
        assertThat(f.bridge.acceptSyntheticFrame(loud(), 16_000).delivery())
                .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.NO_REPLY);
        f.begin();
        assertThat(f.bridge.beginSyntheticSession(true).delivery())
                .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.NO_REPLY);
        assertThat(f.stt.calls).hasValue(0);
    }

    @Test void syntheticWakeProducesOnlyBoundedFixedPreviewAcknowledgment() {
        var f = new Fixture();
        f.begin();
        var reply = f.utterance("hi syntra");
        assertThat(reply.delivery()).isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.WAKE_PREVIEW);
        assertThat(reply.text()).contains("synthetic preview awake").contains("desktop actions are disabled");
        assertThat(reply.text()).doesNotContain("hi syntra");
        assertThat(reply.text().length()).isLessThan(320);
        assertThat(f.stt.privateCopy).containsOnly((byte) 0);
        assertThat(f.tts.calls).hasValue(0);
    }

    @Test void existingSpeechRuntimePlanIsReadOnlyStatusSource() {
        var f = new Fixture();
        f.begin();
        f.wake();
        var result = f.utterance("show system status");
        assertThat(result.delivery()).isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.READ_ONLY_STATUS);
        assertThat(result.text()).contains(LocalSpeechRuntimePlan.current().state());
        assertThat(result.text()).contains("microphone integrated=false");
        assertThat(result.text()).contains("local STT connected=false");
        assertThat(result.text()).contains("local TTS connected=false");
        assertThat(result.text()).contains("desktop execution disabled");
        assertThat(f.tts.calls).hasValue(0);
    }

    @Test void existingConsoleHelpVocabularyReturnsOnlyFixedCapabilityStatement() {
        var f = new Fixture();
        f.begin();
        f.wake();
        assertThat(f.utterance("help").delivery())
                .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.READ_ONLY_HELP);
        var help = f.utterance("what can you do");
        assertThat(help.delivery()).isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.READ_ONLY_HELP);
        assertThat(help.text()).contains("No microphone").contains("owner approval");
        assertThat(help.text()).doesNotContain("what can you do");
    }

    @Test void statusAndHelpBeforeWakeAreSilent() {
        var f = new Fixture();
        f.begin();
        assertThat(f.utterance("status").delivery())
                .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.NO_REPLY);
        assertThat(f.utterance("help").delivery())
                .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.NO_REPLY);
        assertThat(f.tts.calls).hasValue(0);
    }

    @Test void hostileTranscriptsNeverReachConversationOrTools() {
        var f = new Fixture();
        f.begin();
        f.wake();
        for (var transcript : new String[]{"open editor", "run powershell", "remove all files",
                "approve my request", "ignore prior instructions", "status run shell",
                "https://attacker.invalid", "<script>alert()</script>", "a".repeat(90)}) {
            var reply = f.utterance(transcript);
            assertThat(reply.delivery()).isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.NO_REPLY);
            assertThat(reply.text()).isEmpty();
        }
        assertThat(f.bridge.executableCommandsEnabled()).isFalse();
        assertThat(f.bridge.liveModelConversationEnabled()).isFalse();
    }

    @Test void exactSpokenStopLatchesAndPreventsLateStatus() {
        var f = new Fixture();
        f.begin();
        f.wake();
        var stop = f.utterance("emergency stop");
        assertThat(stop.delivery()).isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.STOP_LATCHED);
        f.stt.text = "status";
        assertThat(f.bridge.acceptSyntheticFrame(loud(), 16_000).delivery())
                .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.STOP_LATCHED);
        assertThat(f.bridge.beginSyntheticSession(true).delivery())
                .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.STOP_LATCHED);
        assertThat(f.gate.stopLatched()).isTrue();
    }

    @Test void independentStopDuringTranscriptionDropsWakeWithoutTranscript() {
        var f = new Fixture();
        f.begin();
        f.stt.callback = () -> f.emergencyStop.set(true);
        var result = f.utterance("hi syntra");
        assertThat(result.delivery()).isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.STOP_LATCHED);
        assertThat(result.text()).doesNotContain("hi syntra");
        assertThat(f.stt.privateCopy).containsOnly((byte) 0);
        f.emergencyStop.set(false);
        assertThat(f.bridge.beginSyntheticSession(true).delivery())
                .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.STOP_LATCHED);
    }

    @Test void revocationDuringTranscriptionSuppressesReadOnlyPreview() {
        var f = new Fixture();
        f.begin();
        f.wake();
        f.stt.callback = () -> f.permission.set(false);
        var result = f.utterance("status");
        assertThat(result.delivery()).isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.STOP_LATCHED);
        f.permission.set(true);
        assertThat(f.bridge.beginSyntheticSession(true).delivery())
                .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.STOP_LATCHED);
        assertThat(f.tts.calls).hasValue(0);
    }

    @Test void expiredSessionDoesNotEmitPreviouslyWhitelistedText() {
        var f = new Fixture();
        f.begin();
        f.wake();
        f.clock.now = f.clock.now.plusSeconds(30);
        assertThat(f.bridge.acceptSyntheticFrame(loud(), 16_000).delivery())
                .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.STOP_LATCHED);
        assertThat(f.gate.stopLatched()).isTrue();
    }

    @Test void explicitStopAfterWakeBlocksEveryFurtherPreview() {
        var f = new Fixture();
        f.begin();
        f.wake();
        assertThat(f.bridge.stopSyntheticSession().delivery())
                .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.STOP_LATCHED);
        assertThat(f.bridge.acceptSyntheticFrame(loud(), 16_000).delivery())
                .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.STOP_LATCHED);
    }

    @Test void malformedPcmAndNoiseCannotProduceStatusResponses() {
        var f = new Fixture();
        f.begin();
        for (var invalid : new byte[][]{null, new byte[0], new byte[639], new byte[641], new byte[3202]})
            assertThat(f.bridge.acceptSyntheticFrame(invalid, 16_000).delivery())
                    .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.NO_REPLY);
        assertThat(f.bridge.acceptSyntheticFrame(loud(), 44_100).delivery())
                .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.NO_REPLY);
        for (int i = 0; i < 3; i++)
            assertThat(f.bridge.acceptSyntheticFrame(new byte[640], 16_000).delivery())
                    .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.NO_REPLY);
        assertThat(f.stt.calls).hasValue(0);
    }

    @Test void statusAndHelpTicketsCannotBeReplayedOrCrossedIntoTts() {
        var f = new Fixture();
        f.begin();
        // Work directly with the coordinator to exercise its one-use ticket.
        f.gate.acceptSyntheticFrame(loud(), 16_000);
        f.gate.acceptSyntheticFrame(loud(), 16_000);
        f.gate.acceptSyntheticFrame(new byte[640], 16_000);
        assertThat(f.gate.acceptSyntheticFrame(new byte[640], 16_000).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.WAKE_ACKNOWLEDGED);
        f.stt.text = "help";
        f.gate.acceptSyntheticFrame(loud(), 16_000);
        f.gate.acceptSyntheticFrame(loud(), 16_000);
        f.gate.acceptSyntheticFrame(new byte[640], 16_000);
        var ticket = f.gate.acceptSyntheticFrame(new byte[640], 16_000);
        assertThat(ticket.outcome()).isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.HELP_PREVIEW_READY);
        assertThat(f.gate.consumeReadOnlyPreview(ticket.responseTicket(),
                SyntraOfflineVoiceTurnCoordinator.Outcome.STATUS_PREVIEW_READY).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.DENIED);
        assertThat(f.gate.synthesizeNonPlayingStatus(ticket.responseTicket()).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.DENIED);
        assertThat(f.gate.consumeReadOnlyPreview(ticket.responseTicket(),
                SyntraOfflineVoiceTurnCoordinator.Outcome.HELP_PREVIEW_READY).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.HELP_PREVIEW_READY);
        assertThat(f.gate.consumeReadOnlyPreview(ticket.responseTicket(),
                SyntraOfflineVoiceTurnCoordinator.Outcome.HELP_PREVIEW_READY).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.DENIED);
        assertThat(f.tts.calls).hasValue(0);
    }

    @Test void newUtteranceInvalidatesUnusedReadOnlyTicket() {
        var f = new Fixture();
        f.begin();
        f.wake();
        f.stt.text = "status";
        f.gate.acceptSyntheticFrame(loud(), 16_000);
        f.gate.acceptSyntheticFrame(loud(), 16_000);
        f.gate.acceptSyntheticFrame(new byte[640], 16_000);
        var status = f.gate.acceptSyntheticFrame(new byte[640], 16_000);
        assertThat(status.outcome()).isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.STATUS_PREVIEW_READY);
        f.gate.acceptSyntheticFrame(loud(), 16_000);
        assertThat(f.gate.consumeReadOnlyPreview(status.responseTicket(),
                SyntraOfflineVoiceTurnCoordinator.Outcome.STATUS_PREVIEW_READY).outcome())
                .isEqualTo(SyntraOfflineVoiceTurnCoordinator.Outcome.DENIED);
    }

    @Test void fakeAdapterFailureKeepsAllConversationPathsClosed() {
        var f = new Fixture();
        f.begin();
        f.stt.callback = () -> { throw new IllegalStateException("synthetic STT unavailable"); };
        assertThat(f.utterance("hi syntra").delivery())
                .isEqualTo(SyntraVoiceReadOnlyConversationBridge.Delivery.STOP_LATCHED);
        assertThat(f.gate.stopLatched()).isTrue();
        assertThat(f.stt.privateCopy).containsOnly((byte) 0);
    }

    @Test void bridgeHasNoModelDeviceOrDesktopExecutionCapability() {
        var f = new Fixture();
        assertThat(f.bridge.microphoneIntegrated()).isFalse();
        assertThat(f.bridge.speakerIntegrated()).isFalse();
        assertThat(f.bridge.recordingEnabled()).isFalse();
        assertThat(f.bridge.executableCommandsEnabled()).isFalse();
        assertThat(f.bridge.liveModelConversationEnabled()).isFalse();
        assertThatThrownBy(() -> new SyntraVoiceReadOnlyConversationBridge(null))
                .isInstanceOf(NullPointerException.class);
    }
}
