package io.aetheris.orchestrator.tool;

import io.aetheris.orchestrator.agent.RiskLevel;

import java.util.Set;

public record ToolDescriptor(
        String id,
        String displayName,
        ToolTransport transport,
        Set<String> scopes,
        RiskLevel riskLevel,
        boolean readOnly,
        boolean credentialRequired
) {
    public ToolDescriptor {
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }
}
