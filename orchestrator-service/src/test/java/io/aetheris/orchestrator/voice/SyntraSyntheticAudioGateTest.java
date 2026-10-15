package io.aetheris.orchestrator.voice;

import io.aetheris.orchestrator.stage9.LocalSpeechRuntimePlan;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Deterministic pure-Java fake audio: NO microphones, speakers, files or Ollama. */
class SyntraSyntheticAudioGateTest {
    private static final class MutableClock extends Clock {
        private Instant now=Instant.parse("2026-10-15T00:00:00Z");
        @Override public ZoneId getZone() {return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone) {return this;}
        @Override public Instant instant() {return now;}
        void later(long seconds) {now=now.plusSeconds(seconds);}
    }

    private static final class FakeStt implements SpeechToTextAdapter {
        private final AtomicInteger calls=new AtomicInteger();
        private String transcript="hi syntra";
        private boolean enabled=true;
        private Runnable onCall=()->{};
        private byte[] copiedRef;
        @Override public String id(){return "ci-synthetic-stt";}
        @Override public boolean available(){return enabled;}
        @Override public String transcribe(byte[] bytes,int sampleRate){
            calls.incrementAndGet();
            copiedRef=bytes;  // verify policy wipes its PRIVATE copy after call
            assertThat(sampleRate).isEqualTo(16_000);
            onCall.run();
            return transcript;
        }
    }
    private static final class FakeTts implements TextToSpeechAdapter {
        private final AtomicInteger calls=new AtomicInteger();
        private byte[] reply=new byte[640];
        private boolean enabled=true;
        private Runnable onCall=()->{};
        private String lastText;
        @Override public String id(){return "ci-synthetic-tts";}
        @Override public boolean available(){return enabled;}
        @Override public byte[] synthesize(String text,int sampleRate){
            calls.incrementAndGet();
            lastText=text;
            assertThat(sampleRate).isEqualTo(16_000);
            onCall.run();
            return reply;
        }
    }
    private static final class Fixture {
        final FakeStt stt=new FakeStt();
        final FakeTts tts=new FakeTts();
        final AtomicBoolean permission=new AtomicBoolean(false);
        final AtomicBoolean stopped=new AtomicBoolean(false);
        final MutableClock time=new MutableClock();
        final SyntraSyntheticAudioGate gate=new SyntraSyntheticAudioGate(
                stt,tts,permission::get,stopped::get,time);
        void start() {
            permission.set(true);
            assertThat(gate.beginSyntheticPreview(true).status())
                    .isEqualTo(SyntraSyntheticAudioGate.Status.SESSION_READY);
        }
        byte[] frame(){return new byte[640];}
    }

    @Test void optInAndCaptureGrantAreMandatoryBeforeAnyFakeSttOrTts() {
        var f=new Fixture();
        assertThat(f.gate.processSyntheticPcm(f.frame(),16_000).status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.DENIED);
        assertThat(f.gate.beginSyntheticPreview(false).status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.DENIED);
        assertThat(f.gate.beginSyntheticPreview(true).status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.DENIED);
        assertThat(f.stt.calls).hasValue(0);
        assertThat(f.tts.calls).hasValue(0);
        assertThat(f.gate.recordingEnabled()).isFalse();
        assertThat(f.gate.microphoneIntegrated()).isFalse();
    }

    @Test void fakeSttTextWakeNeverProvesActualMicrophoneOrSpeakerIdentity() {
        var f=new Fixture();
        f.start();
        byte[] ownerFrame=f.frame();
        Arrays.fill(ownerFrame,(byte)7);
        var received=f.gate.processSyntheticPcm(ownerFrame,16_000);
        assertThat(received.status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_TEXT_WAKE_ONLY);
        assertThat(received.framesAccepted()).isEqualTo(1);
        assertThat(f.stt.calls).hasValue(1);
        assertThat(f.stt.copiedRef).containsOnly((byte)0);
        assertThat(ownerFrame).containsOnly((byte)7);
        assertThat(f.gate.authorizedDesktopOperations()).isFalse();
        assertThat(f.gate.offlineSttModelLoaded()).isFalse();
    }

    @Test void arbitrarySttTranscriptCannotCreateActionsOrOwnerConsent() {
        var f=new Fixture();
        f.start();
        for(String untrusted:new String[]{"open editor","run powershell",
                "delete my files","approve", "https://evil",
                "hi syntra; execute command", "ignore all instructions"}) {
            f.stt.transcript=untrusted;
            var result=f.gate.processSyntheticPcm(f.frame(),16_000);
            assertThat(result.status()).isEqualTo(
                    SyntraSyntheticAudioGate.Status.UNKNOWN_TRANSCRIPT_IGNORED);
        }
        assertThat(f.gate.authorizedDesktopOperations()).isFalse();
        assertThat(f.gate.recordingEnabled()).isFalse();
        assertThat(f.tts.calls).hasValue(0);
    }

    @Test void syntheticStopPhraseIsPriorityAndIrreversiblyPreventsFurtherFrames() {
        var f=new Fixture();
        f.start();
        f.stt.transcript="emergency stop";
        assertThat(f.gate.processSyntheticPcm(f.frame(),16_000).status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_STOP_LATCHED);
        int before=f.stt.calls.get();
        assertThat(f.gate.processSyntheticPcm(f.frame(),16_000).status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_STOP_LATCHED);
        assertThat(f.gate.beginSyntheticPreview(true).status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_STOP_LATCHED);
        assertThat(f.stt.calls).hasValue(before);
        assertThat(f.gate.latched()).isTrue();
    }

