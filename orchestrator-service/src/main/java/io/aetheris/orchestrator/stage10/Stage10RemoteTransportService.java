package io.aetheris.orchestrator.stage10;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Stage10RemoteTransportService {
    public TransportReadiness evaluate(RemoteTransportEvidence evidence) {
        if (evidence == null) throw new IllegalArgumentException("Remote transport evidence is required");
        List<String> blockers = new ArrayList<>();
        if (!"TLS1.3".equalsIgnoreCase(compact(evidence.tlsVersion()))) blockers.add("TLS 1.3 is required");
        if (!evidence.mutualAuthentication()) blockers.add("mutual device authentication is required");
        if (!evidence.privateNetworkOrTunnel()) blockers.add("private network or authenticated tunnel is required");
        if (!evidence.devicePaired()) blockers.add("device pairing is not active");
        if (!evidence.revocationChecked()) blockers.add("revocation state was not checked");
        if (evidence.capabilities() == null || evidence.capabilities().isEmpty()) blockers.add("at least one narrow capability scope is required");
        if (evidence.rawPublicAgentApi()) blockers.add("raw public general-purpose agent API is forbidden");
        if (evidence.sessionCredentialAgeMinutes() < 0 || evidence.sessionCredentialAgeMinutes() > 60) blockers.add("remote session credential must be short-lived (<= 60 minutes)");
        Set<String> normalized = new TreeSet<>();
        if (evidence.capabilities() != null) {
            for (String capability : evidence.capabilities()) {
                if (capability == null || !capability.trim().matches("[A-Za-z0-9._:-]{1,80}")) blockers.add("invalid remote capability scope");
                else normalized.add(capability.trim().toLowerCase(Locale.ROOT));
            }
        }
        return new TransportReadiness(blockers.isEmpty() ? "READY_FOR_DEPLOYMENT_TEST" : "BLOCKED",
                List.copyOf(blockers), Set.copyOf(normalized), false,
                blockers.isEmpty() ? "Policy evidence satisfies the Stage 10 private-transport gate; this does not claim a tunnel is currently deployed"
                        : "Production remote execution remains disabled until every transport gate passes");
    }

    private String compact(String value) {
        return value == null ? "" : value.replace(" ", "").replace("_", "").replace("-", "");
    }

    public record RemoteTransportEvidence(String tlsVersion, boolean mutualAuthentication, boolean privateNetworkOrTunnel,
                                          boolean devicePaired, boolean revocationChecked, Set<String> capabilities,
                                          boolean rawPublicAgentApi, long sessionCredentialAgeMinutes) {}
    public record TransportReadiness(String status, List<String> blockers, Set<String> capabilities,
                                     boolean rawPublicAgentApiAllowed, String detail) {}
}
