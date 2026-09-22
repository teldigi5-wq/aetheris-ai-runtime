package io.aetheris.orchestrator.execution;

public record WorkspaceFileResult(
        String path,
        long size,
        String content
) {
}
