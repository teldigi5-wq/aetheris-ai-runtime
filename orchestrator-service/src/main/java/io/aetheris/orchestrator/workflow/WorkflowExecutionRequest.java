package io.aetheris.orchestrator.workflow;

import java.util.List;

public record WorkflowExecutionRequest(
        String workspaceFile,
        List<String> qaCommand,
        String workingDirectory,
        List<String> acceptanceCriteria
) {
    public WorkflowExecutionRequest {
        qaCommand = qaCommand == null ? List.of() : List.copyOf(qaCommand);
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
    }
}
