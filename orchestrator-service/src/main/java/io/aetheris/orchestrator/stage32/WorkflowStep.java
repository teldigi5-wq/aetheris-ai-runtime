package io.aetheris.orchestrator.stage32;

import java.util.Set;

public record WorkflowStep(String id, Set<String> dependsOn, String actionClass) {
    public WorkflowStep {
        dependsOn = dependsOn == null ? Set.of() : Set.copyOf(dependsOn);
        if (id == null || id.isBlank() || actionClass == null || actionClass.isBlank()) throw new IllegalArgumentException("Workflow step identity is required");
    }
}
