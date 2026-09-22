package io.aetheris.orchestrator.stage31;

import java.time.Instant;
import java.util.List;

public record Stage31AssessmentResult(
        List<ProactiveIssue> issues,
        List<PrioritizedGoal> priorities,
        boolean physicalValidationPending,
        String truthStatus,
        Instant evaluatedAt) {}
