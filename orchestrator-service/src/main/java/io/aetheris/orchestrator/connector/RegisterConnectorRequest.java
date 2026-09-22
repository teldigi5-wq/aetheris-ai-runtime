package io.aetheris.orchestrator.connector;

import java.util.Set;

public record RegisterConnectorRequest(
        String ownerId,
        ConnectorProvider provider,
        String externalAccountRef,
        String displayName,
        Set<ConnectorCapability> capabilities
) {}
