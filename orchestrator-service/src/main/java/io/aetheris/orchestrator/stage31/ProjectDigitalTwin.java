package io.aetheris.orchestrator.stage31;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record ProjectDigitalTwin(
        String projectId,
        String revision,
        String branch,
        Set<String> dependencies,
        Set<String> openBugs,
        Set<String> activeTasks,
        Set<String> goals,
        boolean ciHealthy,
        boolean docsComplete,
        int failedTests,
        boolean deploymentHealthy,
        boolean dependencyDrift,
        double quotaRemainingPercent,
        TwinEvidence evidence,
        Instant updatedAt) {

    public ProjectDigitalTwin {
        requireText(projectId, "projectId");
        requireText(revision, "revision");
        requireText(branch, "branch");
        dependencies = immutable(dependencies);
        openBugs = immutable(openBugs);
        activeTasks = immutable(activeTasks);
        goals = immutable(goals);
        if (failedTests < 0) {
            throw new IllegalArgumentException("failedTests must be non-negative");
        }
        if (!Double.isFinite(quotaRemainingPercent)
                || quotaRemainingPercent < 0.0
                || quotaRemainingPercent > 100.0) {
            throw new IllegalArgumentException("quotaRemainingPercent must be between 0 and 100");
        }
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    private static Set<String> immutable(Set<String> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
