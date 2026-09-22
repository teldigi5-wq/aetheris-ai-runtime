package io.aetheris.orchestrator.syntracore;

import java.util.Objects;

public record ContextAssemblyRequest(
        RetrievalScope scope,
        String query,
        int retrievalLimit,
        ContextBudget budget) {

    public ContextAssemblyRequest {
        scope = Objects.requireNonNull(scope, "scope");
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        query = query.trim();
        if (query.length() > 4_000) {
            throw new IllegalArgumentException("query exceeds context-assembly bound");
        }
        if (retrievalLimit <= 0 || retrievalLimit > 20) {
            throw new IllegalArgumentException("retrievalLimit must be between 1 and 20");
        }
        budget = Objects.requireNonNull(budget, "budget");
    }

    public static ContextAssemblyRequest safeDefaults(RetrievalScope scope, String query) {
        return new ContextAssemblyRequest(scope, query, 12, ContextBudget.safeDefaults());
    }
}
