package io.aetheris.orchestrator.stage31;

public record ModelBenchmarkSample(
        String modelId,
        double qualityScore,
        double firstTokenLatencyMs,
        double tokensPerSecond,
        double failureRate,
        double vramMb,
        double estimatedCostUsd,
        boolean privateLocal) {

    public ModelBenchmarkSample {
        requireText(modelId, "modelId");
        requireUnit(qualityScore, "qualityScore");
        requireNonNegative(firstTokenLatencyMs, "firstTokenLatencyMs");
        requireNonNegative(tokensPerSecond, "tokensPerSecond");
        requireUnit(failureRate, "failureRate");
        requireNonNegative(vramMb, "vramMb");
        requireNonNegative(estimatedCostUsd, "estimatedCostUsd");
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

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
