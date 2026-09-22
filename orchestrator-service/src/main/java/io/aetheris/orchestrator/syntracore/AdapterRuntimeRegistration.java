package io.aetheris.orchestrator.syntracore;

import java.util.Objects;

/**
 * Immutable routing registration for one verified-active local adapter artifact.
 *
 * <p>This record does not activate an adapter. It only allows an already verified
 * Slice 9 activation result to be represented in a runtime catalog.</p>
 */
public record AdapterRuntimeRegistration(
        AdapterArtifactIdentity identity,
        AdapterLifecycleResult activation,
        ModelRuntimeCandidate candidate) {

    public static final String ADAPTER_MODEL_PREFIX = "aetheris-adapter-runtime://sha256/";

    public AdapterRuntimeRegistration {
        identity = Objects.requireNonNull(identity, "identity");
        activation = Objects.requireNonNull(activation, "activation");
        candidate = Objects.requireNonNull(candidate, "candidate");

        if (activation.status() != AdapterLifecycleStatus.ACTIVATED_VERIFIED
                || !activation.effectAttempted()
                || !activation.executionVerified()
                || !activation.active()
                || !activation.authorityGranted()) {
            throw new IllegalArgumentException(
                    "adapter routing registration requires a verified active Slice 9 lifecycle result");
        }
        if (!identity.equals(activation.identity())) {
            throw new IllegalArgumentException(
                    "adapter routing registration identity must exactly match the verified activation identity");
        }
        if (!candidate.local()) {
            throw new IllegalArgumentException("adapter routing registration must remain local-only");
        }
        if (Double.compare(candidate.estimatedCostUsd(), 0d) != 0) {
            throw new IllegalArgumentException("adapter routing registration must remain zero-cost");
        }
        String expectedModelId = modelIdFor(identity);
        if (!expectedModelId.equals(candidate.modelId())) {
            throw new IllegalArgumentException(
                    "adapter runtime modelId must exactly bind the verified artifact SHA-256");
        }
    }

    public static String modelIdFor(AdapterArtifactIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        return ADAPTER_MODEL_PREFIX + identity.artifactSha256();
    }

    public static boolean isAdapterModelId(String modelId) {
        return modelId != null && modelId.startsWith(ADAPTER_MODEL_PREFIX);
    }
}
