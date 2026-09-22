package io.aetheris.orchestrator.stage31;

import java.util.Objects;

public record RecoveryAction(
        String actionId,
        RecoveryKind kind,
        String target,
        boolean reversible,
        boolean preApproved,
        boolean withinApprovedBounds,
        boolean privileged,
        boolean destructive,
        boolean financial) {

    public RecoveryAction {
        requireText(actionId, "actionId");
        requireText(target, "target");
        Objects.requireNonNull(kind, "kind");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
