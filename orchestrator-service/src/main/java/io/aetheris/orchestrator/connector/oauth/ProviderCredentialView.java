package io.aetheris.orchestrator.connector.oauth;

import io.aetheris.orchestrator.connector.ConnectorProvider;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProviderCredentialView(
        UUID connectionId,
        ConnectorProvider provider,
        ProviderCredentialStatus status,
        boolean accessTokenConfigured,
        boolean refreshTokenConfigured,
        List<String> scopes,
        Instant expiresAt,
        Instant lastValidatedAt,
        Instant updatedAt
) {}
