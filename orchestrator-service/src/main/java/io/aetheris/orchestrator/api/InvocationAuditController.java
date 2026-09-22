package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.execution.InvocationAuditEntity;
import io.aetheris.orchestrator.execution.InvocationAuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/invocations")
public class InvocationAuditController {

    private final InvocationAuditService audit;

    public InvocationAuditController(InvocationAuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public List<InvocationAuditEntity> recent() {
        return audit.recent();
    }

    @GetMapping("/task/{taskId}")
    public List<InvocationAuditEntity> forTask(@PathVariable UUID taskId) {
        return audit.forTask(taskId);
    }
}
