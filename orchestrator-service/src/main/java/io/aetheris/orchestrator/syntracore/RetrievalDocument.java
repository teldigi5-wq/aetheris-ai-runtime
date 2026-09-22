package io.aetheris.orchestrator.syntracore;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public record RetrievalDocument(
        String sourceKind,
        String sourceId,
        String title,
        String content,
        boolean protectedData,
        Set<String> tags) {

    private static final Set<String> ALLOWED_SOURCE_KINDS = Set.of(
            "REPOSITORY",
            "DOCUMENT",
            "OWNER_FILE",
            "TEXT");

    public RetrievalDocument {
        sourceKind = canonicalSourceKind(sourceKind);
        sourceId = requireText(sourceId, "sourceId");
        title = requireText(title, "title");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        tags = normalizeTags(tags);
    }

    static String canonicalSourceKind(String sourceKind) {
        String canonical = requireText(sourceKind, "sourceKind").toUpperCase(Locale.ROOT);
        if (!ALLOWED_SOURCE_KINDS.contains(canonical)) {
            throw new IllegalArgumentException("unsupported retrieval source kind");
        }
        return canonical;
    }

    private static Set<String> normalizeTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            String value = requireText(tag, "tag").toLowerCase(Locale.ROOT);
            normalized.add(value);
        }
        return Set.copyOf(normalized);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }
}
