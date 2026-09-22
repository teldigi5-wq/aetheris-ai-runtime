package io.aetheris.orchestrator.syntracore;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase13LocalInferenceEvaluationHarnessTest {

    private static final String CITATION =
            "aetheris-memory://project/slice6-evaluation/11111111-2222-3333-4444-555555555555";

    @Test
    void evaluatesTypedOutcomeAndWorksThroughSlice5ObservationHook() {
        SyntraInferenceEvaluationHarness harness = new SyntraInferenceEvaluationHarness();
        LocalInferenceResult result = LocalInferenceResult.completed(
                InferenceTask.CODING,
                new ModelRouteSelection("ollama", "qwen-coder", ExecutionTarget.CPU, 0.91, "local fit"),
                "bounded evaluated answer",
                List.of(CITATION),
                List.of(),
                2);
        InferenceEvaluationCase evaluationCase = InferenceEvaluationCase.routed(
                "coding-citation-continuity",
                InferenceTask.CODING,
                InferenceStatus.COMPLETED,
                List.of(CITATION),
                "ollama",
                "qwen-coder",
                true);
        AtomicReference<InferenceEvaluationAssessment> observed = new AtomicReference<>();

        harness.hookFor(evaluationCase, observed::set).evaluate(result);

        InferenceEvaluationAssessment assessment = observed.get();
        assertTrue(assessment.passed());
        assertTrue(assessment.failures().isEmpty());
        assertEquals(evaluationCase.caseId(), assessment.caseId());
        assertEquals(result, assessment.result());
    }

    @Test
    void reportsStructuralFailuresWithoutInventingSemanticQuality() {
        SyntraInferenceEvaluationHarness harness = new SyntraInferenceEvaluationHarness();
        String actualCitation =
                "aetheris-memory://project/slice6-evaluation/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        LocalInferenceResult result = LocalInferenceResult.completed(
                InferenceTask.CODING,
                new ModelRouteSelection("ollama", "actual-model", ExecutionTarget.CPU, 0.75, "local fit"),
                "",
                List.of(actualCitation),
                List.of(),
                1);
        InferenceEvaluationCase evaluationCase = new InferenceEvaluationCase(
                "structural-drift",
                InferenceTask.CHAT,
                InferenceStatus.CANCELLED,
                List.of(CITATION),
                Optional.of("expected-provider"),
                Optional.of("expected-model"),
                true);

        InferenceEvaluationAssessment assessment = harness.assess(evaluationCase, result);

        assertFalse(assessment.passed());
        assertTrue(assessment.failures().contains("TASK_MISMATCH"));
        assertTrue(assessment.failures().contains("STATUS_MISMATCH"));
        assertTrue(assessment.failures().contains("EVIDENCE_CONTINUITY_MISMATCH"));
        assertTrue(assessment.failures().contains("OUTPUT_REQUIRED"));
        assertTrue(assessment.failures().contains("PROVIDER_MISMATCH"));
        assertTrue(assessment.failures().contains("MODEL_MISMATCH"));
    }

    @Test
    void unavailableOutcomeCanBeEvaluatedWithoutCreatingRuntimeEvidence() {
        SyntraInferenceEvaluationHarness harness = new SyntraInferenceEvaluationHarness();
        LocalInferenceResult result = LocalInferenceResult.unavailable(
                InferenceTask.REASONING,
                List.of(CITATION),
                List.of("ollama/model:INSUFFICIENT_CAPABILITY"));
        InferenceEvaluationCase evaluationCase = InferenceEvaluationCase.structural(
                "unavailable-is-explicit",
                InferenceTask.REASONING,
                InferenceStatus.UNAVAILABLE,
                List.of(CITATION),
                false);

        InferenceEvaluationAssessment assessment = harness.assess(evaluationCase, result);

        assertTrue(assessment.passed());
        assertTrue(result.routeSelection().isEmpty());
        assertEquals("", result.output());
        assertFalse(result.terminalObserved());
    }

    @Test
    void delegatesObservedRankingToStage31AndPreservesEvidenceProvenance() {
        SyntraInferenceEvaluationHarness harness = new SyntraInferenceEvaluationHarness();
        List<ObservedModelEvaluation> observations = List.of(
                new ObservedModelEvaluation(
                        "hosted-synthetic-local",
                        0.80,
                        250,
                        45,
                        0.02,
                        4200,
                        0,
                        true,
                        EvaluationEvidenceSource.HOSTED_SYNTHETIC),
                new ObservedModelEvaluation(
                        "owner-measured-local",
                        0.84,
                        220,
                        48,
                        0.01,
                        3900,
                        0,
                        true,
                        EvaluationEvidenceSource.OWNER_HARDWARE),
                new ObservedModelEvaluation(
                        "paid-local",
                        0.99,
                        100,
                        60,
                        0.00,
                        3500,
                        0.02,
                        true,
                        EvaluationEvidenceSource.HOSTED_SYNTHETIC),
                new ObservedModelEvaluation(
                        "remote-free",
                        0.95,
                        120,
                        55,
                        0.00,
                        0,
                        0,
                        false,
                        EvaluationEvidenceSource.HOSTED_SYNTHETIC));

        List<EvaluatedModelScore> ranked = harness.rankObservedModels(observations, true, true);

        assertEquals(2, ranked.size());
        EvaluatedModelScore ownerScore = ranked.stream()
                .filter(score -> score.benchmarkScore().modelId().equals("owner-measured-local"))
                .findFirst()
                .orElseThrow();
        EvaluatedModelScore syntheticScore = ranked.stream()
                .filter(score -> score.benchmarkScore().modelId().equals("hosted-synthetic-local"))
                .findFirst()
                .orElseThrow();
        assertTrue(ownerScore.ownerHardwareEvidence());
        assertFalse(syntheticScore.ownerHardwareEvidence());
        assertTrue(ranked.stream().noneMatch(score -> score.benchmarkScore().modelId().equals("paid-local")));
        assertTrue(ranked.stream().noneMatch(score -> score.benchmarkScore().modelId().equals("remote-free")));
    }

    @Test
    void duplicateObservedModelIdsFailClosedBeforeRanking() {
        SyntraInferenceEvaluationHarness harness = new SyntraInferenceEvaluationHarness();
        ObservedModelEvaluation first = new ObservedModelEvaluation(
                "duplicate",
                0.8,
                200,
                40,
                0.01,
                4000,
                0,
                true,
                EvaluationEvidenceSource.HOSTED_SYNTHETIC);
        ObservedModelEvaluation second = new ObservedModelEvaluation(
                "duplicate",
                0.9,
                180,
                45,
                0.01,
                3900,
                0,
                true,
                EvaluationEvidenceSource.OWNER_HARDWARE);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> harness.rankObservedModels(List.of(first, second), true, true));

        assertTrue(failure.getMessage().contains("unique"));
    }

    @Test
    void observedMetricsReuseStage31ValidationInsteadOfAcceptingFabricatedRanges() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ObservedModelEvaluation(
                        "invalid-quality",
                        1.5,
                        100,
                        20,
                        0,
                        0,
                        0,
                        true,
                        EvaluationEvidenceSource.HOSTED_SYNTHETIC));
    }
}
