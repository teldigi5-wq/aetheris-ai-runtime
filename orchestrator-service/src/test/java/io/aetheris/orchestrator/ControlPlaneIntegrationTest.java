package io.aetheris.orchestrator;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.approval.ApprovalDecisionRequest;
import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.approval.ApprovalStatus;
import io.aetheris.orchestrator.approval.CreateApprovalRequest;
import io.aetheris.orchestrator.model.LocalModelService;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.rules.OwnerRuleRevisionRequest;
import io.aetheris.orchestrator.rules.OwnerRuleService;
import io.aetheris.orchestrator.rules.RuleEffect;
import io.aetheris.orchestrator.task.CreateTaskRequest;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.task.TaskState;
import io.aetheris.orchestrator.task.TaskTransitionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ControlPlaneIntegrationTest {

    @Autowired TaskService tasks;
    @Autowired ApprovalService approvals;
    @Autowired OwnerRuleService rules;
    @Autowired LocalModelService localModel;

    @Test
    void persistsTaskApprovalAndVersionedOwnerRules() {
        var task = tasks.create(new CreateTaskRequest(
                "Prepare GitHub release",
                "Review the project and prepare a release without publishing it.",
                OperationMode.ZERO_COST));

        var planning = tasks.transition(task.getId(), new TaskTransitionRequest(
                TaskState.PLANNING,
                "executive-planner",
                "Creating a safe release plan"));

        assertThat(planning.getState()).isEqualTo(TaskState.PLANNING);
        assertThat(tasks.getRequired(task.getId()).getMode()).isEqualTo(OperationMode.ZERO_COST);

        var approval = approvals.request(new CreateApprovalRequest(
                task.getId(),
                "github.publish-release",
                "Publish the prepared release to the public GitHub repository",
                RiskLevel.HIGH));

        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(approvals.pending()).extracting("id").contains(approval.getId());

        var decided = approvals.decide(approval.getId(), new ApprovalDecisionRequest(true, "Owner approved release publication"));
        assertThat(decided.getStatus()).isEqualTo(ApprovalStatus.APPROVED);

        var v1 = rules.createRevision(new OwnerRuleRevisionRequest(
                "github-publish",
                "Never publish to GitHub without approval",
                "github",
                "action in [push, merge, release, publish]",
                RuleEffect.REQUIRE_APPROVAL,
                900,
                true));

        var v2 = rules.createRevision(new OwnerRuleRevisionRequest(
                "github-publish",
                "Require approval for public GitHub publication; local commits are allowed",
                "github",
                "publicAction == true",
                RuleEffect.REQUIRE_APPROVAL,
                900,
                true));

        assertThat(v1.getVersionNumber()).isEqualTo(1);
        assertThat(v2.getVersionNumber()).isEqualTo(2);
        assertThat(rules.history("github-publish")).hasSize(2);
        assertThat(rules.activeRules())
                .filteredOn(rule -> rule.getRuleKey().equals("github-publish"))
                .singleElement()
                .extracting("versionNumber")
                .isEqualTo(2);
    }

    @Test
    void reportsLocalModelUnavailableWithoutCrashingWhenNoPcEndpointExists() {
        var health = localModel.health();
        assertThat(health.available()).isFalse();
        assertThat(health.provider()).isEqualTo("ollama");
    }
}
