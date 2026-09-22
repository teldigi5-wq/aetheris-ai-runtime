package io.aetheris.orchestrator.syntracore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class SyntraAdapterPromotionGovernanceService {

    public AdapterPromotionDecision decide(
            AdaptationExperimentPlan plan,
            AdapterPromotionEvidence evidence,
            AdapterPromotionPolicy policy,
            boolean ownerApproval) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(policy, "policy");

        validatePlanIdentity(plan, evidence);
        validateAssessmentIdentity(evidence);

        Set<String> blockerSet = new LinkedHashSet<>();
        ObservedModelEvaluation baseline = evidence.baseline();
        ObservedModelEvaluation candidate = evidence.candidate();

        if (!baseline.privateLocal() || !candidate.privateLocal()) {
            blockerSet.add("PRIVATE_LOCAL_EVIDENCE_REQUIRED");
        }
        if (policy.requireOwnerHardwareEvidence()
                && (!baseline.ownerHardwareEvidence() || !candidate.ownerHardwareEvidence())) {
            blockerSet.add("OWNER_HARDWARE_EVIDENCE_REQUIRED");
        }
        evidence.structuralAssessments().stream()
                .filter(assessment -> !assessment.passed())
                .forEach(assessment -> blockerSet.add("STRUCTURAL_EVALUATION_FAILED:" + assessment.caseId()));

        double qualityImprovement = candidate.qualityScore() - baseline.qualityScore();
        if (qualityImprovement < policy.minimumQualityImprovement()) {
            blockerSet.add("QUALITY_IMPROVEMENT_BELOW_THRESHOLD");
        }

        double failureRateIncrease = candidate.failureRate() - baseline.failureRate();
        if (failureRateIncrease > policy.maximumFailureRateIncrease()) {
            blockerSet.add("FAILURE_RATE_REGRESSION");
        }

        double maximumLatency = baseline.firstTokenLatencyMs() * policy.maximumFirstTokenLatencyRatio();
        if (candidate.firstTokenLatencyMs() > maximumLatency) {
            blockerSet.add("FIRST_TOKEN_LATENCY_REGRESSION");
        }

        double minimumThroughput = baseline.tokensPerSecond() * policy.minimumThroughputRatio();
        if (candidate.tokensPerSecond() < minimumThroughput) {
            blockerSet.add("THROUGHPUT_REGRESSION");
        }

        List<String> blockers = new ArrayList<>(blockerSet);
        if (!blockers.isEmpty()) {
            return decision(
                    plan,
                    AdapterPromotionStatus.REJECTED,
                    Optional.empty(),
                    ownerApproval,
                    blockers);
        }

        if (!ownerApproval) {
            return decision(
                    plan,
                    AdapterPromotionStatus.HELD,
                    Optional.empty(),
                    false,
                    List.of("OWNER_APPROVAL_REQUIRED"));
        }

        String promotedAddress = plan.candidateAdapterAddress().replaceFirst(
                "^aetheris-adapter://candidate/",
                "aetheris-adapter://promoted/");
        return decision(
                plan,
                AdapterPromotionStatus.APPROVED_FOR_LOCAL_USE,
                Optional.of(promotedAddress),
                true,
                List.of());
    }

    private void validatePlanIdentity(AdaptationExperimentPlan plan, AdapterPromotionEvidence evidence) {
        if (!plan.experimentId().equals(evidence.experimentId())) {
            throw new IllegalArgumentException("evaluation experimentId does not match adaptation plan");
        }
        if (!plan.candidateAdapterAddress().equals(evidence.candidateAdapterAddress())) {
            throw new IllegalArgumentException("evaluation candidate address does not match adaptation plan");
        }
        if (!plan.baseModelId().equals(evidence.baseModelId())) {
            throw new IllegalArgumentException("evaluation baseModelId does not match adaptation plan");
        }
        if (!plan.datasetHash().equals(evidence.datasetHash())) {
            throw new IllegalArgumentException("evaluation datasetHash does not match adaptation plan");
        }
        if (!plan.baseModelId().equals(evidence.baseline().modelId())) {
            throw new IllegalArgumentException("baseline observation must identify the planned base model");
        }
        if (!plan.candidateAdapterAddress().equals(evidence.candidate().modelId())) {
            throw new IllegalArgumentException("candidate observation must identify the planned candidate adapter");
        }
        if (!plan.ownerOptIn() || !plan.localOnly() || plan.baseWeightsMutable() || plan.executionAuthorized()) {
            throw new IllegalArgumentException("adaptation plan violates Slice 7 safety invariants");
        }
        if (!"DETACH_ADAPTER".equals(plan.rollbackStrategy())) {
            throw new IllegalArgumentException("adaptation plan must preserve DETACH_ADAPTER rollback");
        }
    }

    private void validateAssessmentIdentity(AdapterPromotionEvidence evidence) {
        for (InferenceEvaluationAssessment assessment : evidence.structuralAssessments()) {
            if (assessment.result().routeSelection().isEmpty()) {
                throw new IllegalArgumentException(
                        "candidate structural assessment must contain an observed local route: " + assessment.caseId());
            }
            String observedModelId = assessment.result().routeSelection().orElseThrow().modelId();
            if (!evidence.candidateAdapterAddress().equals(observedModelId)) {
                throw new IllegalArgumentException(
                        "candidate structural assessment model does not match candidate adapter: " + assessment.caseId());
            }
        }
    }

    private AdapterPromotionDecision decision(
            AdaptationExperimentPlan plan,
            AdapterPromotionStatus status,
            Optional<String> promotedAddress,
            boolean ownerApproved,
            List<String> blockers) {
        return new AdapterPromotionDecision(
                plan.experimentId(),
                plan.candidateAdapterAddress(),
                status,
                promotedAddress,
                ownerApproved,
                true,
                status == AdapterPromotionStatus.APPROVED_FOR_LOCAL_USE,
                false,
                "DETACH_ADAPTER",
                blockers);
    }
}