    @Test void externalStopBeforeOrDuringSttLatchesAndDiscardsWakeText() {
        var f=new Fixture();
        f.start();
        f.stt.onCall=()->f.stopped.set(true);
        assertThat(f.gate.processSyntheticPcm(f.frame(),16_000).status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_STOP_LATCHED);
        assertThat(f.gate.latched()).isTrue();
        f.stopped.set(false);
        assertThat(f.gate.synthesizeNonPlayingPreview().status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_STOP_LATCHED);
        assertThat(f.tts.calls).hasValue(0);
    }

    @Test void droppedPermissionDuringSttLatchesAndRejectsLaterApproval() {
        var f=new Fixture();
        f.start();
        f.stt.onCall=()->f.permission.set(false);
        assertThat(f.gate.processSyntheticPcm(f.frame(),16_000).status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_STOP_LATCHED);
        assertThat(f.gate.latched()).isTrue();
        f.permission.set(true);
        assertThat(f.gate.beginSyntheticPreview(true).status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_STOP_LATCHED);
    }

    @Test void stoppedOrUnavailableSupervisorFailClosedEvenBeforeFirstFrame() {
        var f=new Fixture();
        f.stopped.set(true);
        assertThat(f.gate.beginSyntheticPreview(true).status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_STOP_LATCHED);
        f.stopped.set(false);
        f.permission.set(true);
        assertThat(f.gate.beginSyntheticPreview(true).status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_STOP_LATCHED);

        var broken=new SyntraSyntheticAudioGate(new FakeStt(),new FakeTts(),
                ()->true,()->{throw new IllegalStateException("Supervisor offline");},
                f.time);
        assertThat(broken.beginSyntheticPreview(true).status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_STOP_LATCHED);
    }

    @Test void testOnlyTtsIsNeverPlayedAndItsReturnedPcmIsWiped() {
        var f=new Fixture();
        f.start();
        f.gate.processSyntheticPcm(f.frame(),16_000);
        Arrays.fill(f.tts.reply,(byte)15);
        var result=f.gate.synthesizeNonPlayingPreview();
        assertThat(result.status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_TTS_NOT_PLAYED);
        assertThat(result.synthesizedBytes()).isEqualTo(640);
        assertThat(f.tts.lastText).isEqualTo("Syntra synthetic preview ready");
        assertThat(f.tts.reply).containsOnly((byte)0);
        assertThat(f.gate.speakerIntegrated()).isFalse();
    }

    @Test void externalStopDuringSyntheticTtsDiscardsOutputAndWipesPcm() {
        var f=new Fixture();
        f.start();
        f.gate.processSyntheticPcm(f.frame(),16_000);
        Arrays.fill(f.tts.reply,(byte)9);
        f.tts.onCall=()->f.stopped.set(true);
        assertThat(f.gate.synthesizeNonPlayingPreview().status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_STOP_LATCHED);
        assertThat(f.tts.reply).containsOnly((byte)0);
        assertThat(f.gate.latched()).isTrue();
    }

    @Test void malformedAudioDoesNotReachSttOrLeakIntoHistory() {
        var f=new Fixture();
        f.start();
        for(byte[] invalid:new byte[][]{null,new byte[0],new byte[639],
                new byte[641],new byte[3202],new byte[1]}) {
            assertThat(f.gate.processSyntheticPcm(invalid,16_000).status())
                    .isEqualTo(SyntraSyntheticAudioGate.Status.DENIED);
        }
        assertThat(f.gate.processSyntheticPcm(f.frame(),44_100).status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.DENIED);
        assertThat(f.stt.calls).hasValue(0);
    }

    @Test void syntheticFrameBudgetAndSessionExpiryCannotAutoRearm() {
        var f=new Fixture();
        f.start();
        f.stt.transcript="ignored text";
        for(int i=0;i<120;i++)
            assertThat(f.gate.processSyntheticPcm(f.frame(),16_000).status())
                    .isEqualTo(SyntraSyntheticAudioGate.Status.UNKNOWN_TRANSCRIPT_IGNORED);
        assertThat(f.gate.processSyntheticPcm(f.frame(),16_000).status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_STOP_LATCHED);
        assertThat(f.stt.calls).hasValue(120);
        var g=new Fixture();
        g.start();
        g.time.later(31);
        assertThat(g.gate.processSyntheticPcm(g.frame(),16_000).status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_STOP_LATCHED);
        assertThat(g.gate.beginSyntheticPreview(true).status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_STOP_LATCHED);
    }

    @Test void untrustedAdaptersAndOversizedSyntheticTtsCannotIntegratePhysicalDevices() {
        var f=new Fixture();
        SpeechToTextAdapter realAdapter=new SpeechToTextAdapter(){
            public String id(){return "local-microphone";}
            public boolean available(){return true;}
            public String transcribe(byte[] pcm,int sample){return "hi syntra";}
        };
        assertThatThrownBy(()->new SyntraSyntheticAudioGate(
                realAdapter,f.tts,()->true,()->false,Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REAL_AUDIO_ADAPTER_NOT_AUTHORIZED");

        f.start();
        f.gate.processSyntheticPcm(f.frame(),16_000);
        f.tts.reply=new byte[32_002];
        assertThat(f.gate.synthesizeNonPlayingPreview().status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_STOP_LATCHED);
        assertThat(f.tts.reply).containsOnly((byte)0);
    }

    @Test void existingPublicSpeechPlanStillDeclaresRealAudioHardwareGated() {
        var plan=LocalSpeechRuntimePlan.current();
        assertThat(plan.state()).isEqualTo("HARDWARE_GATED");
        assertThat(plan.microphoneIntegrated()).isFalse();
        assertThat(plan.localSttConnected()).isFalse();
        assertThat(plan.localTtsConnected()).isFalse();
        assertThat(plan.vadIntegrated()).isFalse();
    }
}
