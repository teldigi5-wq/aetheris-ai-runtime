package io.aetheris.orchestrator.syntracore;

import io.aetheris.orchestrator.stage31.ModelBenchmarkEngine;
import io.aetheris.orchestrator.stage31.ModelBenchmarkSample;
import io.aetheris.orchestrator.stage31.ModelBenchmarkScore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class SyntraInferenceEvaluationHarness {
    private final ModelBenchmarkEngine benchmarkEngine;

    public SyntraInferenceEvaluationHarness() {
        this(new ModelBenchmarkEngine());
    }

    public SyntraInferenceEvaluationHarness(ModelBenchmarkEngine benchmarkEngine) {
        this.benchmarkEngine = Objects.requireNonNull(benchmarkEngine, "benchmarkEngine");
    }

    public InferenceEvaluationAssessment assess(
            InferenceEvaluationCase evaluationCase,
            LocalInferenceResult result) {
        Objects.requireNonNull(evaluationCase, "evaluationCase");
        Objects.requireNonNull(result, "result");

        List<String> failures = new ArrayList<>();
        if (evaluationCase.task() != result.task()) {
            failures.add("TASK_MISMATCH");
        }
        if (evaluationCase.expectedStatus() != result.status()) {
            failures.add("STATUS_MISMATCH");
        }
        if (!evaluationCase.expectedEvidenceAddresses().equals(result.evidenceAddresses())) {
            failures.add("EVIDENCE_CONTINUITY_MISMATCH");
        }
        if (evaluationCase.requireNonBlankOutput() && result.output().isBlank()) {
            failures.add("OUTPUT_REQUIRED");
        }

        if (evaluationCase.expectedProviderId().isPresent()) {
            if (result.routeSelection().isEmpty()) {
                failures.add("EXPECTED_ROUTE_MISSING");
            } else {
                ModelRouteSelection selection = result.routeSelection().orElseThrow();
                if (!evaluationCase.expectedProviderId().orElseThrow().equals(selection.providerId())) {
                    failures.add("PROVIDER_MISMATCH");
                }
                if (!evaluationCase.expectedModelId().orElseThrow().equals(selection.modelId())) {
                    failures.add("MODEL_MISMATCH");
                }
            }
        }

        return new InferenceEvaluationAssessment(
                evaluationCase.caseId(),
                failures.isEmpty(),
                failures,
                result);
    }

    public LocalInferenceEvaluationHook hookFor(
            InferenceEvaluationCase evaluationCase,
            Consumer<InferenceEvaluationAssessment> assessmentSink) {
        Objects.requireNonNull(evaluationCase, "evaluationCase");
        Objects.requireNonNull(assessmentSink, "assessmentSink");
        return result -> assessmentSink.accept(assess(evaluationCase, result));
    }

    public List<EvaluatedModelScore> rankObservedModels(
            List<ObservedModelEvaluation> observations,
            boolean zeroCostMode,
            boolean privateOnly) {
        Objects.requireNonNull(observations, "observations");
        if (observations.isEmpty()) {
            return List.of();
        }

        Map<String, EvaluationEvidenceSource> sourceByModelId = new LinkedHashMap<>();
        List<ModelBenchmarkSample> samples = new ArrayList<>();
        for (ObservedModelEvaluation observation : observations) {
            Objects.requireNonNull(observation, "observation");
            if (sourceByModelId.putIfAbsent(observation.modelId(), observation.evidenceSource()) != null) {
                throw new IllegalArgumentException(
                        "observed model IDs must be unique for deterministic ranking: " + observation.modelId());
            }
            samples.add(observation.toBenchmarkSample());
        }

        List<ModelBenchmarkScore> scores = benchmarkEngine.rank(samples, zeroCostMode, privateOnly);
        return scores.stream()
                .map(score -> new EvaluatedModelScore(
                        score,
                        sourceByModelId.get(score.modelId())))
                .toList();
    }
}
