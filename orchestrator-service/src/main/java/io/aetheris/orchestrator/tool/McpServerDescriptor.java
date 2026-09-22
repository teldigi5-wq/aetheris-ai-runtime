package io.aetheris.orchestrator.tool;

import java.net.URI;
import java.util.Set;

public record McpServerDescriptor(
        String id,
        String displayName,
        URI endpoint,
        boolean local,
        boolean enabled,
        Set<String> approvedCapabilities,
        Set<String> allowedDataClasses
) {
    public McpServerDescriptor {
        approvedCapabilities = approvedCapabilities == null ? Set.of() : Set.copyOf(approvedCapabilities);
        allowedDataClasses = allowedDataClasses == null ? Set.of() : Set.copyOf(allowedDataClasses);
    }
}
