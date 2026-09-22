package io.aetheris.orchestrator.mcp;

import java.util.Set;

public record McpServerRegistrationRequest(
        String serverKey,
        String displayName,
        String endpoint,
        boolean local,
        boolean enabled,
        Set<String> approvedCapabilities,
        Set<String> allowedDataClasses
) {
    public McpServerRegistrationRequest {
        approvedCapabilities = approvedCapabilities == null ? Set.of() : Set.copyOf(approvedCapabilities);
        allowedDataClasses = allowedDataClasses == null ? Set.of() : Set.copyOf(allowedDataClasses);
    }
}
