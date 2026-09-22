package io.aetheris.orchestrator.model;

public record LocalGenerateResponse(
        String provider,
        String model,
        String response
) {
}
