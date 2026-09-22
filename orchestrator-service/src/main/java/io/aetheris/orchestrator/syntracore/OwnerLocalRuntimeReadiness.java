package io.aetheris.orchestrator.syntracore;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Read-only deployment evidence for the configured owner-local runtime.
 * This is not hardware attestation and contains no performance certification.
 */
public record OwnerLocalRuntimeReadiness(
        boolean providerReachable,
        boolean ready,
        List<String> visibleModelIds,
        List<String> missingBaseModelIds,
        List<String> missingAdapterAliases,
        Instant observedAt,
        String providerErrorCode,
        String evidenceRef) {

    public OwnerLocalRuntimeReadiness {
        visibleModelIds = List.copyOf(Objects.requireNonNull(visibleModelIds, "visibleModelIds"));
        missingBaseModelIds = List.copyOf(Objects.requireNonNull(missingBaseModelIds, "missingBaseModelIds"));
        missingAdapterAliases = List.copyOf(Objects.requireNonNull(missingAdapterAliases, "missingAdapterAliases"));
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
        if (providerReachable && providerErrorCode != null) {
            throw new IllegalArgumentException("reachable owner runtime must not carry a provider error code");
        }
        if (!providerReachable && (providerErrorCode == null || providerErrorCode.isBlank())) {
            throw new IllegalArgumentException("unreachable owner runtime requires a provider error code");
        }
        if (ready && (!providerReachable || !missingBaseModelIds.isEmpty() || !missingAdapterAliases.isEmpty())) {
            throw new IllegalArgumentException("ready owner runtime evidence is internally inconsistent");
        }
        if (evidenceRef == null || !evidenceRef.startsWith("aetheris-runtime-readiness://ollama-local/")) {
            throw new IllegalArgumentException("readiness evidence must use the local Ollama readiness namespace");
        }
    }
}
