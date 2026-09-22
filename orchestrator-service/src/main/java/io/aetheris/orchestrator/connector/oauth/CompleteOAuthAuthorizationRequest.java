package io.aetheris.orchestrator.connector.oauth;

public record CompleteOAuthAuthorizationRequest(String code, String state) {}
