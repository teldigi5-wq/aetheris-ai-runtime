package io.aetheris.orchestrator.execution;

public record WorkspaceWriteResult(
        String path,
        long size
) {
}
