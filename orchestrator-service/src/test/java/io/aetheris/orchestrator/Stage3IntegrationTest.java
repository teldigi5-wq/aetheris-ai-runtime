package io.aetheris.orchestrator;

import io.aetheris.orchestrator.agent.ModelClass;
import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.approval.ApprovalDecisionRequest;
import io.aetheris.orchestrator.approval.ApprovalEntity;
import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.approval.CreateApprovalRequest;
import io.aetheris.orchestrator.mcp.McpCapabilityGrantRequest;
import io.aetheris.orchestrator.mcp.McpRegistryService;
import io.aetheris.orchestrator.mcp.McpServerEntity;
import io.aetheris.orchestrator.mcp.McpServerRegistrationRequest;
import io.aetheris.orchestrator.model.ModelRouteDecision;
import io.aetheris.orchestrator.model.ModelRouteRequest;
import io.aetheris.orchestrator.model.ModelRouterService;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.rules.OwnerRuleRevisionRequest;
import io.aetheris.orchestrator.rules.OwnerRuleService;
import io.aetheris.orchestrator.rules.RuleEffect;
import io.aetheris.orchestrator.task.CreateTaskRequest;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.task.TaskEventStreamService;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.task.TaskState;
import io.aetheris.orchestrator.task.TaskTransitionRequest;
import io.aetheris.orchestrator.tool.SafeToolRegistryService;
import io.aetheris.orchestrator.tool.ToolAccessDecision;
import io.aetheris.orchestrator.tool.ToolAccessRequest;
import io.aetheris.orchestrator.workflow.EngineeringWorkflowEntity;
import io.aetheris.orchestrator.workflow.EngineeringWorkflowPhase;
import io.aetheris.orchestrator.workflow.EngineeringWorkflowRequest;
import io.aetheris.orchestrator.workflow.EngineeringWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class Stage3IntegrationTest {

    @Autowired OwnerRuleService ownerRules;
    @Autowired SafeToolRegistryService tools;
    @Autowired TaskService tasks;
    @Autowired TaskEventStreamService taskEvents;
    @Autowired ApprovalService approvals;
    @Autowired McpRegistryService mcp;
    @Autowired ModelRouterService modelRouter;
    @Autowired EngineeringWorkflowService workflows;

    @Test
    void ownerRuleCompilerCanDenyAHighImpactTool() {
        ownerRules.createRevision(new OwnerRuleRevisionRequest(
                "deny-github-write-stage3",
                "Block GitHub proposed writes in this test",
                "tool",
                "action=tool:github.propose-change",
                RuleEffect.DENY,
                1000,
                true));

        ToolAccessDecision decision = tools.evaluate(new ToolAccessRequest(
                "backend-engineer",
                "github.propose-change",
                OperationMode.BALANCED,
                false,
                true,
                Map.of()));

        assertThat(decision.policy().allowed()).isFalse();
        assertThat(decision.policy().matchedRuleKeys()).contains("deny-github-write-stage3");
    }

    @Test
    void approvalDecisionControlsTaskExecutionStateAndEventsAreReplayable() {
        TaskEntity task = tasks.create(new CreateTaskRequest("Approval test", "Perform guarded operation", OperationMode.BALANCED));
        tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.PLANNING, "executive-planner", "Planning"));

        ApprovalEntity approval = approvals.request(new CreateApprovalRequest(
                task.getId(), "system.change", "Apply guarded change", RiskLevel.HIGH));
        assertThat(tasks.getRequired(task.getId()).getState()).isEqualTo(TaskState.AWAITING_APPROVAL);

        approvals.decide(approval.getId(), new ApprovalDecisionRequest(true, "Approved for test"));
        assertThat(tasks.getRequired(task.getId()).getState()).isEqualTo(TaskState.RUNNING);
        assertThat(taskEvents.history(task.getId())).isNotEmpty();
        assertThat(taskEvents.history(task.getId()))
                .extracting(event -> event.state())
                .contains(TaskState.QUEUED, TaskState.PLANNING, TaskState.AWAITING_APPROVAL, TaskState.RUNNING);
    }

    @Test
    void mcpRegistryEnforcesApprovedCapabilitiesAndKnownAgents() {
        McpServerEntity server = mcp.register(new McpServerRegistrationRequest(
                "stage3-github-mcp",
                "Stage 3 GitHub MCP",
                "http://127.0.0.1:19090/mcp",
                true,
                true,
                Set.of("repo.read"),
                Set.of("public")));

        assertThat(mcp.grant(server.getId(), new McpCapabilityGrantRequest(
                "backend-engineer", "repo.read", "public")).isEnabled()).isTrue();

        assertThatThrownBy(() -> mcp.grant(server.getId(), new McpCapabilityGrantRequest(
                "backend-engineer", "repo.write", "public")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroCostRoutingDoesNotInventACloudProvider() {
        ModelRouteDecision decision = modelRouter.route(new ModelRouteRequest(
                ModelClass.CODING,
                OperationMode.ZERO_COST,
                false,
                false));

        assertThat(decision.routable()).isFalse();
        assertThat(decision.reason()).contains("zero-cost");
    }

    @Test
    void engineeringWorkflowMovesThroughQaAndIndependentVerification() {
        EngineeringWorkflowEntity workflow = workflows.start(new EngineeringWorkflowRequest(
                "Stage 3 workflow test",
                "Implement and independently verify a safe code change",
                OperationMode.BALANCED));

        assertThat(workflow.getPhase()).isEqualTo(EngineeringWorkflowPhase.ENGINEERING);
        assertThat(tasks.getRequired(workflow.getTaskId()).getState()).isEqualTo(TaskState.RUNNING);

        workflow = workflows.advance(workflow.getId());
        assertThat(workflow.getPhase()).isEqualTo(EngineeringWorkflowPhase.QA);
        assertThat(tasks.getRequired(workflow.getTaskId()).getState()).isEqualTo(TaskState.VERIFYING);

        workflow = workflows.advance(workflow.getId());
        assertThat(workflow.getPhase()).isEqualTo(EngineeringWorkflowPhase.VERIFICATION);

        workflow = workflows.advance(workflow.getId());
        assertThat(workflow.getPhase()).isEqualTo(EngineeringWorkflowPhase.COMPLETED);
        assertThat(tasks.getRequired(workflow.getTaskId()).getState()).isEqualTo(TaskState.COMPLETED);
    }
}
