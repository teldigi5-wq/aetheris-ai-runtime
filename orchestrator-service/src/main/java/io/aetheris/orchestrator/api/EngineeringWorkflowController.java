package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.workflow.EngineeringWorkflowEntity;
import io.aetheris.orchestrator.workflow.EngineeringWorkflowRequest;
import io.aetheris.orchestrator.workflow.EngineeringWorkflowService;
import io.aetheris.orchestrator.workflow.WorkflowExecutionRequest;
import io.aetheris.orchestrator.workflow.WorkflowExecutionResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/workflows/engineering")
public class EngineeringWorkflowController {

    private final EngineeringWorkflowService workflows;

    public EngineeringWorkflowController(EngineeringWorkflowService workflows) {
        this.workflows = workflows;
    }

    @PostMapping
    public EngineeringWorkflowEntity start(@Valid @RequestBody EngineeringWorkflowRequest request) {
        return workflows.start(request);
    }

    @GetMapping
    public List<EngineeringWorkflowEntity> recent() {
        return workflows.recent();
    }

    @GetMapping("/{id}")
    public EngineeringWorkflowEntity get(@PathVariable UUID id) {
        return workflows.getRequired(id);
    }

    @PostMapping("/{id}/advance")
    public EngineeringWorkflowEntity advance(@PathVariable UUID id) {
        return workflows.advance(id);
    }

    @PostMapping("/{id}/execute-next")
    public WorkflowExecutionResult executeNext(@PathVariable UUID id, @RequestBody WorkflowExecutionRequest request) {
        return workflows.executeNext(id, request);
    }
}
