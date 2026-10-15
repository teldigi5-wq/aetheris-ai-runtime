package io.aetheris.orchestrator.voice;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Stage 46.7: bounded, in-memory SYNTHETIC voice-turn lifecycle.
 * Not registered with Spring or a device. Accepts caller-provided PCM only;
 * it cannot open microphones, play sound, invoke a tool or authenticate an owner.
 * Test-fixture permissions and engine IDs are NOT real owner/OS consent.
 */
public final class SyntraOfflineVoiceTurnCoordinator {
    public static final int SAMPLE_RATE_HZ = 16_000;
    private static final int MIN_FRAME_BYTES = 640;
    private static final int MAX_FRAME_BYTES = 3_200;
    private static final int MAX_UTTERANCE_BYTES = 32_000;
    private static final int MAX_SYNTHETIC_TTS_BYTES = 32_000;
    private static final int MAX_FRAMES = 120;
    private static final int SESSION_SECONDS = 30;
    private static final int VOICE_LEVEL = 1_024;

    public enum Outcome {
        DENIED, READY, LISTENING, SPEECH_BUFFERED, NOISE_DISCARDED,
        WAKE_ACKNOWLEDGED, STATUS_PREVIEW_READY, UNTRUSTED_TRANSCRIPT_IGNORED,
        SYNTHETIC_TTS_NOT_PLAYED, STOP_LATCHED
    }
    /** responseTicket is a one-use local correlation token, not consent. */
    public record Event(Outcome outcome, int framesSeen, long responseTicket, int syntheticAudioBytes) {
        public Event { Objects.requireNonNull(outcome); }
    }

    private final SpeechToTextAdapter stt;
    private final TextToSpeechAdapter tts;
    private final BooleanSupplier syntheticPermission;
    private final BooleanSupplier emergencyStop;
    private final Clock clock;
    private final byte[] utterance = new byte[MAX_UTTERANCE_BYTES];
    private int bufferedBytes;
    private int voicedFrames;
    private int quietFrames;
    private int framesSeen;
    private long sequence;
    private long pendingTicket;
    private boolean active;
    private boolean awake;
    private boolean latched;
    private Instant expires;

    public SyntraOfflineVoiceTurnCoordinator(SpeechToTextAdapter stt,
                                             TextToSpeechAdapter tts,
                                             BooleanSupplier syntheticPermission,
                                             BooleanSupplier emergencyStop,
                                             Clock clock) {
        this.stt = Objects.requireNonNull(stt);
        this.tts = Objects.requireNonNull(tts);
        this.syntheticPermission = Objects.requireNonNull(syntheticPermission);
        this.emergencyStop = Objects.requireNonNull(emergencyStop);
        this.clock = Objects.requireNonNull(clock);
        if (!"ci-synthetic-stt".equals(stt.id()) || !"ci-synthetic-tts".equals(tts.id()))
            throw new IllegalArgumentException("LIVE_SPEECH_ENGINE_NOT_AUTHORIZED");
    }

    /** Starts a single short-lived, owner-unverified fixture session. */
    public synchronized Event beginSyntheticSession(boolean explicitOptIn) {
        if (observeStop()) return event(Outcome.STOP_LATCHED);
        if (active || !explicitOptIn || !permission()) return event(Outcome.DENIED);
        try {
            if (!stt.available() || !tts.available()) return event(Outcome.DENIED);
        } catch (RuntimeException engineFailure) {
            latch();
            return event(Outcome.STOP_LATCHED);
        }
        active = true;
        expires = clock.instant().plusSeconds(SESSION_SECONDS);
        return event(Outcome.READY);
    }

    /** 16 kHz signed 16-bit little-endian mono fixture frames, 20-100 ms.
     * A simple amplitude threshold determines a TEST-ONLY turn boundary;
     * this is not a production VAD and cannot assess real acoustic quality.
     * We retain at most one second of private PCM, erased after STT.
     */
    public synchronized Event acceptSyntheticFrame(byte[] frame, int sampleRateHz) {
        if (!usable()) return event(latched ? Outcome.STOP_LATCHED : Outcome.DENIED);
        if (frame == null || sampleRateHz != SAMPLE_RATE_HZ ||
                frame.length < MIN_FRAME_BYTES || frame.length > MAX_FRAME_BYTES ||
                (frame.length & 1) != 0)
            return event(Outcome.DENIED);
        if (++framesSeen > MAX_FRAMES) {
            latch();
            return event(Outcome.STOP_LATCHED);
        }
        if (voiced(frame)) {
            if (bufferedBytes + frame.length > utterance.length) {
                latch();
                return event(Outcome.STOP_LATCHED);
            }
            // A new utterance makes the previous unplayed reply stale.
            pendingTicket = 0;
            System.arraycopy(frame, 0, utterance, bufferedBytes, frame.length);
            bufferedBytes += frame.length;
            voicedFrames++;
            quietFrames = 0;
            return event(Outcome.SPEECH_BUFFERED);
        }
        if (voicedFrames == 0) return event(Outcome.LISTENING);
        if (++quietFrames < 2) return event(Outcome.SPEECH_BUFFERED);
        if (voicedFrames < 2) {
            eraseUtterance();
            return event(Outcome.NOISE_DISCARDED);
        }
        return closeTurn();
    }

