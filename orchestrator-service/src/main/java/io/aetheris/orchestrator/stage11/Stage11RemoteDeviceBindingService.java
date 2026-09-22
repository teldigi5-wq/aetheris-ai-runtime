package io.aetheris.orchestrator.stage11;

import io.aetheris.orchestrator.remote.RemoteCompanionService;
import io.aetheris.orchestrator.remote.RemoteCompanionSessionEntity;
import io.aetheris.orchestrator.remote.RemoteCompanionSessionResponse;
import io.aetheris.orchestrator.stage10.Stage10RemoteTransportService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class Stage11RemoteDeviceBindingService {
    private static final Map<String, String> CAPABILITY_SCOPES = Map.of(
            "READ_STATUS", "status.read",
            "VIEW_NOTIFICATIONS", "notifications.view",
            "PAUSE_TASK", "task.pause",
            "STOP_TASK", "task.stop",
            "TAKE_CONTROL", "control.take");

    private final Stage10RemoteTransportService transport;
    private final RemoteCompanionService companion;
    private final Stage11RemoteBindingRepository bindings;

    public Stage11RemoteDeviceBindingService(Stage10RemoteTransportService transport,
                                             RemoteCompanionService companion,
                                             Stage11RemoteBindingRepository bindings) {
        this.transport = transport;
        this.companion = companion;
        this.bindings = bindings;
    }

    @Transactional
    public PairingResult pair(PairingRequest request) {
        if (request == null) throw new IllegalArgumentException("Pairing request is required");
        String fingerprint = fingerprint(request.certificateSha256());
        if (request.ttlMinutes() < 5 || request.ttlMinutes() > 60) {
            throw new IllegalArgumentException("Stage 11 remote session TTL must be between 5 and 60 minutes");
        }
        Set<String> requested = request.capabilities() == null || request.capabilities().isEmpty()
                ? Set.of("READ_STATUS") : new TreeSet<>(request.capabilities());
        for (String capability : requested) {
            if (!CAPABILITY_SCOPES.containsKey(capability)) throw new IllegalArgumentException("Unsupported Stage 11 remote capability: " + capability);
        }

        Stage10RemoteTransportService.TransportReadiness readiness = transport.evaluate(request.transportEvidence());
        if (!readiness.status().equals("READY_FOR_DEPLOYMENT_TEST")) {
            throw new IllegalStateException("Private transport gate is not ready: " + String.join("; ", readiness.blockers()));
        }
        for (String capability : requested) {
            String requiredScope = CAPABILITY_SCOPES.get(capability);
            if (!readiness.capabilities().contains(requiredScope)) {
                throw new IllegalArgumentException("Transport evidence does not grant required scope: " + requiredScope);
            }
        }

        RemoteCompanionSessionResponse session = companion.pair(requested, request.ttlMinutes());
        bindings.save(new Stage11RemoteBindingEntity(session.sessionId(), fingerprint));
        return new PairingResult(session.sessionId(), session.pairingToken(), session.expiresAt(), session.capabilities(),
                fingerprint, "READY_FOR_PRIVATE_TRANSPORT_INTEGRATION_TEST",
                "Session token is returned once; the durable binding stores only token hash in the existing session table and the device certificate fingerprint");
    }

    public AccessResult authenticate(UUID sessionId, String token, String capability, String certificateSha256) {
        Stage11RemoteBindingEntity binding = bindings.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Unknown Stage 11 remote device binding"));
        String presented = fingerprint(certificateSha256);
        if (!MessageDigest.isEqual(binding.getCertificateSha256().getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Remote device certificate fingerprint mismatch");
        }
        RemoteCompanionSessionEntity session = companion.authenticate(sessionId, token, capability);
        return new AccessResult(session.getId(), capability, session.getExpiresAt(), session.capabilities(),
                "AUTHENTICATED_CONTRACT_BOUND_SESSION",
                "Device fingerprint and existing token/capability/revocation checks passed; network TLS termination must still be enforced by the private transport layer");
    }

    @Transactional
    public void revoke(UUID sessionId) {
        companion.revoke(sessionId);
    }

    public List<BindingView> bindings() {
        return bindings.findAll().stream()
                .sorted(Comparator.comparing(Stage11RemoteBindingEntity::getCreatedAt).reversed())
                .map(x -> new BindingView(x.getSessionId(), x.getCertificateSha256(), x.getCreatedAt()))
                .toList();
    }

    private String fingerprint(String value) {
        String normalized = value == null ? "" : value.replace(":", "").trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Device certificate SHA-256 fingerprint is required");
        return normalized;
    }

    public record PairingRequest(String certificateSha256, Set<String> capabilities, int ttlMinutes,
                                 Stage10RemoteTransportService.RemoteTransportEvidence transportEvidence) {
        public PairingRequest { capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities); }
    }
    public record PairingResult(UUID sessionId, String pairingToken, Instant expiresAt, Set<String> capabilities,
                                String certificateSha256, String status, String detail) {}
    public record AccessResult(UUID sessionId, String capability, Instant expiresAt, Set<String> capabilities,
                               String status, String detail) {}
    public record BindingView(UUID sessionId, String certificateSha256, Instant createdAt) {}
}
