package io.aetheris.orchestrator.voice;

import io.aetheris.orchestrator.stage9.LocalSpeechRuntimePlan;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Stage 46.1: real OFFLINE, local, typed interaction with the existing
 * Stage 45 consent policy. This runs as a console, never as a Spring
 * bean, HTTP endpoint, Java agent tool, daemon or workstation installer.
 *
 * This program has NO desktop, network, filesystem, shell or model
 * inference adapter. In particular, the ephemeral signing PRIVATE key
 * is discarded before the consent policy receives only the public key,
 * and no approval signer is exposed: proposals cannot be promoted to
 * an OS action (the policy itself only returns SIMULATED_ONLY anyway).
 *
 * The local owner label is deliberately a demo label, NOT proof of
 * human identity, biometric presence or production authorization.
 */
public final class SyntraLocalTextCompanion {
    private static final int MAX_LINE_CHARS = 120;
    private static final String DEMO_OWNER = "localDemoNoAuth";

    private SyntraLocalTextCompanion() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 0 && (args.length != 2 || !"--local-model".equals(args[0]))) {
            System.err.println("Usage: SyntraLocalTextCompanion [--local-model INSTALLED_MODEL_ID]");
            System.exit(2);
            return;
        }
        try (var reader = new InputStreamReader(System.in, StandardCharsets.UTF_8);
             var writer = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {
            if (args.length == 0) {
                run(reader, writer, () -> false);
            } else {
                try (var model = new SyntraLoopbackModelClient(args[1])) {
                    run(reader, writer, () -> false, model);
                }
            }
        }
    }

    /** Testable local console adapter. It deliberately cannot provide
     * OwnerPermit; no approval can be issued through spoken/typed text. */
    static void run(Reader input, Writer output, BooleanSupplier emergencyStop)
            throws IOException, GeneralSecurityException {
        run(input, output, emergencyStop, null);
    }

    /** Model responses are DISPLAY ONLY and never enter the consent approval path. */
    static void run(Reader input, Writer output, BooleanSupplier emergencyStop,
                    SyntraLoopbackModelClient localModel)
            throws IOException, GeneralSecurityException {
        Objects.requireNonNull(input);
        Objects.requireNonNull(output);
        Objects.requireNonNull(emergencyStop);
        var generator = KeyPairGenerator.getInstance("Ed25519");
        // Only public verifier material is passed; private ephemeral
        // key does not leave this method's temporary keypair result.
        var publicKey = generator.generateKeyPair().getPublic();
        var consent = new SyntheticVoiceOwnerConsentLab(
                publicKey, emergencyStop, Clock.systemUTC());
        UUID session;
        try {
            session = consent.openTrustedSession(DEMO_OWNER);
        } catch (SecurityException stopUnavailable) {
            output.write("STOP: unavailable or active; local console disabled.\n");
            output.flush();
            return;
        }

        var reader = new BufferedReader(input);
        boolean awake = false;
        final java.time.Instant sessionEnds = java.time.Instant.now().plusSeconds(300);
        if (localModel == null) {
            output.write("Syntra Local Text Preview — offline, no microphone, no AI model, no PC control.\n");
        } else {
            output.write("Syntra Local Model Preview — localhost inference OPT-IN; no microphone or PC control.\n");
            output.write("Local model configured: " + localModel.modelId() + " (availability not yet confirmed).\n");
        }
        output.write("Type 'help', 'hi syntra', 'status', 'stop', or 'quit'.\n");
        output.flush();

        while (true) {
            // No raw transcript echo, filesystem logging or telemetry.
            String line = nextBoundedLine(reader);
            if (line == null) return;
            if (line.length() > MAX_LINE_CHARS) {
                output.write("DENIED: command too long.\n");
                output.flush();
                continue;
            }
            String normalized = line.toLowerCase(Locale.ROOT).trim().replaceAll(" +", " ");
            if ("quit".equals(normalized) || "exit".equals(normalized)) {
                output.write("Syntra preview closed; nothing was installed or started.\n");
                output.flush();
                return;
            }
            if ("help".equals(normalized)) {
                output.write("Offline text preview: hi syntra | show system status | open editor | open project notes | status | stop | quit.\n");
                output.write("Desktop requests create proposals only; no approval or real action is possible here.\n");
                if (localModel != null)
                    output.write("After typed wake: models | model info | model health | clear context | model reset | ask YOUR_QUESTION (preinstalled Ollama on loopback only).\n");
                output.flush();
                continue;
            }

            // STOP state must be checked before any status or user-visible
            // proposal; it is irreversible for this console instance.
            if (consent.isSyntheticStopLatched()) {
                output.write("STOP: latched. Restarting this demo does not rearm any real worker.\n");
                output.flush();
                continue;
            }
            boolean sourceActive;
            try {
                sourceActive = emergencyStop.getAsBoolean();
            } catch (RuntimeException unavailable) {
                sourceActive = true;
            }
            if (sourceActive) {
                // A deliberate policy call observes and permanently
                // latches an external or unavailable STOP input.
                consent.receiveSyntheticTranscript(session, "status");
                output.write("STOP: external signal active or unavailable. New requests denied.\n");
                output.flush();
                continue;
            }

            if ("status".equals(normalized)) {
                LocalSpeechRuntimePlan plan = LocalSpeechRuntimePlan.current();
                output.write("STATUS: LOCAL_TEXT_PREVIEW; command execution OFF; installation OFF; network "
                        + (localModel == null ? "NOT USED." : "LOOPBACK OPT-IN ONLY.") + "\n");
                output.write("SPEECH: " + plan.state()
                        + "; microphone=" + plan.microphoneIntegrated()
                        + "; local-STT=" + plan.localSttConnected()
                        + "; local-TTS=" + plan.localTtsConnected() + ".\n");
                if (localModel != null)
                    output.write("MODEL: LOOPBACK_ONLY_OPT_IN; internet/proxies DISABLED; model loading unverified.\n");
                output.flush();
                continue;
            }

            if ("models".equals(normalized) || "model info".equals(normalized)) {
                if (localModel == null || !awake || !java.time.Instant.now().isBefore(sessionEnds)) {
                    output.write("DENIED: typed wake and explicit local-model opt-in required.\n");
                } else {
                    try {
                        if ("models".equals(normalized)) {
                            var inventory=localModel.listInstalledModels();
                            output.write("INSTALLED LOCAL MODELS: " + inventory.size() +
                                    " (read-only; no loading or automatic selection).\n");
                            for (var installed : inventory.stream().limit(10).toList())
                                output.write("MODEL ID: " + installed.name() + " (metadata only).\n");
                        } else {
                            var assessment=localModel.reviewSelectedModel();
                            output.write("MODEL REVIEW: " + assessment.model() + "; file bytes=" +
                                    assessment.modelDiskBytes() + "; SHA256 prefix=" +
                                    assessment.sha256Prefix() + ".\n");
                            output.write("HOST RAM FREE MiB: " +
                                    (assessment.hostMemory().freeBytes().isPresent()
                                    ? assessment.hostMemory().freeBytes().getAsLong()/1048576L
                                    : "unknown") + "; " + assessment.advisory() + ".\n");
                            output.write("GPU/VRAM: UNVERIFIED; loading/performance: NOT_CERTIFIED.\n");
                        }
                    } catch (SyntraLoopbackModelClient.ModelUnavailableException error) {
                        output.write("MODEL UNAVAILABLE: " + error.getMessage() + "; no remote fallback.\n");
                    }
                }
                output.flush();
                continue;
            }

            if ("model health".equals(normalized) || "model reset".equals(normalized)
                    || "clear context".equals(normalized)) {
                if (localModel == null || !awake || !java.time.Instant.now().isBefore(sessionEnds)) {
                    output.write("DENIED: typed wake and explicit local-model opt-in required.\n");
                } else if ("clear context".equals(normalized)) {
                    localModel.clearContext();
                    output.write("LOCAL CONTEXT CLEARED: session-only text history deleted; model digest still pinned.\n");
                } else if ("model reset".equals(normalized)) {
                    localModel.resetFailedSession();
                    output.write("MODEL RETRY RESET: transient failures cleared manually; model identity latch retained.\n");
                } else {
                    var health=localModel.health();
                    output.write("MODEL HEALTH: completed=" + health.completedReplies()
                            + "; consecutive failures=" + health.consecutiveFailures()
                            + "; circuit open=" + health.circuitOpen()
                            + "; model identity blocked=" + health.modelIdentityBlocked()
                            + "; last local attempt ms=" + health.lastAttemptMillis()
                            + "; context turns=" + health.retainedTurns() + ".\n");
                    output.write("LOCAL TIMING: synthetic-only until real weights tested; GPU/model-load certification=false.\n");
                }
                output.flush();
                continue;
            }

            if (normalized.startsWith("ask ")) {
                if (localModel == null || !awake || !java.time.Instant.now().isBefore(sessionEnds)) {
                    output.write("DENIED: typed wake and explicit local-model opt-in required.\n");
                } else {
                    String question = line.strip().substring(4);
                    try {
                        String answer = localModel.reply(question);
                        boolean stopAfter;
                        try { stopAfter = emergencyStop.getAsBoolean(); }
                        catch (RuntimeException unavailable) { stopAfter = true; }
                        if (stopAfter) {
                            consent.receiveSyntheticTranscript(session, "status");
                            output.write("STOP: local model answer discarded; no action executed.\n");
                        } else {
                            output.write("LOCAL MODEL (untrusted text, never an action): " + answer + "\n");
                        }
                    } catch (SyntraLoopbackModelClient.ModelUnavailableException unavailable) {
                        output.write("MODEL UNAVAILABLE: " + unavailable.getMessage() + "; no remote fallback.\n");
                    }
                }
                output.flush();
                continue;
            }

            var response = consent.receiveSyntheticTranscript(session, line);
            switch (response.status()) {
                case WAKE_ACKNOWLEDGED -> {
                    awake = true;
                    output.write("Syntra: text wake acknowledged. No microphone or speaker identity verified.\n");
                }
                case AWAITING_TRUSTED_OWNER_APPROVAL ->
                        output.write("PREVIEW ONLY: proposal recorded. Independent owner authorization required; no action will run.\n");
                case EMERGENCY_STOP_LATCHED ->
                        output.write("STOP: latched. Pending previews invalidated; no host process was started.\n");
                case SIMULATED_ONLY ->
                        output.write("SIMULATED ONLY: no desktop action executed.\n");
                case DENIED ->
                        output.write("DENIED: unsupported command, missing text wake, expired session or STOP.\n");
            }
            output.flush();
        }
    }

    /** Read and discard excess input rather than store unbounded lines;
     * keep reading through the newline so the next command is not spliced. */
    private static String nextBoundedLine(BufferedReader reader) throws IOException {
        var buffer = new StringBuilder(MAX_LINE_CHARS + 1);
        boolean any = false;
        int c;
        while ((c = reader.read()) != -1) {
            any = true;
            if (c == '\n') break;
            if (c == '\r') continue;
            if (buffer.length() <= MAX_LINE_CHARS) buffer.append((char) c);
        }
        return !any ? null : buffer.toString();
    }
}
