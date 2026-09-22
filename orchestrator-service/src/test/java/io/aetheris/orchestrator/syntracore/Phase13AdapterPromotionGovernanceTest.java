package io.aetheris.orchestrator.syntracore;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase13AdapterPromotionGovernanceTest {

    private static final String DATASET_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String CANDIDATE = "aetheris-adapter://candidate/experiment-8/owner-adapter";
    private static final String PROMOTED = "aetheris-adapter://promoted/experiment-8/owner-adapter";

    private final SyntraAdapterPromotionGovernanceService governance =
            new SyntraAdapterPromotionGovernanceService();

    @Test
    void approvedCandidateRequiresObservedImprovementAndExplicitOwnerApproval() {
        AdaptationExperimentPlan plan = plan();
        AdapterPromotionEvidence evidence = passingEvidence(
                EvaluationEvidenceSource.OWNER_HARDWARE,
                EvaluationEvidenceSource.OWNER_HARDWARE);

        AdapterPromotionDecision decision = governance.decide(
                plan,
                evidence,
                AdapterPromotionPolicy.conservativeDefault(),
                true);

        assertEquals(AdapterPromotionStatus.APPROVED_FOR_LOCAL_USE, decision.status());
        assertEquals(Optional.of(PROMOTED), decision.promotedAdapterAddress());
        assertTrue(decision.ownerApproved());
        assertTrue(decision.localOnly());
        assertTrue(decision.routingEligible());
        assertFalse(decision.baseWeightsMutable());
        assertEquals("DETACH_ADAPTER", decision.rollbackStrategy());
        assertTrue(decision.blockers().isEmpty());
        assertFalse(plan.executionAuthorized());
    }

    @Test
    void qualifyingCandidateRemainsHeldUntilOwnerExplicitlyApprovesPromotion() {
        AdapterPromotionDecision decision = governance.decide(
                plan(),
                passingEvidence(
                        EvaluationEvidenceSource.OWNER_HARDWARE,
                        EvaluationEvidenceSource.OWNER_HARDWARE),
                AdapterPromotionPolicy.conservativeDefault(),
                false);

        assertEquals(AdapterPromotionStatus.HELD, decision.status());
        assertEquals(List.of("OWNER_APPROVAL_REQUIRED"), decision.blockers());
        assertFalse(decision.routingEligible());
        assertTrue(decision.promotedAdapterAddress().isEmpty());
    }

    @Test
    void measurableRegressionsRejectCandidateEvenWhenOwnerApproves() {
        AdapterPromotionEvidence evidence = evidence(
                observed("qwen2.5-coder:7b", 0.82, 200, 50, 0.01, EvaluationEvidenceSource.OWNER_HARDWARE),
                observed(CANDIDATE, 0.81, 260, 35, 0.04, EvaluationEvidenceSource.OWNER_HARDWARE),
                passingAssessment());

        AdapterPromotionDecision decision = governance.decide(
                plan(),
                evidence,
                AdapterPromotionPolicy.conservativeDefault(),
                true);

        assertEquals(AdapterPromotionStatus.REJECTED, decision.status());
        assertTrue(decision.blockers().contains("QUALITY_IMPROVEMENT_BELOW_THRESHOLD"));
        assertTrue(decision.blockers().contains("FAILURE_RATE_REGRESSION"));
        assertTrue(decision.blockers().contains("FIRST_TOKEN_LATENCY_REGRESSION"));
        assertTrue(decision.blockers().contains("THROUGHPUT_REGRESSION"));
        assertFalse(decision.routingEligible());
        assertTrue(decision.promotedAdapterAddress().isEmpty());
    }

    @Test
    void defaultPolicyRejectsSyntheticEvidenceAndFailedStructuralProof() {
        InferenceEvaluationAssessment failedAssessment = new InferenceEvaluationAssessment(
                "candidate-structure",
                false,
                List.of("OUTPUT_REQUIRED"),
                candidateResult());
        AdapterPromotionEvidence evidence = evidence(
                observed("qwen2.5-coder:7b", 0.80, 220, 45, 0.01, EvaluationEvidenceSource.HOSTED_SYNTHETIC),
                observed(CANDIDATE, 0.86, 210, 47, 0.01, EvaluationEvidenceSource.HOSTED_SYNTHETIC),
                failedAssessment);

        AdapterPromotionDecision decision = governance.decide(
                plan(),
                evidence,
                AdapterPromotionPolicy.conservativeDefault(),
                true);

        assertEquals(AdapterPromotionStatus.REJECTED, decision.status());
        assertTrue(decision.blockers().contains("OWNER_HARDWARE_EVIDENCE_REQUIRED"));
        assertTrue(decision.blockers().contains("STRUCTURAL_EVALUATION_FAILED:candidate-structure"));
    }

    @Test
    void planEvaluationIdentityMismatchFailsClosed() {
        AdapterPromotionEvidence wrongDataset = new AdapterPromotionEvidence(
                "experiment-8",
                CANDIDATE,
                "qwen2.5-coder:7b",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                observed("qwen2.5-coder:7b", 0.80, 220, 45, 0.01, EvaluationEvidenceSource.OWNER_HARDWARE),
                observed(CANDIDATE, 0.86, 210, 47, 0.01, EvaluationEvidenceSource.OWNER_HARDWARE),
                List.of(passingAssessment()));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> governance.decide(
                        plan(),
                        wrongDataset,
                        AdapterPromotionPolicy.conservativeDefault(),
                        true));

        assertTrue(failure.getMessage().contains("datasetHash"));
    }

    @Test
    void structuralAssessmentMustObserveTheExactCandidateAdapter() {
        LocalInferenceResult wrongRouteResult = LocalInferenceResult.completed(
                InferenceTask.CODING,
                new ModelRouteSelection("ollama", "different-adapter", ExecutionTarget.CPU, 0.90, "wrong route"),
                "observed output",
                List.of(),
                List.of(),
                1);
        InferenceEvaluationAssessment wrongRoute = new InferenceEvaluationAssessment(
                "candidate-structure",
                true,
                List.of(),
                wrongRouteResult);
        AdapterPromotionEvidence evidence = evidence(
                observed("qwen2.5-coder:7b", 0.80, 220, 45, 0.01, EvaluationEvidenceSource.OWNER_HARDWARE),
                observed(CANDIDATE, 0.86, 210, 47, 0.01, EvaluationEvidenceSource.OWNER_HARDWARE),
                wrongRoute);

        assertThrows(
                IllegalArgumentException.class,
                () -> governance.decide(
                        plan(),
                        evidence,
                        AdapterPromotionPolicy.conservativeDefault(),
                        true));
    }

    @Test
    void decisionContractCannotMarkHeldCandidateAsRoutable() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AdapterPromotionDecision(
                        "experiment-8",
                        CANDIDATE,
                        AdapterPromotionStatus.HELD,
                        Optional.empty(),
                        false,
                        true,
                        true,
                        false,
                        "DETACH_ADAPTER",
                        List.of("OWNER_APPROVAL_REQUIRED")));
    }

    private AdapterPromotionEvidence passingEvidence(
            EvaluationEvidenceSource baselineSource,
            EvaluationEvidenceSource candidateSource) {
        return evidence(
                observed("qwen2.5-coder:7b", 0.80, 220, 45, 0.01, baselineSource),
                observed(CANDIDATE, 0.86, 210, 47, 0.01, candidateSource),
                passingAssessment());
    }

    private AdapterPromotionEvidence evidence(
            ObservedModelEvaluation baseline,
            ObservedModelEvaluation candidate,
            InferenceEvaluationAssessment assessment) {
        return new AdapterPromotionEvidence(
                "experiment-8",
                CANDIDATE,
                "qwen2.5-coder:7b",
                DATASET_HASH,
                baseline,
                candidate,
                List.of(assessment));
    }

    private ObservedModelEvaluation observed(
            String modelId,
            double quality,
            double latency,
            double throughput,
            double failureRate,
            EvaluationEvidenceSource source) {
        return new ObservedModelEvaluation(
                modelId,
                quality,
                latency,
                throughput,
                failureRate,
                4096,
                0,
                true,
                source);
    }

    private InferenceEvaluationAssessment passingAssessment() {
        return new InferenceEvaluationAssessment(
                "candidate-structure",
                true,
                List.of(),
                candidateResult());
    }

    private LocalInferenceResult candidateResult() {
        return LocalInferenceResult.completed(
                InferenceTask.CODING,
                new ModelRouteSelection("ollama", CANDIDATE, ExecutionTarget.CPU, 0.95, "candidate evaluation"),
                "observed output",
                List.of(),
                List.of(),
                1);
    }

    private AdaptationExperimentPlan plan() {
        return new AdaptationExperimentPlan(
                "experiment-8",
                AdaptationMethod.QLORA,
                "qwen2.5-coder:7b",
                DATASET_HASH,
                3,
                1,
                CANDIDATE,
                true,
                true,
                false,
                false,
                "DETACH_ADAPTER");
    }
}
