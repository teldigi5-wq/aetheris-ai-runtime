package io.aetheris.orchestrator.syntracore;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record AdaptationSourceDocument(
        String sourceId,
        String content,
        AdaptationRightsBasis rightsBasis,
        String rightsReference,
        String provenanceReference,
        boolean protectedData,
        Set<String> tags) {

    public AdaptationSourceDocument {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        rightsBasis = Objects.requireNonNull(rightsBasis, "rightsBasis");
        if (rightsReference == null || rightsReference.isBlank()) {
            throw new IllegalArgumentException("rightsReference must not be blank");
        }
        if (provenanceReference == null || provenanceReference.isBlank()) {
            throw new IllegalArgumentException("provenanceReference must not be blank");
        }
        sourceId = sourceId.trim();
        rightsReference = rightsReference.trim();
        provenanceReference = provenanceReference.trim();
        tags = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(tags, "tags")));
    }
}
