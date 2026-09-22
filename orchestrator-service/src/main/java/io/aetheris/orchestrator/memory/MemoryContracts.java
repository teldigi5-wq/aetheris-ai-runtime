package io.aetheris.orchestrator.memory;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class MemoryContracts {
    private MemoryContracts() {}

    public record WriteRequest(
            MemoryScope scope,
            String projectId,
            String key,
            String content,
            boolean sensitive,
            Set<String> tags,
            String provenanceType,
            String provenanceReference,
            Instant expiresAt) {}

    public record CorrectRequest(
            String content,
            Set<String> tags,
            String provenanceReference,
            Instant expiresAt) {}

    public record MemoryView(
            UUID id,
            MemoryScope scope,
            String projectId,
            String key,
            String content,
            boolean sensitive,
            Set<String> tags,
            String provenanceType,
            String provenanceReference,
            Instant createdAt,
            Instant updatedAt,
            Instant expiresAt) {}

    public record SearchResult(MemoryView memory,double score,String reason) {}

    public record PurgeResult(int deleted,Instant evaluatedAt) {}
}
