package io.aetheris.orchestrator.stage31;

public record GoalCandidate(
        String goalId,
        String description,
        int urgency,
        int importance,
        int dependencyUnlock,
        int risk,
        int ownerPreference) {

    public GoalCandidate {
        requireText(goalId, "goalId");
        requireText(description, "description");
        requireRange(urgency, "urgency");
        requireRange(importance, "importance");
        requireRange(dependencyUnlock, "dependencyUnlock");
        requireRange(risk, "risk");
        requireRange(ownerPreference, "ownerPreference");
    }

    private static void requireRange(int value, String field) {
        if (value < 0 || value > 10) {
            throw new IllegalArgumentException(field + " must be between 0 and 10");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
