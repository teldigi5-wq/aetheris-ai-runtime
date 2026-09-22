package io.aetheris.orchestrator.stage32;

public record RetryBudget(int maxAttempts, int attemptsUsed) {
    public RetryBudget {
        if (maxAttempts < 1 || attemptsUsed < 0) throw new IllegalArgumentException("Invalid retry budget");
    }
    public boolean exhausted() { return attemptsUsed >= maxAttempts; }
}
