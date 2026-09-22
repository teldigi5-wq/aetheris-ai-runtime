package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.execution.ToolExecutionRequest;
import io.aetheris.orchestrator.execution.ToolExecutionResponse;
import io.aetheris.orchestrator.execution.ToolExecutionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orchestrator/tool-executions")
public class ToolExecutionController {

    private final ToolExecutionService execution;

    public ToolExecutionController(ToolExecutionService execution) {
        this.execution = execution;
    }

    @PostMapping
    public ToolExecutionResponse execute(@RequestBody ToolExecutionRequest request) {
        return execution.execute(request);
    }
}
