package io.aetheris.orchestrator.connector.oauth;

import io.aetheris.orchestrator.connector.ConnectorProvider;

import java.time.Instant;
import java.util.UUID;

public record ProviderHealthView(
        UUID connectionId,
        ConnectorProvider provider,
        boolean healthy,
        String accountId,
        String displayName,
        String message,
        Instant checkedAt
) {}
