package io.aetheris.orchestrator.stage14;

import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.approval.ApprovalStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class Stage14SelfHealingProposalService {
    public static final String APPROVAL_ACTION = "STAGE14_SELF_HEAL";
    private static final Set<String> ACTIONS = Set.of("RESTART_SERVICE", "PAUSE_PROVIDER", "REVOKE_REMOTE_SESSION",
            "ROLLBACK_RELEASE", "PAUSE_TASK", "STOP_TRADING");
    private static final Set<String> FORBIDDEN = Set.of("ARBITRARY_SHELL", "ADMIN_BYPASS", "LIVE_ORDER", "WITHDRAWAL", "TRANSFER");
    private final Stage14SelfHealingProposalRepository repository;
    private final Stage14OperationalIntelligenceService operations;
    private final ApprovalService approvals;

    public Stage14SelfHealingProposalService(Stage14SelfHealingProposalRepository repository,
                                             Stage14OperationalIntelligenceService operations,
                                             ApprovalService approvals) {
        this.repository = repository;
        this.operations = operations;
        this.approvals = approvals;
    }

    @Transactional
    public Stage14SelfHealingProposalEntity propose(ProposalRequest request) {
        if (request == null || request.incidentId() == null || request.taskId() == null) {
            throw new IllegalArgumentException("incidentId and taskId are required");
        }
        var incident = operations.incident(request.incidentId());
        if ("RESOLVED".equals(incident.getStatus())) throw new IllegalStateException("Cannot create self-healing proposal for a resolved incident");
        String action = token(request.action(), "action", 48).toUpperCase(Locale.ROOT);
        if (FORBIDDEN.contains(action) || !ACTIONS.contains(action)) throw new IllegalArgumentException("Forbidden or unsupported self-healing action: " + action);
        String target = token(request.target(), "target", 160);
        String rationale = text(request.rationale(), "rationale", 1200);
        Stage14SelfHealingProposalEntity proposal = repository.save(new Stage14SelfHealingProposalEntity(
                UUID.randomUUID(), request.incidentId(), request.taskId(), action, target, rationale));
        operations.markMitigationProposed(request.incidentId());
        return proposal;
    }

    @Transactional
    public Stage14SelfHealingProposalEntity authorize(UUID proposalId, UUID approvalId) {
        Stage14SelfHealingProposalEntity proposal = get(proposalId);
        var approval = approvals.getRequired(approvalId);
        if (approval.getStatus() != ApprovalStatus.APPROVED) throw new IllegalStateException("Owner approval is not approved");
        if (!APPROVAL_ACTION.equals(approval.getActionType())) throw new IllegalArgumentException("Approval action type must be " + APPROVAL_ACTION);
        if (!proposal.getTaskId().equals(approval.getTaskId())) throw new IllegalArgumentException("Approval task does not match proposal task");
        proposal.authorize(approvalId);
        return repository.save(proposal);
    }

    public Stage14SelfHealingProposalEntity get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NoSuchElementException("Unknown Stage 14 self-healing proposal"));
    }
    public List<Stage14SelfHealingProposalEntity> recent() { return repository.findTop100ByOrderByUpdatedAtDesc(); }
    public List<Stage14SelfHealingProposalEntity> forIncident(UUID incidentId) {
        operations.incident(incidentId);
        return repository.findTop100ByIncidentIdOrderByUpdatedAtDesc(incidentId);
    }

    private String token(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim();
        if (v.length() > max || !v.matches("[A-Za-z0-9._:/-]{1," + max + "}")) throw new IllegalArgumentException(label + " is invalid");
        return v;
    }
    private String text(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim();
        if (v.length() > max) throw new IllegalArgumentException(label + " is too long");
        return v;
    }

    public record ProposalRequest(UUID incidentId, UUID taskId, String action, String target, String rationale) {}
}
