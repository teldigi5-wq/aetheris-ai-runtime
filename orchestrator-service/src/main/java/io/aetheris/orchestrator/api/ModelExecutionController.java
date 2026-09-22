package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.model.ModelExecutionRequest;
import io.aetheris.orchestrator.model.ModelExecutionResponse;
import io.aetheris.orchestrator.model.ModelExecutionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orchestrator/models")
public class ModelExecutionController {

    private final ModelExecutionService execution;

    public ModelExecutionController(ModelExecutionService execution) {
        this.execution = execution;
    }

    @PostMapping("/execute")
    public ModelExecutionResponse execute(@RequestBody ModelExecutionRequest request) {
        return execution.execute(request);
    }
}
