package io.aetheris.orchestrator.operator;

import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.execution.InvocationAuditEntity;
import io.aetheris.orchestrator.execution.InvocationAuditService;
import io.aetheris.orchestrator.execution.InvocationKind;
import io.aetheris.orchestrator.execution.InvocationStatus;
import io.aetheris.orchestrator.policy.CompiledPolicyDecision;
import io.aetheris.orchestrator.policy.OwnerRuleCompilerService;
import io.aetheris.orchestrator.policy.PolicyEvaluationRequest;
import io.aetheris.orchestrator.task.DirectExecutionAuthorityService;
import io.aetheris.orchestrator.task.TaskControlService;
import io.aetheris.orchestrator.task.TaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.aetheris.orchestrator.operator.BrowserTypes.*;

@Service
public class BrowserOperatorService {

    public static final String RUNTIME_ID = "browser.webdriver-local";
    public static final String PHYSICAL_MACHINE_STATUS = "BLOCKED_PENDING_HARDWARE";
    private static final String APPROVAL_ACTION = "browser:workflow";
    private static final String DIRECT_TOOL_FAMILY = "browser";

    private final AgentCatalogService agents;
    private final OwnerRuleCompilerService policy;
    private final ApprovalService approvals;
    private final TaskControlService control;
    private final TaskService tasks;
    private final DirectExecutionAuthorityService directAuthority;
    private final InvocationAuditService audit;
    private final W3cWebDriverBrowserAdapter adapter;
    private final boolean runtimeEnabled;
    private final boolean physicalValidated;
    private final String webdriverEndpoint;
    private final String browserName;

    public BrowserOperatorService(
            AgentCatalogService agents,
            OwnerRuleCompilerService policy,
            ApprovalService approvals,
            TaskControlService control,
            TaskService tasks,
            DirectExecutionAuthorityService directAuthority,
            InvocationAuditService audit,
            W3cWebDriverBrowserAdapter adapter,
            @Value("${aetheris.browser.runtime-enabled:false}") boolean runtimeEnabled,
            @Value("${aetheris.browser.physical-validated:false}") boolean physicalValidated,
            @Value("${aetheris.browser.webdriver-endpoint:http://127.0.0.1:9515/}") String webdriverEndpoint,
            @Value("${aetheris.browser.browser-name:chrome}") String browserName) {
        this.agents = agents;
        this.policy = policy;
        this.approvals = approvals;
        this.control = control;
        this.tasks = tasks;
        this.directAuthority = directAuthority;
        this.audit = audit;
        this.adapter = adapter;
        this.runtimeEnabled = runtimeEnabled;
        this.physicalValidated = physicalValidated;
        this.webdriverEndpoint = webdriverEndpoint;
        this.browserName = browserName;
    }