    /** No PCM is returned. Only one fixed, safe preview phrase can be
     * passed to a fake TTS; the generated array is erased, not played. */
    public synchronized Event synthesizeNonPlayingStatus(long ticket) {
        if (!usable()) return event(latched ? Outcome.STOP_LATCHED : Outcome.DENIED);
        if (ticket <= 0 || ticket != pendingTicket) return event(Outcome.DENIED);
        pendingTicket = 0; // Consume before running untrusted fake adapter.
        byte[] generated = null;
        try {
            generated = tts.synthesize("Syntra status preview. Real voice and desktop control are disabled.",
                    SAMPLE_RATE_HZ);
            if (!usable()) return event(Outcome.STOP_LATCHED);
            if (generated == null || generated.length < 2 ||
                    generated.length > MAX_SYNTHETIC_TTS_BYTES || (generated.length & 1) != 0) {
                latch();
                return event(Outcome.STOP_LATCHED);
            }
            return new Event(Outcome.SYNTHETIC_TTS_NOT_PLAYED, framesSeen, 0, generated.length);
        } catch (RuntimeException adapterFailure) {
            latch();
            return event(Outcome.STOP_LATCHED);
        } finally {
            if (generated != null) Arrays.fill(generated, (byte) 0);
        }
    }

    /** Irreversible for this instance; DOES NOT stop other applications. */
    public synchronized Event stopSyntheticSession() {
        latch();
        return event(Outcome.STOP_LATCHED);
    }

    public synchronized boolean stopLatched() { return latched; }
    public boolean microphoneIntegrated() { return false; }
    public boolean speakerIntegrated() { return false; }
    public boolean modelWeightsLoaded() { return false; }
    public boolean ownerAuthenticated() { return false; }
    public boolean desktopCommandsAuthorized() { return false; }
    public boolean recordingEnabled() { return false; }

    private Event closeTurn() {
        byte[] privateCopy = Arrays.copyOf(utterance, bufferedBytes);
        eraseUtterance();
        try {
            String transcript = stt.transcribe(privateCopy, SAMPLE_RATE_HZ);
            if (!usable()) return event(Outcome.STOP_LATCHED);
            String normalized = normalize(transcript);
            if (isStop(normalized)) {
                latch();
                return event(Outcome.STOP_LATCHED);
            }
            if (isWake(normalized)) {
                awake = true;
                pendingTicket = 0;
                return event(Outcome.WAKE_ACKNOWLEDGED);
            }
            if (awake && ("status".equals(normalized) || "show system status".equals(normalized))) {
                pendingTicket = ++sequence;
                return new Event(Outcome.STATUS_PREVIEW_READY, framesSeen, pendingTicket, 0);
            }
            return event(Outcome.UNTRUSTED_TRANSCRIPT_IGNORED);
        } catch (RuntimeException adapterFailure) {
            latch();
            return event(Outcome.STOP_LATCHED);
        } finally {
            Arrays.fill(privateCopy, (byte) 0);
        }
    }

    private static boolean voiced(byte[] frame) {
        int above = 0;
        for (int i = 0; i < frame.length; i += 2) {
            int value = (short) (((frame[i + 1] & 0xff) << 8) | (frame[i] & 0xff));
            if (Math.abs(value) >= VOICE_LEVEL) above++;
        }
        // At least 10% of samples over threshold; deterministic fixture heuristic.
        return above >= (frame.length / 2 + 9) / 10;
    }

    private boolean usable() {
        if (observeStop()) return false;
        if (!active) return false;
        if (!permission() || !clock.instant().isBefore(expires)) {
            latch();
            return false;
        }
        return true;
    }

    private boolean observeStop() {
        if (latched) return true;
        boolean stopped;
        try { stopped = emergencyStop.getAsBoolean(); }
        catch (RuntimeException unavailable) { stopped = true; }
        if (stopped) latch();
        return latched;
    }

    private boolean permission() {
        try { return syntheticPermission.getAsBoolean(); }
        catch (RuntimeException unavailable) { return false; }
    }

    private void latch() {
        latched = true;
        active = false;
        awake = false;
        expires = null;
        pendingTicket = 0;
        eraseUtterance();
    }

    private void eraseUtterance() {
        Arrays.fill(utterance, (byte) 0);
        bufferedBytes = 0;
        voicedFrames = 0;
        quietFrames = 0;
    }

    private Event event(Outcome outcome) {
        return new Event(outcome, framesSeen, 0, 0);
    }

    private static String normalize(String transcript) {
        if (transcript == null || transcript.length() > 80 ||
                !transcript.matches("[A-Za-z ]{1,80}")) return "";
        return transcript.toLowerCase(Locale.ROOT).trim().replaceAll(" +", " ");
    }
    private static boolean isStop(String text) {
        return switch (text) {
            case "stop", "emergency stop", "stop everything", "stop syntra", "stop sintra",
                    "syntra stop", "sintra stop" -> true;
            default -> false;
        };
    }
    private static boolean isWake(String text) {
        return switch (text) {
            case "syntra", "sintra", "hi syntra", "hi sintra", "hey syntra", "hey sintra" -> true;
            default -> false;
        };
    }
}
