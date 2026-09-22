package io.aetheris.orchestrator.model;

public record LocalModelHealth(
        boolean available,
        String provider,
        String baseUrl,
        String configuredModel,
        String detail
) {
}
