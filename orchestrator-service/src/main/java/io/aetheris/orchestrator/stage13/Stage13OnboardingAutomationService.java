package io.aetheris.orchestrator.stage13;

import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.approval.ApprovalStatus;
import io.aetheris.orchestrator.stage12.Stage12ProviderRegistryService;
import io.aetheris.orchestrator.task.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class Stage13OnboardingAutomationService {
    public static final String APPROVAL_ACTION = "STAGE13_PROVIDER_ONBOARD";

    private final Stage13OnboardingPlanRepository repository;
    private final Stage12ProviderRegistryService providers;
    private final Stage13ProviderHealthEvidenceService health;
    private final Stage13CredentialBindingService credentials;
    private final TaskService tasks;
    private final ApprovalService approvals;

    public Stage13OnboardingAutomationService(Stage13OnboardingPlanRepository repository,
                                              Stage12ProviderRegistryService providers,
                                              Stage13ProviderHealthEvidenceService health,
                                              Stage13CredentialBindingService credentials,
                                              TaskService tasks,
                                              ApprovalService approvals) {
        this.repository = repository;
        this.providers = providers;
        this.health = health;
        this.credentials = credentials;
        this.tasks = tasks;
        this.approvals = approvals;
    }

    @Transactional
    public Stage13OnboardingPlanEntity create(CreateOnboardingRequest request) {
        if (request == null || request.taskId() == null) throw new IllegalArgumentException("taskId is required");
        tasks.getRequired(request.taskId());
        String providerId = require(request.providerId()).toLowerCase(Locale.ROOT);
        var provider = providers.find(providerId).orElseThrow(() -> new NoSuchElementException("Unknown Stage 12 provider"));
        if (!provider.enabled()) throw new IllegalStateException("Provider must be enabled before Stage 13 onboarding can be planned");
        return repository.save(new Stage13OnboardingPlanEntity(UUID.randomUUID(), request.taskId(), providerId, provider.credentialAlias()));
    }

    @Transactional
    public OnboardingAssessment assess(UUID planId) {
        Stage13OnboardingPlanEntity plan = required(planId);
        var provider = providers.find(plan.getProviderId()).orElseThrow(() -> new NoSuchElementException("Unknown Stage 12 provider"));
        var healthWindow = health.assessWindow(plan.getProviderId());
        var binding = credentials.assess(plan.getProviderId());
        List<String> blockers = new ArrayList<>();
        if (!"HEALTH_WINDOW_READY".equals(healthWindow.status())) blockers.addAll(healthWindow.blockers());
        if (!binding.available()) blockers.add("credential alias is not available in any configured vault");
        if (provider.credentialAlias() == null || provider.credentialAlias().isBlank()) blockers.add("provider credential alias is required");
        if (blockers.isEmpty()) {
            plan.markAwaitingApproval();
            repository.save(plan);
        }
        return new OnboardingAssessment(blockers.isEmpty() ? "AWAITING_OWNER_APPROVAL" : "BLOCKED",
                plan.getId(), plan.getProviderId(), provider.type(), binding.status(), binding.operatingSystemBacked(),
                healthWindow.status(), List.copyOf(blockers), false,
                blockers.isEmpty() ? "Evidence is sufficient to ask the owner for a controlled provider test; no provider action has occurred"
                        : "Provider onboarding remains blocked");
    }

    @Transactional
    public Stage13OnboardingPlanEntity authorize(UUID planId, UUID approvalId) {
        Stage13OnboardingPlanEntity plan = required(planId);
        if (!"AWAITING_OWNER_APPROVAL".equals(plan.getStatus())) throw new IllegalStateException("Plan is not awaiting owner approval");
        var approval = approvals.getRequired(approvalId);
        if (approval.getStatus() != ApprovalStatus.APPROVED) throw new IllegalStateException("Owner approval is not approved");
        if (!APPROVAL_ACTION.equals(approval.getActionType())) throw new IllegalArgumentException("Approval action type must be " + APPROVAL_ACTION);
        if (!plan.getTaskId().equals(approval.getTaskId())) throw new IllegalArgumentException("Approval task does not match onboarding plan task");
        plan.authorize(approvalId);
        return repository.save(plan);
    }

    @Transactional
    public Stage13OnboardingPlanEntity recordControlledTestEvidence(UUID planId, ControlledTestEvidence evidence) {
        Stage13OnboardingPlanEntity plan = required(planId);
        if (!"CONTROLLED_TEST_AUTHORIZED_NOT_EXECUTED".equals(plan.getStatus())) {
            throw new IllegalStateException("Controlled test evidence requires an owner-authorized Stage 13 plan");
        }
        if (evidence == null || !evidence.providerMeasured()) throw new IllegalArgumentException("Provider-measured controlled-test evidence is required");
        String sha = sha256(evidence.attestationSha256());
        String outcome = require(evidence.outcome()).toUpperCase(Locale.ROOT);
        if (!Set.of("PASS", "FAIL").contains(outcome)) throw new IllegalArgumentException("Controlled-test outcome must be PASS or FAIL");
        if ("PASS".equals(outcome) && !evidence.rollbackAvailable()) {
            throw new IllegalArgumentException("A passing controlled deployment test must preserve rollback availability");
        }
        plan.recordEvidence("PASS".equals(outcome) ? "CONTROLLED_TEST_VERIFIED" : "ROLLBACK_REQUIRED",
                sha, evidence.rollbackAvailable());
        return repository.save(plan);
    }

    public List<Stage13OnboardingPlanEntity> recent() { return repository.findTop100ByOrderByUpdatedAtDesc(); }

    public Stage13OnboardingPlanEntity required(UUID id) {
        if (id == null) throw new IllegalArgumentException("planId is required");
        return repository.findById(id).orElseThrow(() -> new NoSuchElementException("Unknown Stage 13 onboarding plan"));
    }

    private String require(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("value is required");
        return value.trim();
    }

    private String sha256(String value) {
        String v = require(value).toLowerCase(Locale.ROOT);
        if (!v.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("attestation must be SHA-256");
        return v;
    }

    public record CreateOnboardingRequest(UUID taskId, String providerId) {}
    public record ControlledTestEvidence(String outcome, boolean providerMeasured, String attestationSha256,
                                         boolean rollbackAvailable) {}
    public record OnboardingAssessment(String status, UUID planId, String providerId, String providerType,
                                       String credentialStatus, boolean operatingSystemBackedCredential,
                                       String healthWindowStatus, List<String> blockers,
                                       boolean externalActionAttempted, String detail) {}
}
