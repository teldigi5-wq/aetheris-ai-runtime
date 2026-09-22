package io.aetheris.orchestrator;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.policy.ActionRequest;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.policy.OwnerPolicyService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerPolicyServiceTest {

    private final OwnerPolicyService policyService = new OwnerPolicyService();

    @Test
    void zeroCostModeRejectsBillableActions() {
        var decision = policyService.evaluate(new ActionRequest(
                "ai-engineer",
                "call-premium-model",
                RiskLevel.LOW,
                true,
                true,
                OperationMode.ZERO_COST));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("ZERO_COST");
    }

    @Test
    void privateModeRejectsOffDeviceDataTransfer() {
        var decision = policyService.evaluate(new ActionRequest(
                "research-scientist",
                "upload-private-document",
                RiskLevel.MEDIUM,
                false,
                true,
                OperationMode.PRIVATE));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("PRIVATE");
    }

    @Test
    void highRiskActionRequiresApproval() {
        var decision = policyService.evaluate(new ActionRequest(
                "pc-care-engineer",
                "install-driver",
                RiskLevel.HIGH,
                false,
                false,
                OperationMode.BALANCED));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.requiresApproval()).isTrue();
    }
}
