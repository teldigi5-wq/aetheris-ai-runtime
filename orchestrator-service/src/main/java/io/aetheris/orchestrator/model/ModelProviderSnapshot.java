package io.aetheris.orchestrator.model;

public record ModelProviderSnapshot(
        String providerId,
        boolean available,
        boolean local,
        boolean zeroCost,
        String model,
        String detail
) {
}
