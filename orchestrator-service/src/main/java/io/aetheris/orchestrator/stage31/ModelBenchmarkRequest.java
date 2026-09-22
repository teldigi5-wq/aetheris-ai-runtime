package io.aetheris.orchestrator.stage31;

import java.util.List;

public record ModelBenchmarkRequest(
        List<ModelBenchmarkSample> samples,
        boolean zeroCostMode,
        boolean privateOnly) {

    public ModelBenchmarkRequest {
        samples = samples == null ? List.of() : List.copyOf(samples);
    }
}
