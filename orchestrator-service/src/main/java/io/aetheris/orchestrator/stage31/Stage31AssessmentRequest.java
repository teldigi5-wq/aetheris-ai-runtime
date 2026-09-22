package io.aetheris.orchestrator.stage31;

import java.util.List;
import java.util.Objects;

public record Stage31AssessmentRequest(
        ProjectDigitalTwin project,
        PcDigitalTwin pc,
        OwnerWorkspaceModel workspace,
        List<GoalCandidate> goals) {

    public Stage31AssessmentRequest {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(pc, "pc");
        Objects.requireNonNull(workspace, "workspace");
        goals = goals == null ? List.of() : List.copyOf(goals);
    }
}
