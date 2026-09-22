package io.aetheris.orchestrator.syntracore;

import java.util.Objects;

/**
 * Immutable runtime-issued handle binding one adapter invocation to the exact
 * verified artifact, provider, and adapter-derived model identity.
 *
 * <p>This is a contract object, not a cryptographic attestation or proof of
 * physical runtime atomicity.</p>
 */
public record AdapterInvocationLease(
        AdapterArtifactIdentity identity,
        String providerId,
        String modelId,
        String evidenceAddress) {

    public static final String LEASE_EVIDENCE_PREFIX = "aetheris-adapter-lease://";

    public AdapterInvocationLease {
        identity = Objects.requireNonNull(identity, "identity");
        providerId = requireText(providerId, "providerId");
        modelId = requireText(modelId, "modelId");
        evidenceAddress = requireText(evidenceAddress, "evidenceAddress");

        String expectedModelId = AdapterRuntimeRegistration.modelIdFor(identity);
        if (!expectedModelId.equals(modelId)) {
            throw new IllegalArgumentException(
                    "adapter invocation lease modelId must exactly bind the adapter artifact identity");
        }
        if (!evidenceAddress.startsWith(LEASE_EVIDENCE_PREFIX)) {
            throw new IllegalArgumentException(
                    "adapter invocation lease evidence address must use the aetheris-adapter-lease namespace");
        }
    }

    public boolean matches(AdapterRuntimeRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        return identity.equals(registration.identity())
                && providerId.equals(registration.candidate().providerId())
                && modelId.equals(registration.candidate().modelId());
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
