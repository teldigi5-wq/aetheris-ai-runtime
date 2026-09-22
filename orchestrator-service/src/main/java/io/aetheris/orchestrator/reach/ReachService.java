package io.aetheris.orchestrator.reach;

import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.policy.CompiledPolicyDecision;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.policy.OwnerRuleCompilerService;
import io.aetheris.orchestrator.policy.PolicyEvaluationRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.aetheris.orchestrator.reach.ReachTypes.*;

@Service
public class ReachService {

    public static final String PHYSICAL_MACHINE_STATUS = "BLOCKED_PENDING_HARDWARE";

    private final ReachCatalogService catalog;
    private final AgentCatalogService agents;
    private final OwnerRuleCompilerService policy;

    public ReachService(ReachCatalogService catalog, AgentCatalogService agents, OwnerRuleCompilerService policy) {
        this.catalog = catalog;
        this.agents = agents;
        this.policy = policy;
    }

    public RouteDecision route(RouteRequest request) {
        if (request == null) throw new IllegalArgumentException("Reach route request is required");
        if (request.agentId() == null || request.agentId().isBlank()) throw new IllegalArgumentException("agentId is required");
        if (request.channel() == null) throw new IllegalArgumentException("channel is required");
        if (request.capability() == null) throw new IllegalArgumentException("capability is required");
        agents.getRequired(request.agentId());

        List<BackendDescriptor> candidates = catalog.backendsFor(request.channel());
        List<String> blocked = new ArrayList<>();
        List<EligibleBackend> eligible = new ArrayList<>();

        for (BackendDescriptor backend : candidates) {
            if (!backend.capabilities().contains(request.capability())) {
                blocked.add(backend.id() + ":CAPABILITY_UNSUPPORTED");
                continue;
            }
            if (backend.implementationStatus() != ImplementationStatus.IMPLEMENTED) {
                blocked.add(backend.id() + ":" + backend.implementationStatus().name());
                continue;
            }
            if (!request.runtimeAvailableBackendIds().contains(backend.id())) {
                blocked.add(backend.id() + ":RUNTIME_UNAVAILABLE");
                continue;
            }
            if (backend.credentialRequired() && !request.authenticatedBackendIds().contains(backend.id())) {
                blocked.add(backend.id() + ":AUTH_REQUIRED");
                continue;
            }

            boolean sendsProtectedDataOffDevice = backend.sendsDataOffDevice() && request.protectedData();
            CompiledPolicyDecision decision = policy.evaluate(new PolicyEvaluationRequest(
                    request.agentId(),
                    "reach:" + request.channel().name().toLowerCase() + ":" + request.capability().name().toLowerCase(),
                    "reach",
                    riskFor(backend),
                    backend.billable(),
                    sendsProtectedDataOffDevice,
                    request.mode(),
                    Map.of(
                            "backend", backend.id(),
                            "channel", request.channel().name(),
                            "capability", request.capability().name(),
                            "transport", backend.transport().name(),
                            "protectedData", Boolean.toString(request.protectedData()),
                            "networkOffDevice", Boolean.toString(backend.sendsDataOffDevice())
                    )));

            if (!decision.allowed()) {
                blocked.add(backend.id() + ":POLICY_DENIED");
                continue;
            }
            eligible.add(new EligibleBackend(backend, decision));
        }

        eligible.sort(Comparator.comparingInt(item -> item.backend().priority()));
        if (eligible.isEmpty()) {
            return new RouteDecision(false, null, List.of(), blocked, false, List.of(),
                    "No implemented, runtime-ready backend is eligible. Planned adapters are never treated as working integrations.");
        }

        EligibleBackend primary = eligible.getFirst();
        List<BackendDescriptor> fallbacks = eligible.stream().skip(1).map(EligibleBackend::backend).toList();
        return new RouteDecision(
                true,
                primary.backend(),
                fallbacks,
                blocked,
                primary.policy().requiresApproval(),
                primary.policy().reasons(),
                "Selected the highest-priority implemented backend that passed runtime, credential and owner-policy checks."
        );
    }

