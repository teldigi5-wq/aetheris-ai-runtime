package io.aetheris.orchestrator.execution;

import java.util.UUID;

public record ToolExecutionResponse(
        ToolExecutionStatus status,
        String toolId,
        String detail,
        Object output,
        UUID auditId
) {
}
