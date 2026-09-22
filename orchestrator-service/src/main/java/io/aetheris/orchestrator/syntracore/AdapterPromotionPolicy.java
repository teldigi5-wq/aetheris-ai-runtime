package io.aetheris.orchestrator.syntracore;

public record AdapterPromotionPolicy(
        double minimumQualityImprovement,
        double maximumFailureRateIncrease,
        double maximumFirstTokenLatencyRatio,
        double minimumThroughputRatio,
        boolean requireOwnerHardwareEvidence) {

    public AdapterPromotionPolicy {
        requireFiniteNonNegative(minimumQualityImprovement, "minimumQualityImprovement");
        requireFiniteNonNegative(maximumFailureRateIncrease, "maximumFailureRateIncrease");
        if (!Double.isFinite(maximumFirstTokenLatencyRatio) || maximumFirstTokenLatencyRatio < 1.0) {
            throw new IllegalArgumentException("maximumFirstTokenLatencyRatio must be finite and at least 1.0");
        }
        if (!Double.isFinite(minimumThroughputRatio)
                || minimumThroughputRatio <= 0.0
                || minimumThroughputRatio > 1.0) {
            throw new IllegalArgumentException("minimumThroughputRatio must be finite and between 0 and 1");
        }
    }

    public static AdapterPromotionPolicy conservativeDefault() {
        return new AdapterPromotionPolicy(0.02, 0.0, 1.10, 0.90, true);
    }

    private static void requireFiniteNonNegative(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(field + " must be finite and non-negative");
        }
    }
}
