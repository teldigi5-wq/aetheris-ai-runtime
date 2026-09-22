package io.aetheris.orchestrator.host;

import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.task.DirectExecutionAuthorityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
public class HostCommandService {
    private static final String REMOTE_APPROVAL_ACTION = "host:command";
    private final HostRegistryService hosts;
    private final DirectExecutionAuthorityService authority;
    private final ApprovalService approvals;
    private final String signingKey;
    private final boolean simulationOnly;

    public HostCommandService(
            HostRegistryService hosts,
            DirectExecutionAuthorityService authority,
            ApprovalService approvals,
            @Value("${aetheris.host.signing-key:}") String signingKey,
            @Value("${aetheris.host.simulation-only:true}") boolean simulationOnly) {
        this.hosts = hosts;
        this.authority = authority;
        this.approvals = approvals;
        this.signingKey = signingKey == null ? "" : signingKey;
        this.simulationOnly = simulationOnly;
    }

    public HostCommandEnvelope issue(HostCommandRequest request) {
        hosts.requireExecutable(request.hostId());
        HostNodeEntity host = hosts.getRequired(request.hostId());
        if (!host.getCapabilities().contains(request.capability())) {
            throw new IllegalArgumentException("Host capability is not declared: " + request.capability());
        }
        if (signingKey.length() < 32) throw new IllegalStateException("Host command signing key is not configured");

        if (!simulationOnly) {
            authority.requireRunningSpecialist(request.taskId(), request.agentId(), "pc-control");
            if (!approvals.hasApproved(request.taskId(), REMOTE_APPROVAL_ACTION)) {
                throw new IllegalStateException("Explicit owner approval is required for " + REMOTE_APPROVAL_ACTION);
            }
        }

        UUID commandId = UUID.randomUUID();
        Instant issued = Instant.now();
        Instant expires = issued.plusSeconds(30);
        Map<String,Object> args = request.arguments() == null ? Map.of() : new TreeMap<>(request.arguments());
        String canonical = commandId + "|" + request.hostId() + "|" + request.capability() + "|" + request.action()
                + "|" + issued + "|" + expires + "|" + args;
        String signature = hmac(canonical);
        return new HostCommandEnvelope(commandId, request.hostId(), request.capability(), request.action(), args,
                issued, expires, signature, simulationOnly ? "SIMULATION" : "REMOTE");
    }

    public Map<String,Object> simulate(HostCommandEnvelope envelope) {
        if (!simulationOnly) throw new IllegalStateException("Simulation endpoint is disabled");
        hosts.requireExecutable(envelope.hostId());
        if (Instant.now().isAfter(envelope.expiresAt())) throw new IllegalStateException("Host command envelope expired");
        String canonical = envelope.commandId() + "|" + envelope.hostId() + "|" + envelope.capability() + "|"
                + envelope.action() + "|" + envelope.issuedAt() + "|" + envelope.expiresAt() + "|"
                + new TreeMap<>(envelope.arguments());
        if (!MessageDigest.isEqual(signatureBytes(envelope.signature()), signatureBytes(hmac(canonical)))) {
            throw new IllegalArgumentException("Host command signature mismatch");
        }
        return Map.of("status", "SIMULATED", "commandId", envelope.commandId().toString(), "action", envelope.action(),
                "capability", envelope.capability(), "detail", "No operating-system action was executed");
    }

    public HostTelemetrySnapshot simulatedTelemetry(UUID hostId) {
        hosts.requireExecutable(hostId);
        return new HostTelemetrySnapshot(hostId, Instant.now(), 0, 0, 0, 0, 0, "SIMULATION");
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign host command", e);
        }
    }

    private byte[] signatureBytes(String value) {
        try { return Base64.getDecoder().decode(value); }
        catch (Exception e) { return new byte[0]; }
    }
}
