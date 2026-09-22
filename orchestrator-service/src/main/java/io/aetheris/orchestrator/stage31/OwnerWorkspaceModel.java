package io.aetheris.orchestrator.stage31;

import java.util.Set;

public record OwnerWorkspaceModel(
        String ownerId,
        String activeProjectId,
        Set<String> careerGoals,
        Set<String> studyAreas,
        Set<String> approvedRoutineIds,
        Set<String> preferredTools,
        boolean localFirst,
        boolean zeroCostMode) {

    public OwnerWorkspaceModel {
        requireText(ownerId, "ownerId");
        requireText(activeProjectId, "activeProjectId");
        careerGoals = immutable(careerGoals);
        studyAreas = immutable(studyAreas);
        approvedRoutineIds = immutable(approvedRoutineIds);
        preferredTools = immutable(preferredTools);
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
