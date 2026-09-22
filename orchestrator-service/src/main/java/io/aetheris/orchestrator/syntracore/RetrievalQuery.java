package io.aetheris.orchestrator.syntracore;

import java.util.Objects;

public record RetrievalQuery(RetrievalScope scope, String text, int limit) {
    public RetrievalQuery {
        scope = Objects.requireNonNull(scope, "scope");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("retrieval query must not be blank");
        }
        text = text.trim();
        if (limit <= 0) {
            throw new IllegalArgumentException("retrieval limit must be positive");
        }
    }
}
