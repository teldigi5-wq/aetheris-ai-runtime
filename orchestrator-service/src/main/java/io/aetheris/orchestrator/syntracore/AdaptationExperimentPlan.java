package io.aetheris.orchestrator.syntracore;

import java.util.Objects;

public record AdaptationExperimentPlan(
        String experimentId,
        AdaptationMethod method,
        String baseModelId,
        String datasetHash,
        int sourceCount,
        int redactionCount,
        String candidateAdapterAddress,
        boolean ownerOptIn,
        boolean localOnly,
        boolean baseWeightsMutable,
        boolean executionAuthorized,
        String rollbackStrategy) {

    public AdaptationExperimentPlan {
        if (experimentId == null || experimentId.isBlank()) {
            throw new IllegalArgumentException("experimentId must not be blank");
        }
        method = Objects.requireNonNull(method, "method");
        if (baseModelId == null || baseModelId.isBlank()) {
            throw new IllegalArgumentException("baseModelId must not be blank");
        }
        if (datasetHash == null || !datasetHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("datasetHash must be a lowercase SHA-256 hex digest");
        }
        if (sourceCount <= 0) {
            throw new IllegalArgumentException("sourceCount must be positive");
        }
        if (redactionCount < 0) {
            throw new IllegalArgumentException("redactionCount must not be negative");
        }
        if (candidateAdapterAddress == null
                || !candidateAdapterAddress.startsWith("aetheris-adapter://candidate/")) {
            throw new IllegalArgumentException("candidateAdapterAddress must use candidate adapter scheme");
        }
        if (!ownerOptIn) {
            throw new IllegalArgumentException("owner opt-in is required for an adaptation plan");
        }
        if (!localOnly) {
            throw new IllegalArgumentException("adaptation planning must remain local-only");
        }
        if (baseWeightsMutable) {
            throw new IllegalArgumentException("base model weights must remain immutable");
        }
        if (executionAuthorized) {
            throw new IllegalArgumentException("Slice 7 planning must not authorize training execution");
        }
        if (!"DETACH_ADAPTER".equals(rollbackStrategy)) {
            throw new IllegalArgumentException("rollbackStrategy must be DETACH_ADAPTER");
        }
    }
}
