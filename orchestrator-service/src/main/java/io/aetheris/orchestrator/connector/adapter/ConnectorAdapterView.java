package io.aetheris.orchestrator.connector.adapter;

import java.time.Instant;
import java.util.UUID;

public record ConnectorAdapterView(
        UUID connectionId,
        ConnectorAdapterType adapterType,
        boolean secretConfigured,
        int maxClockSkewSeconds,
        Instant createdAt,
        Instant updatedAt
) {}
