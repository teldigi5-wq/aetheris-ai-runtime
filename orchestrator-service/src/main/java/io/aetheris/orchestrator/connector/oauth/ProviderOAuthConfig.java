package io.aetheris.orchestrator.connector.oauth;

import io.aetheris.orchestrator.connector.ConnectorProvider;

import java.util.List;

public record ProviderOAuthConfig(
        ConnectorProvider provider,
        String clientId,
        String clientSecret,
        String authorizationEndpoint,
        String tokenEndpoint,
        String userInfoEndpoint,
        List<String> scopes,
        boolean refreshSupported
) {}
