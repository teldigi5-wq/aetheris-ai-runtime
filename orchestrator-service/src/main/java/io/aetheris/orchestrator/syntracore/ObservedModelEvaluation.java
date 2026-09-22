package io.aetheris.orchestrator.syntracore;

import io.aetheris.orchestrator.stage31.ModelBenchmarkSample;

import java.util.Objects;

public record ObservedModelEvaluation(
        String modelId,
        double qualityScore,
        double firstTokenLatencyMs,
        double tokensPerSecond,
        double failureRate,
        double vramMb,
        double estimatedCostUsd,
        boolean privateLocal,
        EvaluationEvidenceSource evidenceSource) {

    public ObservedModelEvaluation {
        evidenceSource = Objects.requireNonNull(evidenceSource, "evidenceSource");
        new ModelBenchmarkSample(
                modelId,
                qualityScore,
                firstTokenLatencyMs,
                tokensPerSecond,
                failureRate,
                vramMb,
                estimatedCostUsd,
                privateLocal);
    }

    public ModelBenchmarkSample toBenchmarkSample() {
        return new ModelBenchmarkSample(
                modelId,
                qualityScore,
                firstTokenLatencyMs,
                tokensPerSecond,
                failureRate,
                vramMb,
                estimatedCostUsd,
                privateLocal);
    }

    public boolean ownerHardwareEvidence() {
        return evidenceSource == EvaluationEvidenceSource.OWNER_HARDWARE;
    }
}
