package io.aetheris.orchestrator.voice;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Stage 46.5: IN-MEMORY SYNTHETIC audio-policy prototype, not a voice engine.
 * No microphone capture, speakers, OS/device API, network, model download,
 * recording, command execution, Spring registration, file writing or tools.
 *
 * Accepts only caller-supplied CI-style fake PCM and fake adapters with
 * exact synthetic IDs. This class cannot access physical microphone data
 * without some OTHER separately approved component handing it bytes.
 * Neither voice text nor simulated output authorizes desktop operations.
 */
public final class SyntraSyntheticAudioGate {
    public static final int SAMPLE_RATE_HZ = 16_000;
    public static final int MIN_PCM_BYTES = 640;   // 20 ms 16-bit mono
    public static final int MAX_PCM_BYTES = 3_200; // 100 ms 16-bit mono
    private static final int MAX_FRAMES = 120;
    private static final int MAX_SPEECH_BYTES = 32_000; // max 1s synthetic 16kHz mono
    private static final int MAX_SECONDS = 30;

    public enum Status {
        SESSION_READY, DENIED, UNKNOWN_TRANSCRIPT_IGNORED,
        SYNTHETIC_TEXT_WAKE_ONLY, SYNTHETIC_TTS_NOT_PLAYED,
        SYNTHETIC_STOP_LATCHED
    }
    public record Result(Status status, int framesAccepted, int synthesizedBytes) {
        public Result { Objects.requireNonNull(status); }
    }

    private final SpeechToTextAdapter stt;
    private final TextToSpeechAdapter tts;
    private final BooleanSupplier captureGrant;
    private final BooleanSupplier globalStop;
    private final Clock clock;
    private boolean sessionStarted;
    private boolean blockedForever;
    private boolean awake;
    private int frames;
    private Instant deadline;

    /**
     * Grants here are test-fixture booleans, NOT owner authentication or
     * actual OS microphone permission. Tests can toggle them at runtime.
     */
    public SyntraSyntheticAudioGate(SpeechToTextAdapter stt,
                                   TextToSpeechAdapter tts,
                                   BooleanSupplier syntheticCaptureGrant,
                                   BooleanSupplier emergencyStop,
                                   Clock clock) {
        this.stt = Objects.requireNonNull(stt);
        this.tts = Objects.requireNonNull(tts);
        this.captureGrant = Objects.requireNonNull(syntheticCaptureGrant);
        this.globalStop = Objects.requireNonNull(emergencyStop);
        this.clock = Objects.requireNonNull(clock);
        // Exact CI fixture IDs. Do not turn a registered production STT
        // adapter into automatic recording or streaming integration.
        if (!"ci-synthetic-stt".equals(stt.id()) ||
                !"ci-synthetic-tts".equals(tts.id())) {
            throw new IllegalArgumentException("REAL_AUDIO_ADAPTER_NOT_AUTHORIZED");
        }
    }

    /** Test-only opt-in, NOT real microphone consent or an owner permit. */
    public synchronized Result beginSyntheticPreview(boolean explicitOptIn) {
        if (guardStop()) return result(Status.SYNTHETIC_STOP_LATCHED);
        if (sessionStarted || !explicitOptIn || !permissionPresent())
            return result(Status.DENIED);
        try {
            if (!stt.available() || !tts.available())
                return result(Status.DENIED);
        } catch (RuntimeException failure) {
            latch();
            return result(Status.SYNTHETIC_STOP_LATCHED);
        }
        sessionStarted = true;
        deadline = clock.instant().plusSeconds(MAX_SECONDS);
        return result(Status.SESSION_READY);
    }

