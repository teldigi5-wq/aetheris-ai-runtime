package io.aetheris.orchestrator.syntracore;

public record RetrievalBounds(
        int maxDocumentCharacters,
        int maxQueryCharacters,
        int maxResults,
        int maxEvidenceCharacters,
        int maxSourceIdCharacters,
        int maxTitleCharacters,
        int maxTags,
        int maxTagCharacters) {

    public RetrievalBounds {
        requirePositive(maxDocumentCharacters, "maxDocumentCharacters");
        requirePositive(maxQueryCharacters, "maxQueryCharacters");
        requirePositive(maxResults, "maxResults");
        requirePositive(maxEvidenceCharacters, "maxEvidenceCharacters");
        requirePositive(maxSourceIdCharacters, "maxSourceIdCharacters");
        requirePositive(maxTitleCharacters, "maxTitleCharacters");
        requirePositive(maxTags, "maxTags");
        requirePositive(maxTagCharacters, "maxTagCharacters");
    }

    public static RetrievalBounds safeDefaults() {
        return new RetrievalBounds(
                120_000,
                4_000,
                20,
                4_000,
                180,
                240,
                32,
                64);
    }

    public void validateDocument(RetrievalDocument document) {
        if (document.content().length() > maxDocumentCharacters) {
            throw new IllegalArgumentException("document exceeds configured retrieval ingestion bound");
        }
        if (document.sourceId().length() > maxSourceIdCharacters) {
            throw new IllegalArgumentException("sourceId exceeds configured retrieval bound");
        }
        if (document.title().length() > maxTitleCharacters) {
            throw new IllegalArgumentException("title exceeds configured retrieval bound");
        }
        if (document.tags().size() > maxTags) {
            throw new IllegalArgumentException("too many retrieval tags");
        }
        for (String tag : document.tags()) {
            if (tag.length() > maxTagCharacters) {
                throw new IllegalArgumentException("retrieval tag exceeds configured bound");
            }
        }
    }

    public void validateQuery(RetrievalQuery query) {
        if (query.text().length() > maxQueryCharacters) {
            throw new IllegalArgumentException("query exceeds configured retrieval bound");
        }
        if (query.limit() > maxResults) {
            throw new IllegalArgumentException("retrieval limit exceeds configured bound");
        }
    }

    public void validateSourceId(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (sourceId.trim().length() > maxSourceIdCharacters) {
            throw new IllegalArgumentException("sourceId exceeds configured retrieval bound");
        }
    }

    private static void requirePositive(int value, String label) {
        if (value <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }
}
