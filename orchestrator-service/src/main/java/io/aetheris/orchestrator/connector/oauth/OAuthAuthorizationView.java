package io.aetheris.orchestrator.connector.oauth;

import io.aetheris.orchestrator.connector.ConnectorProvider;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OAuthAuthorizationView(
        UUID sessionId,
        UUID connectionId,
        ConnectorProvider provider,
        String authorizationUrl,
        String state,
        Instant expiresAt,
        List<String> scopes
) {}