    public DoctorReport doctor(DoctorRequest request) {
        DoctorRequest effective = request == null ? new DoctorRequest(Set.of(), Set.of()) : request;
        List<BackendHealth> health = catalog.backends().stream().map(backend -> {
            HealthStatus status;
            String detail;
            if (backend.implementationStatus() == ImplementationStatus.ADAPTER_REQUIRED) {
                status = HealthStatus.ADAPTER_REQUIRED;
                detail = "Repository adapter is not implemented yet. " + backend.installHint();
            } else if (backend.implementationStatus() == ImplementationStatus.PHYSICAL_VALIDATION_REQUIRED) {
                status = HealthStatus.PHYSICAL_VALIDATION_REQUIRED;
                detail = "Repository support exists but physical-machine validation is still required.";
            } else if (!effective.runtimeAvailableBackendIds().contains(backend.id())) {
                status = HealthStatus.RUNTIME_UNAVAILABLE;
                detail = "Implemented adapter exists, but the runtime backend is not reported available.";
            } else if (backend.credentialRequired() && !effective.authenticatedBackendIds().contains(backend.id())) {
                status = HealthStatus.AUTH_REQUIRED;
                detail = "Runtime is available, but owner-controlled credentials/session state are not reported ready.";
            } else {
                status = HealthStatus.READY;
                detail = "Repository adapter and supplied runtime state are ready for governed routing.";
            }
            return new BackendHealth(backend.id(), backend.channel(), status, detail);
        }).toList();

        long ready = health.stream().filter(item -> item.status() == HealthStatus.READY).count();
        return new DoctorReport(
                health,
                PHYSICAL_MACHINE_STATUS,
                ready + " Reach backend(s) are ready in the supplied runtime snapshot. Physical-PC validation remains pending."
        );
    }

    public InstallPlan installPlan(InstallPlanRequest request) {
        InstallPlanRequest effective = request == null
                ? new InstallPlanRequest(Set.of(), Set.of(), false)
                : request;
        Set<Channel> selected = effective.channels().isEmpty() ? Set.of(Channel.values()) : effective.channels();
        List<InstallPlanStep> steps = new ArrayList<>();
        boolean executableNow = true;

        for (Channel channel : selected.stream().sorted(Comparator.comparing(Enum::name)).toList()) {
            List<BackendDescriptor> channelBackends = catalog.backendsFor(channel);
            if (channelBackends.isEmpty()) continue;
            BackendDescriptor preferred = channelBackends.getFirst();
            if (preferred.implementationStatus() == ImplementationStatus.IMPLEMENTED
                    && effective.runtimeAvailableBackendIds().contains(preferred.id())) {
                continue;
            }

            boolean approvalRequired = preferred.installScope() == InstallScope.SYSTEM_APPROVAL_REQUIRED
                    && !effective.allowSystemChanges();
            String action;
            if (preferred.implementationStatus() == ImplementationStatus.ADAPTER_REQUIRED) {
                executableNow = false;
                action = "Implement and review the Aetheris adapter first. " + preferred.installHint();
            } else if (preferred.implementationStatus() == ImplementationStatus.PHYSICAL_VALIDATION_REQUIRED) {
                executableNow = false;
                action = "Validate the existing integration on the target physical PC before activation. " + preferred.installHint();
            } else if (approvalRequired) {
                executableNow = false;
                action = "System-level setup is blocked until the owner explicitly approves it. " + preferred.installHint();
            } else {
                action = "Prepare the runtime backend without writing secrets into the repository. " + preferred.installHint();
            }
            steps.add(new InstallPlanStep(channel, preferred.id(), preferred.installScope(), approvalRequired, action));
        }

        String note = steps.isEmpty()
                ? "No setup steps are required for the selected channels in the supplied runtime snapshot."
                : "This is a plan only. Aetheris Reach v1 does not perform system-level installs, browser-session extraction or credential writes.";
        return new InstallPlan(steps, executableNow && steps.stream().noneMatch(InstallPlanStep::ownerApprovalRequired), note);
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("layer", "Aetheris Reach");
        result.put("version", "v1");
        result.put("channels", Channel.values().length);
        result.put("backends", catalog.backends().size());
        result.put("implementedBackends", catalog.backends().stream()
                .filter(backend -> backend.implementationStatus() == ImplementationStatus.IMPLEMENTED).count());
        result.put("physicalMachineStatus", PHYSICAL_MACHINE_STATUS);
        result.put("physicalPcValidationPending", true);
        return Map.copyOf(result);
    }

    private RiskLevel riskFor(BackendDescriptor backend) {
        if (backend.transport() == Transport.UI_AUTOMATION) return RiskLevel.HIGH;
        if (backend.credentialRequired() || backend.transport() == Transport.BROWSER) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    private record EligibleBackend(BackendDescriptor backend, CompiledPolicyDecision policy) {}
}