    /** Caller keeps ownership of input. We copy a bounded frame and wipe
     * that copy even when STT throws; raw audio is never retained. */
    public synchronized Result processSyntheticPcm(byte[] externalPcm, int rate) {
        if (!usable()) return result(blockedForever
                ? Status.SYNTHETIC_STOP_LATCHED : Status.DENIED);
        if (externalPcm == null || rate != SAMPLE_RATE_HZ ||
                externalPcm.length < MIN_PCM_BYTES ||
                externalPcm.length > MAX_PCM_BYTES ||
                (externalPcm.length & 1) != 0)
            return result(Status.DENIED);
        if (frames >= MAX_FRAMES) {
            latch();
            return result(Status.SYNTHETIC_STOP_LATCHED);
        }
        byte[] copy = externalPcm.clone();
        try {
            frames++;
            String text = stt.transcribe(copy, SAMPLE_RATE_HZ);
            if (!usable()) return result(Status.SYNTHETIC_STOP_LATCHED);
            if (text == null || text.length() > 80 ||
                    !text.matches("[A-Za-z ]{1,80}"))
                return result(Status.UNKNOWN_TRANSCRIPT_IGNORED);
            String normalized = text.trim().toLowerCase(Locale.ROOT)
                    .replaceAll(" +", " ");
            if (isStop(normalized)) {
                latch();
                return result(Status.SYNTHETIC_STOP_LATCHED);
            }
            if (isSyntheticWake(normalized)) {
                awake = true;
                return result(Status.SYNTHETIC_TEXT_WAKE_ONLY);
            }
            // No general text/command forwarding from STT. This is
            // intentionally NOT connected to LocalTextCompanion.
            return result(Status.UNKNOWN_TRANSCRIPT_IGNORED);
        } catch (RuntimeException invalidAdapter) {
            latch();
            return result(Status.SYNTHETIC_STOP_LATCHED);
        } finally {
            Arrays.fill(copy, (byte) 0);
        }
    }

    /** Generates a fixed synthetic phrase but never plays or returns
     * PCM to a device. A result contains only the byte count. */
    public synchronized Result synthesizeNonPlayingPreview() {
        if (!usable()) return result(blockedForever
                ? Status.SYNTHETIC_STOP_LATCHED : Status.DENIED);
        if (!awake) return result(Status.DENIED);
        byte[] generated = null;
        try {
            generated = tts.synthesize("Syntra synthetic preview ready",
                    SAMPLE_RATE_HZ);
            if (!usable()) return result(Status.SYNTHETIC_STOP_LATCHED);
            if (generated == null || generated.length < 2 ||
                    generated.length > MAX_SPEECH_BYTES ||
                    (generated.length & 1) != 0) {
                latch();
                return result(Status.SYNTHETIC_STOP_LATCHED);
            }
            return new Result(Status.SYNTHETIC_TTS_NOT_PLAYED,
                    frames, generated.length);
        } catch (RuntimeException invalidAdapter) {
            latch();
            return result(Status.SYNTHETIC_STOP_LATCHED);
        } finally {
            if (generated != null) Arrays.fill(generated, (byte) 0);
        }
    }

    /** Synthetic priority STOP; never unlocks within this instance.
     * This stops ONLY this test gate; it does NOT kill Ollama or apps. */
    public synchronized Result stopSyntheticPreview() {
        latch();
        return result(Status.SYNTHETIC_STOP_LATCHED);
    }

    public synchronized boolean latched() { return blockedForever; }

    public synchronized boolean microphoneIntegrated() { return false; }
    public synchronized boolean offlineSttModelLoaded() { return false; }
    public synchronized boolean offlineTtsModelLoaded() { return false; }
    public synchronized boolean speakerIntegrated() { return false; }
    public synchronized boolean authorizedDesktopOperations() { return false; }
    public synchronized boolean recordingEnabled() { return false; }

    private boolean usable() {
        if (guardStop()) return false;
        if (!sessionStarted) return false;
        if (!permissionPresent()) {
            latch();
            return false;
        }
        if (!clock.instant().isBefore(deadline)) {
            latch();
            return false;
        }
        return true;
    }

    private boolean guardStop() {
        if (blockedForever) return true;
        boolean stopped;
        try { stopped = globalStop.getAsBoolean(); }
        catch (RuntimeException unavailable) { stopped = true; }
        if (stopped) latch();
        return blockedForever;
    }

    private boolean permissionPresent() {
        try { return captureGrant.getAsBoolean(); }
        catch (RuntimeException unavailable) { return false; }
    }

    private void latch() {
        blockedForever = true;
        awake = false;
        deadline = null;
    }

    private Result result(Status status) { return new Result(status, frames, 0); }

    private static boolean isSyntheticWake(String text) {
        return switch (text) {
            case "syntra", "sintra", "hi syntra", "hi sintra",
                    "hey syntra", "hey sintra" -> true;
            default -> false;
        };
    }

    private static boolean isStop(String text) {
        return switch (text) {
            case "stop", "stop syntra", "stop sintra",
                    "syntra stop", "sintra stop",
                    "stop everything", "emergency stop" -> true;
            default -> false;
        };
    }
}