    public BrowserWorkflowPlan plan(BrowserPlanRequest request) {
        if (request == null) throw new IllegalArgumentException("Browser plan request is required");
        if (request.agentId() == null || request.agentId().isBlank()) throw new IllegalArgumentException("agentId is required");
        if (request.actions().isEmpty()) throw new IllegalArgumentException("At least one browser action is required");
        agents.getRequired(request.agentId());

        Set<String> allowedDomains = normalizeDomains(request.allowedDomains());
        if (allowedDomains.isEmpty()) throw new IllegalArgumentException("At least one allowed domain is required for generic browser workflows");

        List<BrowserActionDecision> decisions = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        boolean requiresApproval = false;
        String currentHost = "";

        for (int i = 0; i < request.actions().size(); i++) {
            BrowserAction action = request.actions().get(i);
            if (action == null || action.type() == null) {
                blocked.add("step-" + (i + 1) + ":ACTION_TYPE_REQUIRED");
                continue;
            }
            String actionId = action.actionId().isBlank() ? "step-" + (i + 1) : action.actionId();
            BrowserEffect effect = effectiveEffect(action);
            RiskLevel risk = riskFor(effect);
            List<String> evidence = evidenceFor(action.type(), effect);

            String targetHost;
            try {
                if (action.type() == BrowserActionType.NAVIGATE) {
                    targetHost = validateNavigation(action.url(), allowedDomains);
                    currentHost = targetHost;
                } else {
                    targetHost = currentHost;
                    if (targetHost.isBlank()) throw new IllegalArgumentException("A NAVIGATE action must establish an allowed browser origin first");
                    validateActionShape(action);
                }
            } catch (RuntimeException exception) {
                String reason = actionId + ":" + safeMessage(exception);
                blocked.add(reason);
                decisions.add(new BrowserActionDecision(actionId, action.type(), effect, risk, currentHost,
                        false, false, List.of(), evidence, reason));
                continue;
            }

            if (effect == BrowserEffect.FINANCIAL_CHANGE) {
                String reason = actionId + ":LIVE_MONEY_BROWSER_ACTION_BLOCKED";
                blocked.add(reason);
                decisions.add(new BrowserActionDecision(actionId, action.type(), effect, RiskLevel.CRITICAL, targetHost,
                        false, false, List.of("Live-money execution is outside the default trusted AI path."), evidence, reason));
                continue;
            }

            boolean protectedDataOffDevice = request.protectedData()
                    && isRemoteHost(targetHost)
                    && sendsPotentialUserData(action.type(), effect);

            CompiledPolicyDecision policyDecision = policy.evaluate(new PolicyEvaluationRequest(
                    request.agentId(),
                    "browser:" + action.type().name().toLowerCase(Locale.ROOT),
                    "browser",
                    risk,
                    false,
                    protectedDataOffDevice,
                    request.mode(),
                    Map.of(
                            "runtime", RUNTIME_ID,
                            "host", targetHost,
                            "actionType", action.type().name(),
                            "effect", effect.name(),
                            "protectedData", Boolean.toString(request.protectedData())
                    )));

            if (!policyDecision.allowed()) {
                String reason = actionId + ":POLICY_DENIED";
                blocked.add(reason);
                decisions.add(new BrowserActionDecision(actionId, action.type(), effect, risk, targetHost,
                        false, false, policyDecision.reasons(), evidence, reason));
                continue;
            }

            requiresApproval |= policyDecision.requiresApproval();
            decisions.add(new BrowserActionDecision(actionId, action.type(), effect, risk, targetHost,
                    true, policyDecision.requiresApproval(), policyDecision.reasons(), evidence,
                    policyDecision.requiresApproval() ? "Eligible only after owner approval." : "Eligible inside the current deterministic policy envelope."));
        }

        BrowserPlanStatus status;
        boolean executableNow = false;
        String summary;
        if (!blocked.isEmpty()) {
            status = BrowserPlanStatus.BLOCKED;
            summary = "Browser workflow contains one or more blocked actions. Nothing should execute.";
        } else if (!runtimeEnabled) {
            status = BrowserPlanStatus.RUNTIME_UNAVAILABLE;
            summary = "The generic browser adapter is implemented, but the local WebDriver runtime is disabled.";
        } else if (!physicalValidated) {
            status = BrowserPlanStatus.PHYSICAL_VALIDATION_REQUIRED;
            summary = "Repository browser support exists, but the target physical browser/runtime has not been validated.";
        } else if (requiresApproval) {
            status = BrowserPlanStatus.APPROVAL_REQUIRED;
            summary = "The workflow is policy-eligible but includes external side effects that require owner approval.";
        } else {
            status = BrowserPlanStatus.READY;
            executableNow = true;
            summary = "The workflow is eligible for governed local browser execution.";
        }

        return new BrowserWorkflowPlan(status, executableNow, requiresApproval, RUNTIME_ID,
                PHYSICAL_MACHINE_STATUS, decisions, blocked, summary);
    }

