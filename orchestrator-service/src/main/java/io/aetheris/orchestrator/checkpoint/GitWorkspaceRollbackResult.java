package io.aetheris.orchestrator.checkpoint;
import java.util.UUID;
public record GitWorkspaceRollbackResult(UUID checkpointId,String branch,String restoredCommit,String detail) {}
