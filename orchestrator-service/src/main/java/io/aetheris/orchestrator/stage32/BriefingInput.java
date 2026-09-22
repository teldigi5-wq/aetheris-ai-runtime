package io.aetheris.orchestrator.stage32;

import java.util.List;

public record BriefingInput(List<String> completed, List<String> risks, List<String> pendingApprovals,
                            List<String> evidenceReferences, double costUsd) {
    public BriefingInput {
        completed = completed == null ? List.of() : List.copyOf(completed);
        risks = risks == null ? List.of() : List.copyOf(risks);
        pendingApprovals = pendingApprovals == null ? List.of() : List.copyOf(pendingApprovals);
        evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
        if (!Double.isFinite(costUsd) || costUsd < 0) throw new IllegalArgumentException("Invalid cost");
    }
}