    public BrowserExecutionResponse execute(BrowserExecuteRequest request) {
        if (request == null || request.workflow() == null) throw new IllegalArgumentException("Browser execution workflow is required");
        BrowserPlanRequest workflow = request.workflow();
        if (workflow.taskId() != null) tasks.getRequired(workflow.taskId());

        BrowserWorkflowPlan plan = plan(workflow);
        InvocationAuditEntity entry = audit.start(
                workflow.taskId(), workflow.agentId(), InvocationKind.TOOL, RUNTIME_ID,
                Map.of(
                        "mode", workflow.mode().name(),
                        "actionCount", workflow.actions().size(),
                        "allowedDomains", normalizeDomains(workflow.allowedDomains()),
                        "protectedData", workflow.protectedData(),
                        "valuesRedacted", true,
                        "filesRedacted", true
                ));

        if (control.isEmergencyStopActive()) {
            return finish(entry, BrowserExecutionStatus.CANCELLED, InvocationStatus.CANCELLED,
                    "Emergency stop is active", "", List.of());
        }
        if (plan.status() == BrowserPlanStatus.BLOCKED) {
            return finish(entry, BrowserExecutionStatus.BLOCKED, InvocationStatus.BLOCKED,
                    String.join(" | ", plan.blockedReasons()), "", List.of());
        }
        if (!runtimeEnabled) {
            return finish(entry, BrowserExecutionStatus.RUNTIME_UNAVAILABLE, InvocationStatus.BLOCKED,
                    "Local WebDriver runtime is disabled", "", List.of());
        }
        if (!physicalValidated) {
            return finish(entry, BrowserExecutionStatus.PHYSICAL_VALIDATION_REQUIRED, InvocationStatus.BLOCKED,
                    "Physical-PC browser validation remains pending", "", List.of());
        }

        try {
            directAuthority.requireRunningSpecialist(
                    workflow.taskId(), workflow.agentId(), DIRECT_TOOL_FAMILY, workflow.mode());
        } catch (RuntimeException exception) {
            return finish(entry, BrowserExecutionStatus.BLOCKED, InvocationStatus.BLOCKED,
                    "Direct browser execution authority denied: " + safeMessage(exception), "", List.of());
        }

        if (plan.requiresApproval()
                && !approvals.hasApproved(workflow.taskId(), APPROVAL_ACTION)) {
            return finish(entry, BrowserExecutionStatus.APPROVAL_REQUIRED, InvocationStatus.BLOCKED,
                    "Owner approval is required for " + APPROVAL_ACTION, "", List.of());
        }

        try {
            W3cWebDriverBrowserAdapter.AdapterExecution result = adapter.execute(
                    webdriverEndpoint,
                    browserName,
                    workflow.actions(),
                    request.valuesByRef(),
                    request.filesByRef(),
                    normalizeDomains(workflow.allowedDomains()));
            if (!result.success()) {
                return finish(entry, BrowserExecutionStatus.FAILED, InvocationStatus.FAILED,
                        result.detail(), result.finalUrl(), result.actions());
            }
            return finish(entry, BrowserExecutionStatus.SUCCEEDED, InvocationStatus.SUCCEEDED,
                    result.detail(), result.finalUrl(), result.actions());
        } catch (RuntimeException exception) {
            return finish(entry, BrowserExecutionStatus.FAILED, InvocationStatus.FAILED,
                    safeMessage(exception), "", List.of());
        }
    }

    public BrowserRuntimeStatus status() {
        String detail;
        if (!runtimeEnabled) {
            detail = "Repository adapter and sandboxed download evidence are implemented. Runtime activation is disabled until the owner PC is prepared.";
        } else if (!physicalValidated) {
            detail = "Runtime is enabled, but physical validation is still required before browser actions may execute.";
        } else {
            detail = "Runtime is enabled and marked physically validated; durable specialist authority, owner policy and approval gates still apply to every workflow.";
        }
        return new BrowserRuntimeStatus(RUNTIME_ID, true, runtimeEnabled, physicalValidated,
                webdriverEndpoint, PHYSICAL_MACHINE_STATUS, detail);
    }

    private BrowserExecutionResponse finish(
            InvocationAuditEntity entry,
            BrowserExecutionStatus status,
            InvocationStatus auditStatus,
            String detail,
            String finalUrl,
            List<BrowserActionResult> actions) {
        audit.finish(entry.getId(), auditStatus, detail, Map.of(
                "status", status.name(),
                "actionResults", actions.size(),
                "finalUrlPresent", finalUrl != null && !finalUrl.isBlank()
        ));
        return new BrowserExecutionResponse(status, detail, finalUrl, actions, entry.getId());
    }

    private void validateActionShape(BrowserAction action) {
        switch (action.type()) {
            case CLICK, EXTRACT_TEXT -> requireSelector(action);
            case TYPE -> {
                requireSelector(action);
                if (action.valueRef().isBlank()) throw new IllegalArgumentException("valueRef is required for TYPE; literal text is intentionally not embedded in the workflow plan");
            }
            case UPLOAD -> {
                requireSelector(action);
                if (action.fileRef().isBlank()) throw new IllegalArgumentException("fileRef is required for UPLOAD");
            }
            case DOWNLOAD -> {
                requireSelector(action);
                if (action.fileRef().isBlank()) throw new IllegalArgumentException("fileRef is required for DOWNLOAD so the expected artifact filename stays ephemeral");
                if (action.timeoutSeconds() != null && (action.timeoutSeconds() < 1 || action.timeoutSeconds() > 30)) {
                    throw new IllegalArgumentException("DOWNLOAD timeoutSeconds must be between 1 and 30");
                }
            }
            case WAIT -> {
                if (action.timeoutSeconds() != null && (action.timeoutSeconds() < 0 || action.timeoutSeconds() > 30)) {
                    throw new IllegalArgumentException("WAIT timeoutSeconds must be between 0 and 30");
                }
            }
            case NAVIGATE, SCREENSHOT -> {
                // NAVIGATE is validated separately.
            }
        }
    }

    private void requireSelector(BrowserAction action) {
        if (action.selector().isBlank()) throw new IllegalArgumentException("CSS selector is required for " + action.type().name());
    }

