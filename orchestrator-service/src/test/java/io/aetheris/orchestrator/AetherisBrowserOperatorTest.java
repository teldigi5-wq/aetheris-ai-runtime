package io.aetheris.orchestrator;

import io.aetheris.orchestrator.operator.BrowserOperatorService;
import io.aetheris.orchestrator.operator.BrowserTypes.BrowserAction;
import io.aetheris.orchestrator.operator.BrowserTypes.BrowserActionType;
import io.aetheris.orchestrator.operator.BrowserTypes.BrowserEffect;
import io.aetheris.orchestrator.operator.BrowserTypes.BrowserExecuteRequest;
import io.aetheris.orchestrator.operator.BrowserTypes.BrowserExecutionStatus;
import io.aetheris.orchestrator.operator.BrowserTypes.BrowserPlanRequest;
import io.aetheris.orchestrator.operator.BrowserTypes.BrowserPlanStatus;
import io.aetheris.orchestrator.policy.OperationMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AetherisBrowserOperatorTest {

    @Autowired BrowserOperatorService browser;

    @Test
    void runtimeTruthDoesNotPretendTheOwnerPcBrowserIsReady() {
        var status = browser.status();
        assertThat(status.repositoryAdapterImplemented()).isTrue();
        assertThat(status.runtimeEnabled()).isFalse();
        assertThat(status.physicalValidated()).isFalse();
        assertThat(status.physicalMachineStatus()).isEqualTo("BLOCKED_PENDING_HARDWARE");
        assertThat(status.detail()).contains("sandboxed download evidence");
    }

    @Test
    void readOnlyGenericBrowserPlanIsPolicyEligibleButRuntimeRemainsUnavailable() {
        var plan = browser.plan(new BrowserPlanRequest(
                null,
                "research-scientist",
                OperationMode.BALANCED,
                false,
                Set.of("linkedin.com"),
                List.of(
                        action("open", BrowserActionType.NAVIGATE, "https://www.linkedin.com/", "", "", "", BrowserEffect.OBSERVE),
                        action("read", BrowserActionType.EXTRACT_TEXT, "", "main", "", "", BrowserEffect.OBSERVE)
                )));

        assertThat(plan.status()).isEqualTo(BrowserPlanStatus.RUNTIME_UNAVAILABLE);
        assertThat(plan.blockedReasons()).isEmpty();
        assertThat(plan.requiresApproval()).isFalse();
        assertThat(plan.physicalMachineStatus()).isEqualTo("BLOCKED_PENDING_HARDWARE");
    }

    @Test
    void externalBrowserMutationRequiresOwnerApproval() {
        var plan = browser.plan(new BrowserPlanRequest(
                null,
                "backend-engineer",
                OperationMode.BALANCED,
                false,
                Set.of("vercel.com"),
                List.of(
                        action("open", BrowserActionType.NAVIGATE, "https://vercel.com/dashboard", "", "", "", BrowserEffect.OBSERVE),
                        action("deploy", BrowserActionType.CLICK, "", "button[data-action='deploy']", "", "", BrowserEffect.EXTERNAL_CHANGE)
                )));

        assertThat(plan.requiresApproval()).isTrue();
        assertThat(plan.actions()).anyMatch(step -> step.actionId().equals("deploy") && step.requiresApproval());
    }

    @Test
    void privateModeBlocksProtectedTextFromBeingSentToRemoteSite() {
        var plan = browser.plan(new BrowserPlanRequest(
                null,
                "research-scientist",
                OperationMode.PRIVATE,
                true,
                Set.of("linkedin.com"),
                List.of(
                        action("open", BrowserActionType.NAVIGATE, "https://www.linkedin.com/in/example/edit", "", "", "", BrowserEffect.OBSERVE),
                        action("type", BrowserActionType.TYPE, "", "textarea", "profile-about", "", BrowserEffect.LOCAL_DRAFT)
                )));

        assertThat(plan.status()).isEqualTo(BrowserPlanStatus.BLOCKED);
        assertThat(plan.blockedReasons()).contains("type:POLICY_DENIED");
    }

    @Test
    void browserCannotNavigateOutsideExplicitDomainAllowlist() {
        var plan = browser.plan(new BrowserPlanRequest(
                null,
                "research-scientist",
                OperationMode.BALANCED,
                false,
                Set.of("linkedin.com"),
                List.of(action("escape", BrowserActionType.NAVIGATE, "https://example.com/", "", "", "", BrowserEffect.OBSERVE))));

        assertThat(plan.status()).isEqualTo(BrowserPlanStatus.BLOCKED);
        assertThat(plan.blockedReasons()).anyMatch(reason -> reason.contains("outside the workflow domain allowlist"));
    }

    @Test
    void nonHttpSchemesAreRejected() {
        var plan = browser.plan(new BrowserPlanRequest(
                null,
                "research-scientist",
                OperationMode.BALANCED,
                false,
                Set.of("linkedin.com"),
                List.of(action("script", BrowserActionType.NAVIGATE, "javascript:alert(1)", "", "", "", BrowserEffect.OBSERVE))));

        assertThat(plan.status()).isEqualTo(BrowserPlanStatus.BLOCKED);
        assertThat(plan.blockedReasons()).anyMatch(reason -> reason.contains("Only http/https"));
    }

    @Test
    void financialBrowserMutationIsHardBlocked() {
        var plan = browser.plan(new BrowserPlanRequest(
                null,
                "research-scientist",
                OperationMode.BALANCED,
                false,
                Set.of("example.com"),
                List.of(
                        action("open", BrowserActionType.NAVIGATE, "https://example.com/trade", "", "", "", BrowserEffect.OBSERVE),
                        action("buy", BrowserActionType.CLICK, "", "button.buy", "", "", BrowserEffect.FINANCIAL_CHANGE)
                )));

        assertThat(plan.status()).isEqualTo(BrowserPlanStatus.BLOCKED);
        assertThat(plan.blockedReasons()).contains("buy:LIVE_MONEY_BROWSER_ACTION_BLOCKED");
    }

    @Test
    void downloadPlanRequiresVerifiedArtifactEvidenceButStillRespectsRuntimeTruth() {
        var plan = browser.plan(new BrowserPlanRequest(
                null,
                "research-scientist",
                OperationMode.BALANCED,
                false,
                Set.of("example.com"),
                List.of(
                        action("open", BrowserActionType.NAVIGATE, "https://example.com/report", "", "", "", BrowserEffect.OBSERVE),
                        action("download", BrowserActionType.DOWNLOAD, "", "a[data-download='report']", "", "download.report", BrowserEffect.OBSERVE)
                )));

        assertThat(plan.status()).isEqualTo(BrowserPlanStatus.RUNTIME_UNAVAILABLE);
        assertThat(plan.blockedReasons()).isEmpty();
        assertThat(plan.actions()).anySatisfy(step -> {
            if (step.actionId().equals("download")) {
                assertThat(step.effect()).isEqualTo(BrowserEffect.LOCAL_DRAFT);
                assertThat(step.evidenceRequired()).anyMatch(evidence -> evidence.contains("SHA-256"));
            }
        });
    }

    @Test
    void downloadWithoutSelectorAndFileReferenceFailsClosed() {
        var plan = browser.plan(new BrowserPlanRequest(
                null,
                "research-scientist",
                OperationMode.BALANCED,
                false,
                Set.of("example.com"),
                List.of(
                        action("open", BrowserActionType.NAVIGATE, "https://example.com/report", "", "", "", BrowserEffect.OBSERVE),
                        action("download", BrowserActionType.DOWNLOAD, "", "", "", "", BrowserEffect.OBSERVE)
                )));

        assertThat(plan.status()).isEqualTo(BrowserPlanStatus.BLOCKED);
        assertThat(plan.blockedReasons()).anyMatch(reason -> reason.contains("CSS selector is required for DOWNLOAD"));
    }

    @Test
    void executeFailsClosedWhilePhysicalRuntimeIsDisabled() {
        BrowserPlanRequest workflow = new BrowserPlanRequest(
                null,
                "research-scientist",
                OperationMode.BALANCED,
                false,
                Set.of("example.com"),
                List.of(action("open", BrowserActionType.NAVIGATE, "https://example.com/", "", "", "", BrowserEffect.OBSERVE)));

        var response = browser.execute(new BrowserExecuteRequest(workflow, Map.of(), Map.of()));
        assertThat(response.status()).isEqualTo(BrowserExecutionStatus.RUNTIME_UNAVAILABLE);
        assertThat(response.auditId()).isNotNull();
    }

    private BrowserAction action(
            String id,
            BrowserActionType type,
            String url,
            String selector,
            String valueRef,
            String fileRef,
            BrowserEffect effect) {
        return new BrowserAction(id, type, url, selector, valueRef, fileRef, effect, id, null);
    }
}
