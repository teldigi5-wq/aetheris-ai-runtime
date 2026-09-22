package io.aetheris.orchestrator.stage14;

import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.approval.ApprovalStatus;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Stage14ContainmentService {
    public static final String APPROVAL_ACTION = "STAGE14_CONTAINMENT";
    private static final Set<String> ACTIONS = Set.of("PROVIDER_DISABLE", "TASK_PAUSE", "REMOTE_REVOKE", "TRADING_STOP");
    private final Stage14OperationalIntelligenceService operations;
    private final ApprovalService approvals;

    public Stage14ContainmentService(Stage14OperationalIntelligenceService operations, ApprovalService approvals) {
        this.operations = operations;
        this.approvals = approvals;
    }

    public ContainmentDecision evaluate(ContainmentRequest request) {
        if (request == null || request.incidentId() == null || request.taskId() == null) {
            throw new IllegalArgumentException("incidentId and taskId are required");
        }
        String action = token(request.action(), "action", 40).toUpperCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) throw new IllegalArgumentException("Unsupported containment action");
        var incident = operations.incident(request.incidentId());
        List<String> blockers = new ArrayList<>();
        if ("RESOLVED".equals(incident.getStatus())) blockers.add("incident is already resolved");
        if (!"CRITICAL".equals(incident.getSeverity()) && Set.of("PROVIDER_DISABLE", "REMOTE_REVOKE", "TRADING_STOP").contains(action)) {
            blockers.add("high-impact containment requires a CRITICAL incident");
        }
        boolean approved = false;
        if (request.approvalId() == null) blockers.add("owner approval is required");
        else {
            var approval = approvals.getRequired(request.approvalId());
            if (approval.getStatus() != ApprovalStatus.APPROVED) blockers.add("owner approval is not approved");
            if (!APPROVAL_ACTION.equals(approval.getActionType())) blockers.add("approval action type must be " + APPROVAL_ACTION);
            if (!request.taskId().equals(approval.getTaskId())) blockers.add("approval task does not match containment task");
            approved = blockers.stream().noneMatch(x -> x.contains("approval"));
        }
        String status = blockers.isEmpty() ? "AUTHORIZED_NOT_EXECUTED" : "APPROVAL_OR_POLICY_REQUIRED";
        return new ContainmentDecision(status, action, incident.getId(), incident.getSeverity(), approved,
                List.copyOf(blockers), true, false, false,
                blockers.isEmpty() ? "Containment is authorized but Stage 14 does not execute external remediation" : "Containment remains blocked");
    }

    private String token(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim();
        if (v.length() > max || !v.matches("[A-Za-z0-9._:-]{1," + max + "}")) throw new IllegalArgumentException(label + " is invalid");
        return v;
    }

    public record ContainmentRequest(UUID incidentId, UUID taskId, UUID approvalId, String action) {}
    public record ContainmentDecision(String status, String action, UUID incidentId, String incidentSeverity,
                                      boolean ownerApproved, List<String> blockers,
                                      boolean killSwitchAvailable, boolean remediationExecuted,
                                      boolean externalActionAttempted, String detail) {}
}
