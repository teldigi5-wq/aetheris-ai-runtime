package io.aetheris.orchestrator.syntracore;

import java.util.Objects;

public record ContextAwareInferenceRequest(
        InferenceTask task,
        ContextPack contextPack,
        String userInput,
        int maxOutputTokens,
        int requiredContextWindowTokens,
        boolean allowCpuFallback) {

    private static final boolean ZERO_COST_MODE = true;
    private static final boolean PRIVATE_MODE = true;
    private static final boolean REQUIRE_STREAMING = true;
    private static final boolean PREFER_LOCAL = true;

    public ContextAwareInferenceRequest {
        task = Objects.requireNonNull(task, "task");
        contextPack = Objects.requireNonNull(contextPack, "contextPack");
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("userInput must not be blank");
        }
        userInput = userInput.trim();
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
        if (requiredContextWindowTokens <= 0) {
            throw new IllegalArgumentException("requiredContextWindowTokens must be positive");
        }
        if (requiredContextWindowTokens < maxOutputTokens) {
            throw new IllegalArgumentException("requiredContextWindowTokens must cover maxOutputTokens");
        }
    }

    public ModelRouteRequest routeRequest() {
        return new ModelRouteRequest(
                task.requiredCapabilities(),
                requiredContextWindowTokens,
                ZERO_COST_MODE,
                PRIVATE_MODE,
                REQUIRE_STREAMING,
                allowCpuFallback,
                PREFER_LOCAL);
    }
}
