package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.approval.ApprovalDecisionRequest;
import io.aetheris.orchestrator.approval.ApprovalEntity;
import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.approval.CreateApprovalRequest;
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
@RequestMapping("/api/orchestrator/approvals")
public class ApprovalController {

    private final ApprovalService approvals;

    public ApprovalController(ApprovalService approvals) {
        this.approvals = approvals;
    }

    @PostMapping
    public ApprovalEntity request(@Valid @RequestBody CreateApprovalRequest request) {
        return approvals.request(request);
    }

    @GetMapping("/pending")
    public List<ApprovalEntity> pending() {
        return approvals.pending();
    }

    @GetMapping("/task/{taskId}")
    public List<ApprovalEntity> forTask(@PathVariable UUID taskId) {
        return approvals.forTask(taskId);
    }

    @PostMapping("/{id}/decision")
    public ApprovalEntity decide(@PathVariable UUID id, @RequestBody ApprovalDecisionRequest request) {
        return approvals.decide(id, request);
    }
}
