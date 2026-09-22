package io.aetheris.workstation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class HostAgentSecurityTest {
    @TempDir Path tempDir;

    @Test
    void signedEnvelopeIsAcceptedOnceAndReplayFailsClosed() throws Exception {
        UUID hostId = UUID.randomUUID();
        byte[] key = "stage11-host-agent-test-signing-key-0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        ReplayCache replay = new ReplayCache(tempDir.resolve("replay.txt"));
        Instant now = Instant.parse("2026-09-13T12:00:00Z");
        HostCommandEnvelope unsigned = new HostCommandEnvelope(UUID.randomUUID(), hostId, "system.telemetry", "snapshot",
                Map.of(), now.minusSeconds(1), now.plusSeconds(29), "placeholder", "REMOTE");
        HostCommandEnvelope signed = signed(unsigned, key);
        try (EnvelopeVerifier verifier = new EnvelopeVerifier(hostId, key, replay)) {
            assertDoesNotThrow(() -> verifier.verifyAndClaim(signed, now));
            SecurityException replayError = assertThrows(SecurityException.class, () -> verifier.verifyAndClaim(signed, now));
            assertTrue(replayError.getMessage().toLowerCase(Locale.ROOT).contains("replay"));
        }
    }

    @Test
    void wrongHostExpiredSignatureAndModeAreRejected() throws Exception {
        UUID hostId = UUID.randomUUID();
        byte[] key = "stage11-host-agent-test-signing-key-0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        Instant now = Instant.parse("2026-09-13T12:00:00Z");
        try (EnvelopeVerifier verifier = new EnvelopeVerifier(hostId, key, new ReplayCache(tempDir.resolve("replay2.txt")))) {
            HostCommandEnvelope wrongHost = signed(new HostCommandEnvelope(UUID.randomUUID(), UUID.randomUUID(), "system.telemetry", "snapshot",
                    Map.of(), now.minusSeconds(1), now.plusSeconds(20), "x", "REMOTE"), key);
            assertThrows(SecurityException.class, () -> verifier.verifyAndClaim(wrongHost, now));

            HostCommandEnvelope expired = signed(new HostCommandEnvelope(UUID.randomUUID(), hostId, "system.telemetry", "snapshot",
                    Map.of(), now.minusSeconds(40), now.minusSeconds(1), "x", "REMOTE"), key);
            assertThrows(SecurityException.class, () -> verifier.verifyAndClaim(expired, now));

            HostCommandEnvelope simulated = signed(new HostCommandEnvelope(UUID.randomUUID(), hostId, "system.telemetry", "snapshot",
                    Map.of(), now.minusSeconds(1), now.plusSeconds(20), "x", "SIMULATION"), key);
            assertThrows(SecurityException.class, () -> verifier.verifyAndClaim(simulated, now));

            HostCommandEnvelope badSignature = new HostCommandEnvelope(UUID.randomUUID(), hostId, "system.telemetry", "snapshot",
                    Map.of(), now.minusSeconds(1), now.plusSeconds(20), Base64.getEncoder().encodeToString("wrong".getBytes(StandardCharsets.UTF_8)), "REMOTE");
            assertThrows(SecurityException.class, () -> verifier.verifyAndClaim(badSignature, now));
        }
    }

    @Test
    void complexArgumentsAndLongTtlAreRejectedBeforeExecution() throws Exception {
        UUID hostId = UUID.randomUUID();
        byte[] key = "stage11-host-agent-test-signing-key-0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        Instant now = Instant.parse("2026-09-13T12:00:00Z");
        try (EnvelopeVerifier verifier = new EnvelopeVerifier(hostId, key, new ReplayCache(tempDir.resolve("replay3.txt")))) {
            HostCommandEnvelope nested = signed(new HostCommandEnvelope(UUID.randomUUID(), hostId, "process.status", "status",
                    Map.of("processName", Map.of("nested", true)), now.minusSeconds(1), now.plusSeconds(20), "x", "REMOTE"), key);
            assertThrows(IllegalArgumentException.class, () -> verifier.verifyAndClaim(nested, now));

            HostCommandEnvelope longTtl = signed(new HostCommandEnvelope(UUID.randomUUID(), hostId, "system.telemetry", "snapshot",
                    Map.of(), now.minusSeconds(1), now.plusSeconds(61), "x", "REMOTE"), key);
            assertThrows(SecurityException.class, () -> verifier.verifyAndClaim(longTtl, now));
        }
    }

    @Test
    void dispatcherExposesOnlyFourLeastPrivilegeCapabilities() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("workspace"));
        Path fakeApp = Files.writeString(tempDir.resolve("editor.exe"), "not-executed");
        AgentConfig config = new AgentConfig(UUID.randomUUID(), 17771, tempDir.resolve("agent.properties"),
                tempDir.resolve("key.dpapi"), List.of(root), Map.of("editor", fakeApp));
        LeastPrivilegeDispatcher dispatcher = new LeastPrivilegeDispatcher(config);
        Instant now = Instant.now();

        assertDoesNotThrow(() -> dispatcher.validate(envelope(config.hostId(), "system.telemetry", "snapshot", Map.of(), now)));
        assertDoesNotThrow(() -> dispatcher.validate(envelope(config.hostId(), "process.status", "status", Map.of("processName", "java.exe"), now)));
        assertDoesNotThrow(() -> dispatcher.validate(envelope(config.hostId(), "app.launch", "launch", Map.of("appAlias", "editor"), now)));
        assertDoesNotThrow(() -> dispatcher.validate(envelope(config.hostId(), "workspace.open", "open", Map.of("path", root.resolve("notes.md").toString()), now)));

        assertThrows(SecurityException.class, () -> dispatcher.validate(envelope(config.hostId(), "shell.exec", "run", Map.of("command", "whoami"), now)));
        assertThrows(SecurityException.class, () -> dispatcher.validate(envelope(config.hostId(), "system.telemetry", "snapshot", Map.of("extra", "x"), now)));
        assertThrows(SecurityException.class, () -> dispatcher.validate(envelope(config.hostId(), "process.status", "status", Map.of("processName", "powershell.exe"), now)));
        assertThrows(SecurityException.class, () -> dispatcher.validate(envelope(config.hostId(), "app.launch", "launch", Map.of("appAlias", "unknown"), now)));
        assertThrows(SecurityException.class, () -> dispatcher.validate(envelope(config.hostId(), "workspace.open", "open", Map.of("path", tempDir.resolve("outside.txt").toString()), now)));

        Map<String, Object> telemetry = dispatcher.execute(envelope(config.hostId(), "system.telemetry", "snapshot", Map.of(), now));
        assertEquals("OK", telemetry.get("status"));
        assertEquals("VENDOR_ADAPTER_NOT_CONNECTED", telemetry.get("gpuTelemetry"));
    }

    private HostCommandEnvelope envelope(UUID hostId, String capability, String action, Map<String, Object> args, Instant now) {
        return new HostCommandEnvelope(UUID.randomUUID(), hostId, capability, action, args,
                now.minusSeconds(1), now.plusSeconds(20), "unused-in-dispatcher-test", "REMOTE");
    }

    private HostCommandEnvelope signed(HostCommandEnvelope envelope, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(mac.doFinal(EnvelopeVerifier.canonical(envelope).getBytes(StandardCharsets.UTF_8)));
        return new HostCommandEnvelope(envelope.commandId(), envelope.hostId(), envelope.capability(), envelope.action(), envelope.arguments(),
                envelope.issuedAt(), envelope.expiresAt(), signature, envelope.mode());
    }
}
