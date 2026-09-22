package io.aetheris.orchestrator.syntracore;

public record ModelRouteSelection(
        String providerId,
        String modelId,
        ExecutionTarget executionTarget,
        double score,
        String reason) {

    public ModelRouteSelection {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
        if (executionTarget == null) {
            throw new IllegalArgumentException("executionTarget must not be null");
        }
        if (!Double.isFinite(score) || score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("score must be between 0 and 1");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }
}
