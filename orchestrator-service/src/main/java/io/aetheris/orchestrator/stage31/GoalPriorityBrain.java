package io.aetheris.orchestrator.stage31;

import java.util.Comparator;
import java.util.List;

public class GoalPriorityBrain {

    public List<PrioritizedGoal> rank(List<GoalCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .map(goal -> new PrioritizedGoal(goal.goalId(), goal.description(), score(goal)))
                .sorted(Comparator.comparingDouble(PrioritizedGoal::score).reversed()
                        .thenComparing(PrioritizedGoal::goalId))
                .toList();
    }

    private static double score(GoalCandidate goal) {
        double score = goal.urgency() * 0.30
                + goal.importance() * 0.30
                + goal.dependencyUnlock() * 0.20
                + goal.ownerPreference() * 0.15
                - goal.risk() * 0.05;
        return Math.round(score * 1000.0) / 1000.0;
    }
}
