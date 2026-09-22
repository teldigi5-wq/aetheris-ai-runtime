package io.aetheris.orchestrator.syntracore;

import java.util.Objects;

public record AdaptationDatasetEntry(
        String sourceId,
        String sourceContentHash,
        String sanitizedContent,
        String sanitizedContentHash,
        int redactionCount,
        AdaptationRightsBasis rightsBasis,
        String rightsReference,
        String provenanceReference) {

    public AdaptationDatasetEntry {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        sourceId = sourceId.trim();
        sourceContentHash = requireHash(sourceContentHash, "sourceContentHash");
        if (sanitizedContent == null || sanitizedContent.isBlank()) {
            throw new IllegalArgumentException("sanitizedContent must not be blank");
        }
        sanitizedContentHash = requireHash(sanitizedContentHash, "sanitizedContentHash");
        if (redactionCount < 0) {
            throw new IllegalArgumentException("redactionCount must not be negative");
        }
        rightsBasis = Objects.requireNonNull(rightsBasis, "rightsBasis");
        if (rightsReference == null || rightsReference.isBlank()) {
            throw new IllegalArgumentException("rightsReference must not be blank");
        }
        if (provenanceReference == null || provenanceReference.isBlank()) {
            throw new IllegalArgumentException("provenanceReference must not be blank");
        }
        rightsReference = rightsReference.trim();
        provenanceReference = provenanceReference.trim();
    }

    private static String requireHash(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be a lowercase SHA-256 hex digest");
        }
        return value;
    }
}
