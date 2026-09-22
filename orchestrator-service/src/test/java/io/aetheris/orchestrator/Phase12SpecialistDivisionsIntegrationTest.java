package io.aetheris.orchestrator;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.approval.ApprovalDecisionRequest;
import io.aetheris.orchestrator.approval.ApprovalEntity;
import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.approval.CreateApprovalRequest;
import io.aetheris.orchestrator.execution.ToolExecutionRequest;
import io.aetheris.orchestrator.execution.ToolExecutionResponse;
import io.aetheris.orchestrator.execution.ToolExecutionService;
import io.aetheris.orchestrator.execution.ToolExecutionStatus;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.task.CreateTaskRequest;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.task.TaskState;
import io.aetheris.orchestrator.task.TaskTransitionRequest;
import io.aetheris.orchestrator.tool.SafeToolRegistryService;
import io.aetheris.orchestrator.tool.SpecialistToolAuthorizationService;
import io.aetheris.orchestrator.tool.ToolAccessDecision;
import io.aetheris.orchestrator.tool.ToolAccessRequest;
import io.aetheris.orchestrator.tool.ToolDescriptor;
import io.aetheris.orchestrator.tool.ToolTransport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class Phase12SpecialistDivisionsIntegrationTest {

    @Autowired SafeToolRegistryService tools;
    @Autowired SpecialistToolAuthorizationService specialistTools;
    @Autowired ToolExecutionService executions;
    @Autowired TaskService tasks;
    @Autowired ApprovalService approvals;

    @Test
    void everyRegisteredSafeToolHasAnExplicitSpecialistFamilyMapping() {
        assertThat(tools.list()).isNotEmpty();
        assertThat(tools.list())
                .allSatisfy(tool -> assertThat(specialistTools.toolFamilyFor(tool.id()))
                        .as("specialist family for %s", tool.id())
                        .isNotBlank());
    }

    @Test
    void backendEngineerCanReachDeclaredGithubFamilyAndStillUsesOwnerPolicy() {
        ToolAccessDecision decision = tools.evaluate(new ToolAccessRequest(
                "backend-engineer",
                "github.read",
                OperationMode.BALANCED,
                false,
                true,
                Map.of()));

        assertThat(decision.policy().allowed()).isTrue();
        assertThat(decision.policy().requiresApproval()).isFalse();
    }

    @Test
    void highRiskAllowedToolStillRequiresOrdinaryOwnerApproval() {
        ToolAccessDecision decision = tools.evaluate(new ToolAccessRequest(
                "backend-engineer",
                "terminal.execute-workspace",
                OperationMode.BALANCED,
                false,
                false,
                Map.of()));

        assertThat(decision.policy().allowed()).isTrue();
        assertThat(decision.policy().requiresApproval()).isTrue();
    }

    @Test
    void specialistDenyHappensBeforeApprovalAndCannotBeOverriddenByApproval() {
        TaskEntity task = tasks.create(new CreateTaskRequest(
                "Phase 12 specialist boundary",
                "Prove an approved action cannot grant an undeclared specialist tool family",
                OperationMode.BALANCED));
        tasks.transition(task.getId(), new TaskTransitionRequest(
                TaskState.PLANNING,
                "professor-tutor",
                "Prepare specialist boundary proof"));

        ApprovalEntity approval = approvals.request(new CreateApprovalRequest(
                task.getId(),
                "tool:terminal.execute-workspace",
                "Attempt a harmless command only if every policy boundary allows it",
                RiskLevel.HIGH));
        approvals.decide(approval.getId(), new ApprovalDecisionRequest(true, "Approved only for boundary proof"));

        ToolExecutionResponse response = executions.execute(new ToolExecutionRequest(
                task.getId(),
                "professor-tutor",
                "terminal.execute-workspace",
                OperationMode.BALANCED,
                Map.of("command", List.of("echo", "phase12-should-not-execute"))));

        assertThat(response.status()).isEqualTo(ToolExecutionStatus.BLOCKED);
        assertThat(response.detail()).contains("professor-tutor");
        assertThat(response.detail()).contains("terminal");
    }

    @Test
    void lowRiskToolIsDeniedWhenSpecialistDidNotDeclareItsFamily() {
        ToolAccessDecision decision = tools.evaluate(new ToolAccessRequest(
                "research-scientist",
                "github.read",
                OperationMode.BALANCED,
                false,
                false,
                Map.of()));

        assertThat(decision.policy().allowed()).isFalse();
        assertThat(decision.policy().requiresApproval()).isFalse();
        assertThat(decision.policy().reasons()).singleElement()
                .asString()
                .contains("research-scientist")
                .contains("github");
    }

    @Test
    void unmappedFutureToolFailsClosedEvenForAHighlyPrivilegedSpecialist() {
        ToolDescriptor futureTool = new ToolDescriptor(
                "future.admin",
                "Future Admin Tool",
                ToolTransport.OFFICIAL_API,
                java.util.Set.of("admin"),
                RiskLevel.CRITICAL,
                false,
                true);

        SpecialistToolAuthorizationService.Decision decision =
                specialistTools.evaluate("security-architect", futureTool);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.toolFamily()).isNull();
        assertThat(decision.reason()).contains("No specialist tool-family mapping");
    }
}
