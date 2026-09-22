package io.aetheris.orchestrator.syntracore;

import java.util.List;
import java.util.Objects;

public record InferenceEvaluationAssessment(
        String caseId,
        boolean passed,
        List<String> failures,
        LocalInferenceResult result) {

    public InferenceEvaluationAssessment {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must not be blank");
        }
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        result = Objects.requireNonNull(result, "result");
        if (passed != failures.isEmpty()) {
            throw new IllegalArgumentException("passed must exactly match whether failures are empty");
        }
    }
}
