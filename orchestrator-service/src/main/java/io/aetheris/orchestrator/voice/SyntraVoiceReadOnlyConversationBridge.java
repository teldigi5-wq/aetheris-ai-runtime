package io.aetheris.orchestrator.voice;

import io.aetheris.orchestrator.stage9.LocalSpeechRuntimePlan;

import java.util.Objects;

/**
 * Stage 46.8: non-executing, in-memory, read-only conversation projection.
 *
 * The underlying coordinator accepts caller-supplied synthetic PCM with
 * fixture-only consent. NO actual microphone, wake/STT/TTS engine, speaker,
 * HTTP, Spring registration, model inference, filesystem or desktop action.
 *
 * Only two fixed read-only conversational intents are projected: help and
 * the existing LocalSpeechRuntimePlan status. There is no method for a
 * transcript to create an OS command, an owner permit, or a model prompt.
 * Synthetic adapter IDs/permission booleans do not prove physical provenance
 * or authenticate an owner. STOP is only a local preview latch.
 */
public final class SyntraVoiceReadOnlyConversationBridge {
    public enum Delivery {
        NO_REPLY, SESSION_READY, WAKE_PREVIEW,
        READ_ONLY_STATUS, READ_ONLY_HELP, STOP_LATCHED
    }

    /** A bounded text response, never an untrusted transcript or audio. */
    public record Reply(Delivery delivery, String text, int framesSeen) {
        public Reply {
            Objects.requireNonNull(delivery);
            Objects.requireNonNull(text);
            if (text.length() > 320 || framesSeen < 0)
                throw new IllegalArgumentException("INVALID_READ_ONLY_REPLY");
        }
    }

    private static final String WAKE_TEXT =
            "Syntra synthetic preview awake. Real microphone, AI voice and desktop actions are disabled.";
    private static final String HELP_TEXT =
            "Synthetic preview commands: Hi Syntra, status, help, emergency stop. " +
            "No microphone, live speech engine, autonomous actions or owner approval.";
    private static final String STOP_TEXT =
            "Synthetic STOP latched. No more replies until a separately created preview session.";

    private final SyntraOfflineVoiceTurnCoordinator coordinator;

    public SyntraVoiceReadOnlyConversationBridge(SyntraOfflineVoiceTurnCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator);
    }

    public synchronized Reply beginSyntheticSession(boolean explicitFixtureOptIn) {
        var result = coordinator.beginSyntheticSession(explicitFixtureOptIn);
        return switch (result.outcome()) {
            case READY -> new Reply(Delivery.SESSION_READY,
                    "Synthetic preview enabled; no physical recording or device access.", result.framesSeen());
            case STOP_LATCHED -> stopReply(result.framesSeen());
            default -> silent(result.framesSeen());
        };
    }

    /** Frames always pass through the existing bounded synthetic coordinator.
     * The bridge never receives or re-emits an STT transcript. */
    public synchronized Reply acceptSyntheticFrame(byte[] callerOwnedPcm, int sampleRateHz) {
        var event = coordinator.acceptSyntheticFrame(callerOwnedPcm, sampleRateHz);
        return switch (event.outcome()) {
            case WAKE_ACKNOWLEDGED ->
                    validatedReply(Delivery.WAKE_PREVIEW, WAKE_TEXT, event.framesSeen());
            case STATUS_PREVIEW_READY, HELP_PREVIEW_READY ->
                    consumeAndProject(event);
            case STOP_LATCHED -> stopReply(event.framesSeen());
            default -> silent(event.framesSeen());
        };
    }

    /** Local synthetic STOP, not a host kill switch or production interrupt. */
    public synchronized Reply stopSyntheticSession() {
        var event = coordinator.stopSyntheticSession();
        return stopReply(event.framesSeen());
    }

    public boolean microphoneIntegrated() { return false; }
    public boolean speakerIntegrated() { return false; }
    public boolean executableCommandsEnabled() { return false; }
    public boolean recordingEnabled() { return false; }
    public boolean liveModelConversationEnabled() { return false; }

    private Reply consumeAndProject(SyntraOfflineVoiceTurnCoordinator.Event event) {
        var checked = coordinator.consumeReadOnlyPreview(event.responseTicket(), event.outcome());
        if (checked.outcome() == SyntraOfflineVoiceTurnCoordinator.Outcome.STOP_LATCHED)
            return stopReply(checked.framesSeen());
        if (checked.outcome() != event.outcome())
            return silent(event.framesSeen());

        String text;
        Delivery delivery;
        if (event.outcome() == SyntraOfflineVoiceTurnCoordinator.Outcome.STATUS_PREVIEW_READY) {
            LocalSpeechRuntimePlan plan = LocalSpeechRuntimePlan.current();
            text = "Syntra local voice preview: " + plan.state() +
                    "; microphone integrated=" + plan.microphoneIntegrated() +
                    "; local STT connected=" + plan.localSttConnected() +
                    "; local TTS connected=" + plan.localTtsConnected() +
                    "; desktop execution disabled in this preview.";
            delivery = Delivery.READ_ONLY_STATUS;
        } else {
            text = HELP_TEXT;
            delivery = Delivery.READ_ONLY_HELP;
        }
        // An independent STOP signal or lost synthetic fixture permission
        // observed after ticket consumption must suppress late output.
        return validatedReply(delivery, text, event.framesSeen());
    }

    private Reply validatedReply(Delivery delivery, String safeText, int frames) {
        if (!coordinator.canDeliverReadOnlyReply())
            return coordinator.stopLatched() ? stopReply(frames) : silent(frames);
        return new Reply(delivery, safeText, frames);
    }

    private static Reply silent(int frames) {
        return new Reply(Delivery.NO_REPLY, "", frames);
    }

    private static Reply stopReply(int frames) {
        return new Reply(Delivery.STOP_LATCHED, STOP_TEXT, frames);
    }
}
