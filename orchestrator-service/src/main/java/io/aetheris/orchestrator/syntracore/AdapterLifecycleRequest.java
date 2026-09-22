package io.aetheris.orchestrator.syntracore;

import java.util.Objects;

public record AdapterLifecycleRequest(
        AdaptationExperimentPlan plan,
        AdapterPromotionEvidence evidence,
        AdapterPromotionPolicy promotionPolicy,
        AdapterPromotionDecision promotionDecision,
        AdapterArtifactIdentity artifactIdentity,
        String activationActionId,
        String rollbackActionId,
        String actorId) {

    public AdapterLifecycleRequest {
        plan = Objects.requireNonNull(plan, "plan");
        evidence = Objects.requireNonNull(evidence, "evidence");
        promotionPolicy = Objects.requireNonNull(promotionPolicy, "promotionPolicy");
        promotionDecision = Objects.requireNonNull(promotionDecision, "promotionDecision");
        artifactIdentity = Objects.requireNonNull(artifactIdentity, "artifactIdentity");
        activationActionId = requireText(activationActionId, "activationActionId");
        rollbackActionId = requireText(rollbackActionId, "rollbackActionId");
        actorId = requireText(actorId, "actorId");
        if (activationActionId.equals(rollbackActionId)) {
            throw new IllegalArgumentException("activation and rollback must use distinct Stage 33 action IDs");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
