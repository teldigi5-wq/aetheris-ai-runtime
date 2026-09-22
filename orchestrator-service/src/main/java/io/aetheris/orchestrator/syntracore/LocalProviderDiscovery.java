package io.aetheris.orchestrator.syntracore;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record LocalProviderDiscovery(
        String providerId,
        boolean reachable,
        List<String> modelIds,
        Instant observedAt,
        String errorCode) {

    public LocalProviderDiscovery {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        modelIds = List.copyOf(Objects.requireNonNull(modelIds, "modelIds"));
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
        if (reachable && errorCode != null) {
            throw new IllegalArgumentException("reachable discovery must not carry an errorCode");
        }
        if (!reachable && (errorCode == null || errorCode.isBlank())) {
            throw new IllegalArgumentException("unreachable discovery requires an errorCode");
        }
    }

    public static LocalProviderDiscovery reachable(
            String providerId,
            List<String> modelIds,
            Instant observedAt) {
        return new LocalProviderDiscovery(providerId, true, modelIds, observedAt, null);
    }

    public static LocalProviderDiscovery unreachable(
            String providerId,
            Instant observedAt,
            String errorCode) {
        return new LocalProviderDiscovery(providerId, false, List.of(), observedAt, errorCode);
    }
}
