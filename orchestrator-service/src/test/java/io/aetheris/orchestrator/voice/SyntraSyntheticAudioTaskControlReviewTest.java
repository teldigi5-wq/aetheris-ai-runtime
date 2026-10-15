package io.aetheris.orchestrator.voice;

import io.aetheris.orchestrator.task.TaskControlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test-only actual TaskControlService STOP bridge. This class does NOT
 * register a real speech engine, microphone, speaker, REST API or OS tool.
 */
@SpringBootTest(properties="aetheris.memory.retention-sweep-ms=3600000")
class SyntraSyntheticAudioTaskControlReviewTest {
    @Autowired TaskControlService taskControl;

    private static final class TestStt implements SpeechToTextAdapter {
        private final Runnable beforeResponse;
        private TestStt(Runnable beforeResponse){this.beforeResponse=beforeResponse;}
        public String id(){return "ci-synthetic-stt";}
        public boolean available(){return true;}
        public String transcribe(byte[] pcm,int rate){
            beforeResponse.run();
            return "hi syntra";
        }
    }
    private static final class TestTts implements TextToSpeechAdapter {
        public String id(){return "ci-synthetic-tts";}
        public boolean available(){return true;}
        public byte[] synthesize(String text,int rate){return new byte[640];}
    }

    @Test void taskControlStopInterruptsSyntheticWakeAndCannotRearmAfterRelease() {
        var granted=new AtomicBoolean(true);
        var gate=new SyntraSyntheticAudioGate(
                new TestStt(()->taskControl.engage("synthetic speech STOP during fake STT")),
                new TestTts(),granted::get,taskControl::isEmergencyStopActive,Clock.systemUTC());
        try {
            assertThat(gate.beginSyntheticPreview(true).status())
                    .isEqualTo(SyntraSyntheticAudioGate.Status.SESSION_READY);
            assertThat(gate.processSyntheticPcm(new byte[640],16_000).status())
                    .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_STOP_LATCHED);
        } finally {
            taskControl.release("end test-only STOP, never rearm the old synthetic session");
        }
        assertThat(gate.latched()).isTrue();
        assertThat(gate.beginSyntheticPreview(true).status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_STOP_LATCHED);
        assertThat(gate.synthesizeNonPlayingPreview().status())
                .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_STOP_LATCHED);
    }

    @Test void taskControlEngagedBeforeSyntheticPermissionRefusesSession() {
        var gate=new SyntraSyntheticAudioGate(
                new TestStt(()->{}),new TestTts(),()->true,
                taskControl::isEmergencyStopActive,Clock.systemUTC());
        try {
            taskControl.engage("synthetic STOP before STT");
            assertThat(gate.beginSyntheticPreview(true).status())
                    .isEqualTo(SyntraSyntheticAudioGate.Status.SYNTHETIC_STOP_LATCHED);
        } finally {
            taskControl.release("restore service flag after synthetic test");
        }
        assertThat(gate.latched()).isTrue();
        assertThat(gate.recordingEnabled()).isFalse();
        assertThat(gate.authorizedDesktopOperations()).isFalse();
    }
}
