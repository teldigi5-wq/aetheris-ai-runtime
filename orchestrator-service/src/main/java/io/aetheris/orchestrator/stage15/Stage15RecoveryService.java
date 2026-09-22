package io.aetheris.orchestrator.stage15;

import io.aetheris.orchestrator.approval.*;
import io.aetheris.orchestrator.stage14.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class Stage15RecoveryService {
    public static final String APPROVAL_ACTION = "STAGE15_RECOVERY_EXECUTE";
    private static final Set<String> VERIFICATION_POLICIES = Set.of("HEALTHY_ATTESTATION", "SLO_RECOVERY");
    private final Stage15RecoveryPlanRepository repository;
    private final Stage15ServiceCatalogService catalog;
    private final Stage15ReliabilityService reliability;
    private final Stage14OperationalIntelligenceService operations;
    private final ApprovalService approvals;
    private final BoundedRecoveryAdapter adapter = new EvidenceOnlyRecoveryAdapter();

    public Stage15RecoveryService(Stage15RecoveryPlanRepository repository, Stage15ServiceCatalogService catalog,
                                  Stage15ReliabilityService reliability, Stage14OperationalIntelligenceService operations,
                                  ApprovalService approvals) {
        this.repository = repository; this.catalog = catalog; this.reliability = reliability;
        this.operations = operations; this.approvals = approvals;
    }

    @Transactional
    public Stage15RecoveryPlanEntity create(RecoveryPlanRequest request) {
        if (request == null || request.incidentId() == null || request.taskId() == null)
            throw new IllegalArgumentException("incidentId and taskId are required");
        Stage14IncidentEntity incident = operations.incident(request.incidentId());
        if ("RESOLVED".equals(incident.getStatus())) throw new IllegalStateException("Cannot create recovery plan for resolved incident");
        Stage15ServiceCatalogEntity service = catalog.get(request.serviceId());
        String action = action(request.action());
        String rollback = action(request.rollbackAction());
        if (!service.getAllowedActions().contains(action)) throw new IllegalArgumentException("Recovery action is not allowed for this service");
        if (!service.getAllowedActions().contains(rollback)) throw new IllegalArgumentException("Rollback action is not allowed for this service");
        if (Stage15ServiceCatalogService.FORBIDDEN_ACTIONS.contains(action) || Stage15ServiceCatalogService.FORBIDDEN_ACTIONS.contains(rollback))
            throw new IllegalArgumentException("Forbidden recovery authority requested");
        String policy = token(request.verificationPolicy(), "verificationPolicy", 48).toUpperCase(Locale.ROOT);
        if (!VERIFICATION_POLICIES.contains(policy)) throw new IllegalArgumentException("Unsupported verification policy");
        String planSha = sha(request.planSha256(), "planSha256");
        operations.markMitigationProposed(request.incidentId());
        return repository.save(new Stage15RecoveryPlanEntity(UUID.randomUUID(), request.incidentId(), request.taskId(),
                service.getId(), action, rollback, policy, planSha));
    }

    @Transactional
    public Stage15RecoveryPlanEntity authorize(UUID planId, UUID approvalId) {
        Stage15RecoveryPlanEntity plan = get(planId);
        ApprovalEntity approval = approvals.getRequired(approvalId);
        if (approval.getStatus() != ApprovalStatus.APPROVED) throw new IllegalStateException("Owner approval is not approved");
        if (!APPROVAL_ACTION.equals(approval.getActionType())) throw new IllegalArgumentException("Approval action type must be " + APPROVAL_ACTION);
        if (!plan.getTaskId().equals(approval.getTaskId())) throw new IllegalArgumentException("Approval task does not match recovery plan task");
        plan.authorize(approvalId);
        return repository.save(plan);
    }

    public ExecutionReadiness executionReadiness(UUID planId) {
        Stage15RecoveryPlanEntity plan = get(planId);
        List<String> blockers = new ArrayList<>();
        if (plan.getApprovalId() == null || !plan.getStatus().startsWith("AUTHORIZED")) blockers.add("exact owner approval has not authorized this plan");
        Stage15ReliabilityService.ReliabilityAssessment r = reliability.assess(plan.getServiceId());
        if ("ERROR_BUDGET_EXHAUSTED".equals(r.status())) blockers.add("error budget is exhausted; automated recovery execution is blocked");
        if (r.maintenanceActive()) blockers.add("service is inside a maintenance window");
        AdapterReadiness ar = adapter.readiness(plan);
        if (!ar.ready()) blockers.add(ar.reason());
        return new ExecutionReadiness(blockers.isEmpty() ? "EXECUTION_ADAPTER_READY" : "BLOCKED",
                plan.getId(), r.status(), ar.adapterId(), false, List.copyOf(blockers), false);
    }

    @Transactional
    public Stage15RecoveryPlanEntity recordTargetExecutionEvidence(UUID planId, TargetExecutionEvidence request) {
        Stage15RecoveryPlanEntity plan = get(planId);
        if (plan.getApprovalId() == null) throw new IllegalStateException("Recovery plan is not owner-authorized");
        if (request == null || !request.targetMeasured()) throw new IllegalArgumentException("Target-measured execution evidence is required");
        String evidence = sha(request.attestationSha256(), "attestationSha256");
        plan.recordExecutionEvidence(evidence, request.actionObserved());
        return repository.save(plan);
    }

    @Transactional
    public Stage15RecoveryPlanEntity verify(UUID planId, VerificationEvidence request) {
        Stage15RecoveryPlanEntity plan = get(planId);
        if (!plan.isExecutionObserved()) throw new IllegalStateException("No target execution evidence exists for this recovery plan");
        if (request == null || !request.targetMeasured()) throw new IllegalArgumentException("Target-measured verification evidence is required");
        String evidence = sha(request.attestationSha256(), "attestationSha256");
        String health = token(request.health(), "health", 24).toUpperCase(Locale.ROOT);
        if (!Set.of("HEALTHY", "DEGRADED", "UNAVAILABLE").contains(health)) throw new IllegalArgumentException("Unsupported verification health state");
        boolean recovered = "HEALTHY".equals(health);
        if ("SLO_RECOVERY".equals(plan.getVerificationPolicy())) recovered = recovered && "HEALTHY".equals(reliability.assess(plan.getServiceId()).status());
        plan.verify(evidence, recovered);
        return repository.save(plan);
    }

    public Stage15RecoveryPlanEntity get(UUID id) { return repository.findById(id).orElseThrow(() -> new NoSuchElementException("Unknown Stage 15 recovery plan")); }
    public List<Stage15RecoveryPlanEntity> recent() { return repository.findTop100ByOrderByUpdatedAtDesc(); }
    public List<Stage15RecoveryPlanEntity> forIncident(UUID incidentId) { operations.incident(incidentId); return repository.findTop100ByIncidentIdOrderByUpdatedAtDesc(incidentId); }
    public List<Stage15RecoveryPlanEntity> forService(String serviceId) { return repository.findTop100ByServiceIdOrderByUpdatedAtDesc(catalog.get(serviceId).getId()); }

    private String action(String value) {
        String action = token(value, "action", 48).toUpperCase(Locale.ROOT);
        if (Stage15ServiceCatalogService.FORBIDDEN_ACTIONS.contains(action) || !Stage15ServiceCatalogService.BOUNDED_ACTIONS.contains(action))
            throw new IllegalArgumentException("Forbidden or unsupported recovery action: " + action);
        return action;
    }
    private String token(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim();
        if (v.length() > max || !v.matches("[A-Za-z0-9._:/-]{1," + max + "}")) throw new IllegalArgumentException(label + " is invalid");
        return v;
    }
    private String sha(String value, String label) {
        if (value == null || !value.trim().toLowerCase(Locale.ROOT).matches("[a-f0-9]{64}")) throw new IllegalArgumentException(label + " must be a SHA-256 value");
        return value.trim().toLowerCase(Locale.ROOT);
    }

    interface BoundedRecoveryAdapter { AdapterReadiness readiness(Stage15RecoveryPlanEntity plan); }
    static class EvidenceOnlyRecoveryAdapter implements BoundedRecoveryAdapter {
        public AdapterReadiness readiness(Stage15RecoveryPlanEntity plan) {
            return new AdapterReadiness("evidence-only", false,
                    "target execution adapter is intentionally unavailable until real target/provider evidence is configured");
        }
    }
    public record AdapterReadiness(String adapterId, boolean ready, String reason) {}
    public record RecoveryPlanRequest(UUID incidentId, UUID taskId, String serviceId, String action,
                                      String rollbackAction, String verificationPolicy, String planSha256) {}
    public record TargetExecutionEvidence(boolean targetMeasured, boolean actionObserved, String attestationSha256) {}
    public record VerificationEvidence(boolean targetMeasured, String health, String attestationSha256) {}
    public record ExecutionReadiness(String status, UUID planId, String reliabilityStatus, String adapterId,
                                     boolean executionAttempted, List<String> blockers, boolean externalActionAttempted) {}
}
