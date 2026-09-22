package io.aetheris.orchestrator.model;

public record ModelRouteDecision(
        boolean routable,
        String providerId,
        String model,
        boolean local,
        boolean zeroCost,
        String reason
) {
    public static ModelRouteDecision unavailable(String reason) {
        return new ModelRouteDecision(false, null, null, false, true, reason);
    }
}
