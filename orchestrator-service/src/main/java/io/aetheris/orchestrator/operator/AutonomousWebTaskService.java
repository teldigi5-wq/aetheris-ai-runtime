package io.aetheris.orchestrator.operator;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.aetheris.orchestrator.operator.BrowserTypes.*;
import static io.aetheris.orchestrator.operator.SiteSkillTypes.*;

@Service
public class AutonomousWebTaskService {

    private final SiteSkillCatalogService catalog;
    private final BrowserOperatorService browser;

    public AutonomousWebTaskService(SiteSkillCatalogService catalog, BrowserOperatorService browser) {
        this.catalog = catalog;
        this.browser = browser;
    }

    public List<SiteSkillDescriptor> skills() {
        return catalog.skills();
    }

    public WebTaskCompilation compile(WebTaskRequest request) {
        if (request == null) throw new IllegalArgumentException("Web task request is required");
        if (request.agentId() == null || request.agentId().isBlank()) throw new IllegalArgumentException("agentId is required");

        SiteSkillDescriptor skill = catalog.getRequired(request.skillId());
        List<BrowserAction> actions = catalog.compileActions(skill, request.parameters());
        BrowserPlanRequest workflow = new BrowserPlanRequest(
                request.taskId(),
                request.agentId(),
                request.mode(),
                request.protectedData(),
                skill.allowedDomains(),
                actions);
        BrowserWorkflowPlan browserPlan = browser.plan(workflow);

        int maxAttempts = skill.kind() == SiteSkillKind.OBSERVE ? 2 : 1;
        boolean blindRetryAllowed = skill.kind() == SiteSkillKind.OBSERVE;
        List<String> warnings = new ArrayList<>();
        if (!skill.physicallyValidated()) {
            warnings.add("Site skill is a repository template and is not physically validated on the owner PC.");
        }
        if (skill.kind() == SiteSkillKind.MUTATE) {
            warnings.add("Mutation skills never receive automatic blind retries because the prior side effect may have partially succeeded.");
        }
        if (skill.authenticationExpected()) {
            warnings.add("Authentication/session state must be owner-controlled; cookies, passwords and browser-profile secrets must not be embedded in the repository or task plan.");
        }

        WebTaskStatus status;
        String summary;
        if (browserPlan.status() == BrowserPlanStatus.BLOCKED) {
            status = WebTaskStatus.BLOCKED;
            summary = "The compiled site skill violates browser or owner-policy boundaries and cannot execute.";
        } else if (!skill.physicallyValidated()) {
            status = WebTaskStatus.SKILL_VALIDATION_REQUIRED;
            summary = "The site-skill template compiled successfully, but its selectors and real website flow require physical validation before execution.";
        } else {
            status = map(browserPlan.status());
            summary = switch (status) {
                case READY -> "The validated site skill is ready for governed browser execution.";
                case APPROVAL_REQUIRED -> "The validated site skill is eligible only after owner approval for its external mutation.";
                case PHYSICAL_VALIDATION_REQUIRED -> "The generic browser runtime itself still requires physical validation.";
                case RUNTIME_UNAVAILABLE -> "The generic browser runtime is currently unavailable.";
                default -> "The site skill is not eligible for execution.";
            };
        }

        return new WebTaskCompilation(
                status,
                skill,
                workflow,
                browserPlan,
                maxAttempts,
                blindRetryAllowed,
                warnings,
                summary);
    }

    public WebTaskExecutionResponse execute(WebTaskExecuteRequest request) {
        if (request == null || request.task() == null) throw new IllegalArgumentException("Web task execution request is required");
        WebTaskCompilation compilation = compile(request.task());
        SiteSkillDescriptor skill = compilation.skill();

        List<String> missingValueRefs = catalog.missingValueRefs(skill, request.valuesByRef());
        if (!missingValueRefs.isEmpty()) {
            return response(WebTaskStatus.BLOCKED, skill, 0, false, null,
                    "Missing required ephemeral value references: " + String.join(", ", missingValueRefs));
        }

        if (compilation.status() == WebTaskStatus.BLOCKED
                || compilation.status() == WebTaskStatus.SKILL_VALIDATION_REQUIRED
                || compilation.status() == WebTaskStatus.PHYSICAL_VALIDATION_REQUIRED
                || compilation.status() == WebTaskStatus.RUNTIME_UNAVAILABLE) {
            return response(compilation.status(), skill, 0, false, null, compilation.summary());
        }

        int attempts = 0;
        boolean retried = false;
        BrowserExecutionResponse last = null;
        int maxAttempts = compilation.maxAutomaticAttempts();

        while (attempts < maxAttempts) {
            attempts++;
            last = browser.execute(new BrowserExecuteRequest(
                    compilation.browserWorkflow(),
                    request.valuesByRef(),
                    request.filesByRef()));
            WebTaskStatus mapped = map(last.status());
            if (mapped == WebTaskStatus.SUCCEEDED) {
                return response(mapped, skill, attempts, retried, last,
                        "Site skill completed with browser execution evidence. Success still means the declared evidence contract was satisfied, not that unrelated page state was verified.");
            }
            if (!compilation.blindRetryAllowed() || mapped != WebTaskStatus.FAILED || attempts >= maxAttempts) {
                return response(mapped, skill, attempts, retried, last, last.detail());
            }
            retried = true;
        }

        return response(WebTaskStatus.FAILED, skill, attempts, retried, last,
                last == null ? "No browser attempt was executed" : last.detail());
    }

    private WebTaskExecutionResponse response(
            WebTaskStatus status,
            SiteSkillDescriptor skill,
            int attempts,
            boolean retried,
            BrowserExecutionResponse browserExecution,
            String detail) {
        return new WebTaskExecutionResponse(
                status,
                skill.id(),
                attempts,
                retried,
                browserExecution,
                skill.evidenceRequirements(),
                detail);
    }

    private WebTaskStatus map(BrowserPlanStatus status) {
        return switch (status) {
            case READY -> WebTaskStatus.READY;
            case APPROVAL_REQUIRED -> WebTaskStatus.APPROVAL_REQUIRED;
            case BLOCKED -> WebTaskStatus.BLOCKED;
            case PHYSICAL_VALIDATION_REQUIRED -> WebTaskStatus.PHYSICAL_VALIDATION_REQUIRED;
            case RUNTIME_UNAVAILABLE -> WebTaskStatus.RUNTIME_UNAVAILABLE;
        };
    }

    private WebTaskStatus map(BrowserExecutionStatus status) {
        return switch (status) {
            case SUCCEEDED -> WebTaskStatus.SUCCEEDED;
            case FAILED -> WebTaskStatus.FAILED;
            case BLOCKED -> WebTaskStatus.BLOCKED;
            case APPROVAL_REQUIRED -> WebTaskStatus.APPROVAL_REQUIRED;
            case PHYSICAL_VALIDATION_REQUIRED -> WebTaskStatus.PHYSICAL_VALIDATION_REQUIRED;
            case RUNTIME_UNAVAILABLE -> WebTaskStatus.RUNTIME_UNAVAILABLE;
            case CANCELLED -> WebTaskStatus.CANCELLED;
        };
    }
}
