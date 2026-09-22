package io.aetheris.orchestrator.connector;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ConnectorConnectionView(
        UUID id,
        String ownerId,
        ConnectorProvider provider,
        String externalAccountRef,
        String displayName,
        ConnectorStatus status,
        Set<ConnectorCapability> capabilities,
        Instant createdAt,
        Instant updatedAt
) {}
