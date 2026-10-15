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
        if (args.length != 0) {
            System.err.println("Usage: SyntraLocalTextCompanion (no arguments; offline demo only)");
            System.exit(2);
            return;
        }
        // No persistent keys, approvals, model downloads or OS operations.
        try (var reader = new InputStreamReader(System.in, StandardCharsets.UTF_8);
             var writer = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {
            run(reader, writer, () -> false);
        }
    }

    /** Testable local console adapter. It deliberately cannot provide
     * OwnerPermit; no approval can be issued through spoken/typed text. */
    static void run(Reader input, Writer output, BooleanSupplier emergencyStop)
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
        output.write("Syntra Local Text Preview — offline, no microphone, no AI model, no PC control.\n");
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
                output.write("STATUS: LOCAL_TEXT_PREVIEW; command execution OFF; installation OFF; network NOT USED.\n");
                output.write("SPEECH: " + plan.state()
                        + "; microphone=" + plan.microphoneIntegrated()
                        + "; local-STT=" + plan.localSttConnected()
                        + "; local-TTS=" + plan.localTtsConnected() + ".\n");
                output.flush();
                continue;
            }

            var response = consent.receiveSyntheticTranscript(session, line);
            switch (response.status()) {
                case WAKE_ACKNOWLEDGED ->
                        output.write("Syntra: text wake acknowledged. No microphone or speaker identity verified.\n");
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
