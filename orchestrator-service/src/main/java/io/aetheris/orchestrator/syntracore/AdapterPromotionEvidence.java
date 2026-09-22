package io.aetheris.orchestrator.syntracore;

import java.util.List;
import java.util.Objects;

public record AdapterPromotionEvidence(
        String experimentId,
        String candidateAdapterAddress,
        String baseModelId,
        String datasetHash,
        ObservedModelEvaluation baseline,
        ObservedModelEvaluation candidate,
        List<InferenceEvaluationAssessment> structuralAssessments) {

    public AdapterPromotionEvidence {
        requireText(experimentId, "experimentId");
        if (candidateAdapterAddress == null
                || !candidateAdapterAddress.startsWith("aetheris-adapter://candidate/")) {
            throw new IllegalArgumentException("candidateAdapterAddress must use candidate adapter scheme");
        }
        requireText(baseModelId, "baseModelId");
        if (datasetHash == null || !datasetHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("datasetHash must be a lowercase SHA-256 hex digest");
        }
        baseline = Objects.requireNonNull(baseline, "baseline");
        candidate = Objects.requireNonNull(candidate, "candidate");
        structuralAssessments = List.copyOf(Objects.requireNonNull(structuralAssessments, "structuralAssessments"));
        if (structuralAssessments.isEmpty()) {
            throw new IllegalArgumentException("at least one structural assessment is required");
        }
        structuralAssessments.forEach(assessment -> Objects.requireNonNull(assessment, "structural assessment"));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
