package io.aetheris.orchestrator.stage32;

public record ResourceBudget(
        double maxCpuPercent,
        double maxMemoryPercent,
        double currentUserLoadPercent,
        double maxUserLoadPercent,
        boolean energySaver) {
    public ResourceBudget {
        if (!percent(maxCpuPercent) || !percent(maxMemoryPercent) || !percent(currentUserLoadPercent) || !percent(maxUserLoadPercent)) {
            throw new IllegalArgumentException("Resource percentages must be between 0 and 100");
        }
    }
    private static boolean percent(double value) { return value >= 0 && value <= 100 && Double.isFinite(value); }
}
