package io.aetheris.orchestrator.syntracore;

import io.aetheris.orchestrator.memory.MemoryScope;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record RetrievalScope(MemoryScope memoryScope, String scopeId) {
    private static final Pattern SAFE_SCOPE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,119}");

    public RetrievalScope {
        memoryScope = Objects.requireNonNull(memoryScope, "memoryScope");
        if (memoryScope != MemoryScope.PROJECT && memoryScope != MemoryScope.WORKSPACE) {
            throw new IllegalArgumentException("Syntra retrieval scope must be PROJECT or WORKSPACE");
        }
        if (scopeId == null || scopeId.isBlank()) {
            throw new IllegalArgumentException("scopeId must not be blank");
        }
        scopeId = scopeId.trim().toLowerCase(Locale.ROOT);
        if (!SAFE_SCOPE_ID.matcher(scopeId).matches()) {
            throw new IllegalArgumentException("scopeId contains unsupported characters or is too long");
        }
    }

    public static RetrievalScope project(String projectId) {
        return new RetrievalScope(MemoryScope.PROJECT, projectId);
    }

    public static RetrievalScope workspace(String workspaceId) {
        return new RetrievalScope(MemoryScope.WORKSPACE, workspaceId);
    }

    public String namespace() {
        return kind() + ":" + scopeId;
    }

    public String kind() {
        return memoryScope == MemoryScope.PROJECT ? "project" : "workspace";
    }
}
