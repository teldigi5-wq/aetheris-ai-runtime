package io.aetheris.orchestrator.syntracore;

import java.util.Objects;
import java.util.Set;

public record ModelRuntimeCandidate(
        String providerId,
        String modelId,
        Set<ModelCapability> capabilities,
        int contextWindowTokens,
        boolean local,
        boolean healthy,
        boolean streaming,
        boolean gpuPreferred,
        long requiredVramMb,
        long requiredRamMb,
        boolean cpuFallbackSupported,
        double qualityScore,
        double firstTokenLatencyMs,
        double tokensPerSecond,
        double failureRate,
        double observedVramMb,
        double estimatedCostUsd) {

    public ModelRuntimeCandidate {
        requireText(providerId, "providerId");
        requireText(modelId, "modelId");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        if (capabilities.isEmpty()) {
            throw new IllegalArgumentException("capabilities must not be empty");
        }
        if (contextWindowTokens <= 0) {
            throw new IllegalArgumentException("contextWindowTokens must be positive");
        }
        requireNonNegative(requiredVramMb, "requiredVramMb");
        requireNonNegative(requiredRamMb, "requiredRamMb");
        requireUnit(qualityScore, "qualityScore");
        requireNonNegative(firstTokenLatencyMs, "firstTokenLatencyMs");
        requireNonNegative(tokensPerSecond, "tokensPerSecond");
        requireUnit(failureRate, "failureRate");
        requireNonNegative(observedVramMb, "observedVramMb");
        requireNonNegative(estimatedCostUsd, "estimatedCostUsd");
    }

    public String key() {
        return providerId + "/" + modelId;
    }

    private static void requireUnit(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(field + " must be between 0 and 1");
        }
    }

    private static void requireNonNegative(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(field + " must be finite and non-negative");
        }
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
