package io.aetheris.orchestrator.checkpoint;
import java.util.UUID;
public record GitWorkspaceIsolationResult(UUID checkpointId,String branch,String baseCommit,String detail) {}