    private String validateNavigation(String rawUrl, Set<String> allowedDomains) {
        if (rawUrl == null || rawUrl.isBlank()) throw new IllegalArgumentException("NAVIGATE requires a URL");
        URI uri = URI.create(rawUrl.trim());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Only http/https browser navigation is allowed");
        }
        if (uri.getUserInfo() != null) throw new IllegalArgumentException("URLs containing embedded credentials are not allowed");
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isBlank()) throw new IllegalArgumentException("Navigation URL must include a host");
        if (host.equals("169.254.169.254") || host.equals("metadata.google.internal")) {
            throw new IllegalArgumentException("Cloud metadata endpoints are not browser-eligible targets");
        }
        if (!domainAllowed(host, allowedDomains)) {
            throw new IllegalArgumentException("Navigation host is outside the workflow domain allowlist: " + host);
        }
        return host;
    }

    private BrowserEffect effectiveEffect(BrowserAction action) {
        BrowserEffect minimum = switch (action.type()) {
            case NAVIGATE, SCREENSHOT, EXTRACT_TEXT, WAIT -> BrowserEffect.OBSERVE;
            case TYPE, DOWNLOAD -> BrowserEffect.LOCAL_DRAFT;
            case CLICK, UPLOAD -> BrowserEffect.EXTERNAL_CHANGE;
        };
        BrowserEffect declared = action.effect() == null ? minimum : action.effect();
        return declared.ordinal() < minimum.ordinal() ? minimum : declared;
    }

    private RiskLevel riskFor(BrowserEffect effect) {
        return switch (effect) {
            case OBSERVE -> RiskLevel.LOW;
            case LOCAL_DRAFT -> RiskLevel.MEDIUM;
            case EXTERNAL_CHANGE -> RiskLevel.HIGH;
            case DESTRUCTIVE_CHANGE, SECURITY_CHANGE, FINANCIAL_CHANGE -> RiskLevel.CRITICAL;
        };
    }

    private List<String> evidenceFor(BrowserActionType type, BrowserEffect effect) {
        List<String> evidence = new ArrayList<>();
        if (type == BrowserActionType.NAVIGATE) evidence.add("Record resulting URL/origin and reject unexpected off-allowlist redirects.");
        if (type == BrowserActionType.SCREENSHOT) evidence.add("Store or hash screenshot evidence without exposing secrets.");
        if (type == BrowserActionType.EXTRACT_TEXT) evidence.add("Record bounded extracted text or a content hash as verification evidence.");
        if (type == BrowserActionType.DOWNLOAD) {
            evidence.add("Accept only an explicit single-file expectation inside an isolated download directory.");
            evidence.add("Verify a regular non-symlink artifact and record relative path, size and SHA-256 before claiming success.");
        }
        if (effect.ordinal() >= BrowserEffect.EXTERNAL_CHANGE.ordinal()) {
            evidence.add("Capture before/after state evidence for the external change.");
            evidence.add("Verify the resulting page state before claiming success.");
        }
        if (type == BrowserActionType.TYPE) evidence.add("Audit the value reference only; never log the literal typed value.");
        if (type == BrowserActionType.UPLOAD || type == BrowserActionType.DOWNLOAD) {
            evidence.add("Audit the file reference only; never copy file contents into invocation metadata.");
        }
        return List.copyOf(evidence);
    }

    private boolean sendsPotentialUserData(BrowserActionType type, BrowserEffect effect) {
        return type == BrowserActionType.TYPE
                || type == BrowserActionType.UPLOAD
                || effect.ordinal() >= BrowserEffect.EXTERNAL_CHANGE.ordinal();
    }

    private boolean isRemoteHost(String host) {
        String normalized = host == null ? "" : host.toLowerCase(Locale.ROOT);
        return !(normalized.equals("localhost") || normalized.equals("127.0.0.1") || normalized.equals("::1"));
    }

    private Set<String> normalizeDomains(Set<String> rawDomains) {
        Set<String> result = new LinkedHashSet<>();
        if (rawDomains == null) return Set.of();
        for (String raw : rawDomains) {
            if (raw == null || raw.isBlank()) continue;
            String domain = raw.trim().toLowerCase(Locale.ROOT);
            if (domain.startsWith("*.")) domain = domain.substring(2);
            if (domain.contains("://")) {
                URI uri = URI.create(domain);
                domain = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            }
            while (domain.startsWith(".")) domain = domain.substring(1);
            if (!domain.isBlank()) result.add(domain);
        }
        return Set.copyOf(result);
    }

    private boolean domainAllowed(String host, Set<String> allowedDomains) {
        for (String domain : allowedDomains) {
            if (host.equals(domain) || host.endsWith("." + domain)) return true;
        }
        return false;
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
