package io.aetheris.orchestrator.stage31;

import java.util.Objects;

public record ProactiveIssue(
        String code,
        IssueCategory category,
        IssueSeverity severity,
        String sourceId,
        String summary,
        String recommendation,
        TwinEvidence evidence,
        boolean ownerAttentionRequired) {

    public ProactiveIssue {
        requireText(code, "code");
        requireText(sourceId, "sourceId");
        requireText(summary, "summary");
        requireText(recommendation, "recommendation");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(evidence, "evidence");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
