package io.aetheris.orchestrator.syntracore;

import java.util.Objects;
import java.util.Set;

public record ModelRouteRequest(
        Set<ModelCapability> requiredCapabilities,
        int requiredContextTokens,
        boolean zeroCostMode,
        boolean privateMode,
        boolean requireStreaming,
        boolean allowCpuFallback,
        boolean preferLocal) {

    public ModelRouteRequest {
        requiredCapabilities = Set.copyOf(Objects.requireNonNull(requiredCapabilities, "requiredCapabilities"));
        if (requiredCapabilities.isEmpty()) {
            throw new IllegalArgumentException("requiredCapabilities must not be empty");
        }
        if (requiredContextTokens <= 0) {
            throw new IllegalArgumentException("requiredContextTokens must be positive");
        }
    }
}
