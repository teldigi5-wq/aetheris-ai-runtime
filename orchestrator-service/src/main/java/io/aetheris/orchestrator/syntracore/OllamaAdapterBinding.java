package io.aetheris.orchestrator.syntracore;

import java.util.Objects;

/**
 * Immutable binding between one approved Aetheris adapter identity and a
 * pre-provisioned Ollama model alias.
 *
 * <p>The provider alias is an owner-supplied configuration fact. This record
 * does not install a model and does not attest that provider bytes equal the
 * logical adapter artifact hash.</p>
 */
public record OllamaAdapterBinding(
        AdapterArtifactIdentity identity,
        String providerModelId,
        ModelRuntimeCandidate routingCandidate) {

    public OllamaAdapterBinding {
        identity = Objects.requireNonNull(identity, "identity");
        providerModelId = requireText(providerModelId, "providerModelId");
        routingCandidate = Objects.requireNonNull(routingCandidate, "routingCandidate");

        if (!OllamaLocalRuntime.PROVIDER_ID.equals(routingCandidate.providerId())) {
            throw new IllegalArgumentException("adapter routing candidate must belong to ollama-local");
        }
        if (!AdapterRuntimeRegistration.modelIdFor(identity).equals(routingCandidate.modelId())) {
            throw new IllegalArgumentException("adapter routing candidate must exactly bind the artifact identity");
        }
        if (!routingCandidate.local() || Double.compare(routingCandidate.estimatedCostUsd(), 0d) != 0) {
            throw new IllegalArgumentException("adapter routing candidate must remain local and zero-cost");
        }
        if (AdapterRuntimeRegistration.isAdapterModelId(providerModelId)) {
            throw new IllegalArgumentException("provider model alias must not use the reserved Aetheris adapter namespace");
        }
        if (providerModelId.equals(identity.baseModelId())) {
            throw new IllegalArgumentException("provider adapter alias must be distinct from the declared base model");
        }
    }

    ModelRuntimeCandidate transportCandidate() {
        return new ModelRuntimeCandidate(
                OllamaLocalRuntime.PROVIDER_ID,
                providerModelId,
                routingCandidate.capabilities(),
                routingCandidate.contextWindowTokens(),
                true,
                routingCandidate.healthy(),
                routingCandidate.streaming(),
                routingCandidate.gpuPreferred(),
                routingCandidate.requiredVramMb(),
                routingCandidate.requiredRamMb(),
                routingCandidate.cpuFallbackSupported(),
                routingCandidate.qualityScore(),
                routingCandidate.firstTokenLatencyMs(),
                routingCandidate.tokensPerSecond(),
                routingCandidate.failureRate(),
                routingCandidate.observedVramMb(),
                0d);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
