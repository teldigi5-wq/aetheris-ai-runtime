package io.aetheris.orchestrator.syntracore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Performs read-only readiness discovery against the configured loopback runtime.
 * It never activates, reconciles, leases, streams, downloads, or installs anything.
 */
public final class OwnerLocalRuntimeReadinessService {
    private final OllamaAdapterRuntimeBridge runtime;
    private final Set<String> expectedBaseModels;
    private final Set<String> expectedAdapterAliases;

    public OwnerLocalRuntimeReadinessService(
            OllamaAdapterRuntimeBridge runtime,
            Set<String> expectedBaseModels,
            Set<String> expectedAdapterAliases) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.expectedBaseModels = Set.copyOf(Objects.requireNonNull(expectedBaseModels, "expectedBaseModels"));
        this.expectedAdapterAliases = Set.copyOf(Objects.requireNonNull(expectedAdapterAliases, "expectedAdapterAliases"));
        if (this.expectedBaseModels.isEmpty()) {
            throw new IllegalArgumentException("owner runtime readiness requires an explicit base-model expectation");
        }
    }

    public OwnerLocalRuntimeReadiness check() {
        LocalProviderDiscovery discovery = runtime.discoverModels();
        Set<String> visible = Set.copyOf(discovery.modelIds());
        List<String> missingBase = missing(expectedBaseModels, visible);
        List<String> missingAdapters = missing(expectedAdapterAliases, visible);
        boolean ready = discovery.reachable() && missingBase.isEmpty() && missingAdapters.isEmpty();
        String state = ready ? "ready" : "blocked";
        return new OwnerLocalRuntimeReadiness(
                discovery.reachable(),
                ready,
                discovery.modelIds().stream().sorted().toList(),
                missingBase,
                missingAdapters,
                discovery.observedAt(),
                discovery.errorCode(),
                "aetheris-runtime-readiness://ollama-local/" + state + "/" + discovery.observedAt().toEpochMilli());
    }

    private static List<String> missing(Set<String> expected, Set<String> visible) {
        List<String> missing = new ArrayList<>();
        for (String modelId : expected) {
            if (!visible.contains(modelId)) {
                missing.add(modelId);
            }
        }
        missing.sort(Comparator.naturalOrder());
        return List.copyOf(missing);
    }
}
