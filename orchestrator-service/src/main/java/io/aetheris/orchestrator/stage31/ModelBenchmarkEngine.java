package io.aetheris.orchestrator.stage31;

import java.util.Comparator;
import java.util.List;

public class ModelBenchmarkEngine {

    public List<ModelBenchmarkScore> rank(
            List<ModelBenchmarkSample> samples,
            boolean zeroCostMode,
            boolean privateOnly) {
        if (samples == null || samples.isEmpty()) {
            return List.of();
        }
        return samples.stream()
                .filter(sample -> !zeroCostMode || sample.estimatedCostUsd() == 0.0)
                .filter(sample -> !privateOnly || sample.privateLocal())
                .map(sample -> new ModelBenchmarkScore(
                        sample.modelId(), score(sample), sample.privateLocal(), sample.estimatedCostUsd()))
                .sorted(Comparator.comparingDouble(ModelBenchmarkScore::score).reversed()
                        .thenComparing(ModelBenchmarkScore::modelId))
                .toList();
    }

    private static double score(ModelBenchmarkSample sample) {
        double latency = 1.0 / (1.0 + sample.firstTokenLatencyMs() / 1000.0);
        double throughput = Math.min(1.0, sample.tokensPerSecond() / 60.0);
        double reliability = 1.0 - sample.failureRate();
        double vramEfficiency = 1.0 / (1.0 + sample.vramMb() / 8192.0);
        double cost = sample.estimatedCostUsd() == 0.0
                ? 1.0
                : 1.0 / (1.0 + sample.estimatedCostUsd() * 100.0);
        double weighted = sample.qualityScore() * 0.45
                + latency * 0.15
                + throughput * 0.15
                + reliability * 0.15
                + vramEfficiency * 0.05
                + cost * 0.05;
        return Math.round(weighted * 10000.0) / 10000.0;
    }
}
