package io.aetheris.orchestrator.syntracore;

import io.aetheris.orchestrator.stage31.ModelBenchmarkScore;

import java.util.Objects;

public record EvaluatedModelScore(
        ModelBenchmarkScore benchmarkScore,
        EvaluationEvidenceSource evidenceSource) {

    public EvaluatedModelScore {
        benchmarkScore = Objects.requireNonNull(benchmarkScore, "benchmarkScore");
        evidenceSource = Objects.requireNonNull(evidenceSource, "evidenceSource");
    }

    public boolean ownerHardwareEvidence() {
        return evidenceSource == EvaluationEvidenceSource.OWNER_HARDWARE;
    }
}
