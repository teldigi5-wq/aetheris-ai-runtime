package io.aetheris.orchestrator.syntracore;

public record ContextBudget(
        int maxContextTokens,
        int maxEvidenceTokens,
        int maxEvidenceItems) {

    public ContextBudget {
        if (maxContextTokens <= 0) {
            throw new IllegalArgumentException("maxContextTokens must be positive");
        }
        if (maxEvidenceTokens <= 0) {
            throw new IllegalArgumentException("maxEvidenceTokens must be positive");
        }
        if (maxEvidenceTokens > maxContextTokens) {
            throw new IllegalArgumentException("maxEvidenceTokens must not exceed maxContextTokens");
        }
        if (maxEvidenceItems <= 0 || maxEvidenceItems > 20) {
            throw new IllegalArgumentException("maxEvidenceItems must be between 1 and 20");
        }
    }

    public static ContextBudget safeDefaults() {
        return new ContextBudget(6_000, 1_500, 8);
    }
}
