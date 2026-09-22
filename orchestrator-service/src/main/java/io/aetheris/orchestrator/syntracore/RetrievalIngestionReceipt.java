package io.aetheris.orchestrator.syntracore;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RetrievalIngestionReceipt(
        String sourceKind,
        String sourceId,
        RetrievalScope scope,
        int revision,
        boolean changed,
        boolean tombstoned,
        int chunkCount,
        List<UUID> activeNodeIds,
        String detail) {

    public RetrievalIngestionReceipt {
        sourceKind = Objects.requireNonNull(sourceKind, "sourceKind");
        sourceId = Objects.requireNonNull(sourceId, "sourceId");
        scope = Objects.requireNonNull(scope, "scope");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        if (chunkCount < 0) {
            throw new IllegalArgumentException("chunkCount must not be negative");
        }
        activeNodeIds = List.copyOf(Objects.requireNonNull(activeNodeIds, "activeNodeIds"));
        detail = Objects.requireNonNull(detail, "detail");
    }
}
