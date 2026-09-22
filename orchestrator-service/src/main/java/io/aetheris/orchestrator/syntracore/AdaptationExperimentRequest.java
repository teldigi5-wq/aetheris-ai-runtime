package io.aetheris.orchestrator.syntracore;

import java.util.Objects;

public record AdaptationExperimentRequest(
        String experimentId,
        AdaptationMethod method,
        String baseModelId,
        String outputAdapterId,
        boolean ownerOptIn) {

    public AdaptationExperimentRequest {
        experimentId = requireSafeId(experimentId, "experimentId");
        method = Objects.requireNonNull(method, "method");
        if (baseModelId == null || baseModelId.isBlank()) {
            throw new IllegalArgumentException("baseModelId must not be blank");
        }
        baseModelId = baseModelId.trim();
        if (!baseModelId.matches("[A-Za-z0-9._:/-]{1,200}")) {
            throw new IllegalArgumentException("baseModelId contains unsupported characters");
        }
        outputAdapterId = requireSafeId(outputAdapterId, "outputAdapterId");
    }

    private static String requireSafeId(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        String normalized = value.trim();
        if (!normalized.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException(label + " must be a path-safe identifier");
        }
        return normalized;
    }
}
