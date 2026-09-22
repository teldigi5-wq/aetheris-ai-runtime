package io.aetheris.orchestrator.syntracore;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RetrievalEvidence(
        String evidenceAddress,
        UUID nodeId,
        RetrievalScope scope,
        String memoryKey,
        String excerpt,
        double score,
        String retrievalMethod,
        Instant updatedAt) {

    public RetrievalEvidence {
        evidenceAddress = requireText(evidenceAddress, "evidenceAddress");
        nodeId = Objects.requireNonNull(nodeId, "nodeId");
        scope = Objects.requireNonNull(scope, "scope");
        memoryKey = requireText(memoryKey, "memoryKey");
        excerpt = Objects.requireNonNull(excerpt, "excerpt");
        if (!Double.isFinite(score) || score < 0d) {
            throw new IllegalArgumentException("score must be finite and non-negative");
        }
        retrievalMethod = requireText(retrievalMethod, "retrievalMethod");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }
}
