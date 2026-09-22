package io.aetheris.orchestrator.stage32;

import java.time.Instant;
import java.util.Set;

public record ArtifactRecord(String artifactId, String location, String producer, String contentHash,
                             Set<String> evidenceReferences, Instant createdAt, Instant retainUntil) {
    public ArtifactRecord {
        evidenceReferences = evidenceReferences == null ? Set.of() : Set.copyOf(evidenceReferences);
        if (artifactId == null || artifactId.isBlank() || location == null || location.isBlank() || producer == null || producer.isBlank()
                || contentHash == null || contentHash.isBlank() || createdAt == null || retainUntil == null || retainUntil.isBefore(createdAt)) {
            throw new IllegalArgumentException("Artifact provenance and retention metadata are required");
        }
    }
}
