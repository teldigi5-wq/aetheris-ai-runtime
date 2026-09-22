package io.aetheris.orchestrator;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.approval.ApprovalDecisionRequest;
import io.aetheris.orchestrator.approval.ApprovalEntity;
import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.approval.CreateApprovalRequest;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.task.CreateTaskRequest;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.task.TaskEventStreamService;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.task.TaskState;
import io.aetheris.orchestrator.task.TaskTransitionRequest;
import io.aetheris.orchestrator.task.TaskVerificationService;
import io.aetheris.orchestrator.workflow.EngineeringWorkflowPhase;
import io.aetheris.orchestrator.workflow.EngineeringWorkflowRequest;
import io.aetheris.orchestrator.workflow.EngineeringWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class Phase12SpecialistVerificationIntegrationTest {

    @Autowired TaskService tasks;
    @Autowired TaskVerificationService verification;
    @Autowired TaskEventStreamService events;
    @Autowired ApprovalService approvals;
    @Autowired EngineeringWorkflowService workflows;

    @Test
    void directCompletionWithoutRecordedVerificationFailsClosed() {
        TaskEntity task = preparedTask("backend-engineer", "qa-engineer");

        assertThatThrownBy(() -> tasks.transition(task.getId(), new TaskTransitionRequest(
                TaskState.COMPLETED,
                "software-architect",
                "Attempt completion without verifier decision")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("passing independent verification decision");

        assertThat(tasks.getRequired(task.getId()).getState()).isEqualTo(TaskState.VERIFYING);
    }

    @Test
    void executionSpecialistCannotVerifyItsOwnWork() {
        TaskEntity task = preparedTask("backend-engineer", "qa-engineer");

        assertThatThrownBy(() -> verification.recordDecision(
                task.getId(),
                "backend-engineer",
                true,
                "SELF_REVIEW",
                "Executor attempts to certify its own work"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot verify its own work");
    }

    @Test
    void qaHandoffAgentCannotSelfPromoteToFinalVerifier() {
        TaskEntity task = preparedTask("backend-engineer", "qa-engineer");

        assertThatThrownBy(() -> verification.recordDecision(
                task.getId(),
                "qa-engineer",
                true,
                "QA_REVIEW",
                "QA agent attempts to become final verifier"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot self-promote");
    }

    @Test
    void knownSpecialistWithoutVerificationCapabilityIsDenied() {
        TaskEntity task = preparedTask("backend-engineer", "qa-engineer");

        assertThatThrownBy(() -> verification.recordDecision(
                task.getId(),
                "frontend-engineer",
                true,
                "PEER_REVIEW",
                "Known but non-verifier specialist attempts certification"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not have an independent verification capability");
    }

    @Test
    void unknownVerifierIdentityIsDenied() {
        TaskEntity task = preparedTask("backend-engineer", "qa-engineer");

        assertThatThrownBy(() -> verification.recordDecision(
                task.getId(),
                "invented-verifier",
                true,
                "FAKE_REVIEW",
                "Unknown identity attempts certification"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Unknown agent");
    }

    @Test
    void ownerApprovalCannotBypassIndependentVerification() {
        TaskEntity task = tasks.create(new CreateTaskRequest(
                "Approved but not verified",
                "Owner approval must not become verifier evidence",
                OperationMode.BALANCED));
        tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.PLANNING, "backend-engineer", "Plan approved task"));

        ApprovalEntity approval = approvals.request(new CreateApprovalRequest(
                task.getId(),
                "phase12.governed-completion",
                "Approve execution but not verification",
                RiskLevel.HIGH));
        approvals.decide(approval.getId(), new ApprovalDecisionRequest(true, "Execution approved"));
        tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.VERIFYING, "qa-engineer", "Execution handed to QA"));

        assertThatThrownBy(() -> tasks.transition(task.getId(), new TaskTransitionRequest(
                TaskState.COMPLETED,
                "software-architect",
                "Attempt to treat owner approval as verification")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("passing independent verification decision");
    }

    @Test
    void failedVerificationDecisionCannotCompleteTask() {
        TaskEntity task = preparedTask("backend-engineer", "qa-engineer");
        verification.recordDecision(
                task.getId(),
                "software-architect",
                false,
                "ACCEPTANCE_CRITERIA",
                "Independent verifier rejected the evidence");

        assertThatThrownBy(() -> tasks.transition(task.getId(), new TaskTransitionRequest(
                TaskState.COMPLETED,
                "software-architect",
                "Attempt completion after failed verification")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("passing independent verification decision");
    }

    @Test
    void validIndependentDecisionAllowsCompletionAndIsAudited() {
        TaskEntity task = preparedTask("backend-engineer", "qa-engineer");
        verification.recordDecision(
                task.getId(),
                "software-architect",
                true,
                "ACCEPTANCE_CRITERIA",
                "Independent verifier accepted the evidence");

        TaskEntity completed = tasks.transition(task.getId(), new TaskTransitionRequest(
                TaskState.COMPLETED,
                "software-architect",
                "Complete after independent verification"));

        assertThat(completed.getState()).isEqualTo(TaskState.COMPLETED);
        assertThat(events.history(task.getId()))
                .anySatisfy(event -> {
                    assertThat(event.agentId()).isEqualTo("software-architect");
                    assertThat(event.metadata()).containsEntry("verificationDecision", "PASS");
                    assertThat(event.metadata()).containsEntry("independent", true);
                    assertThat(event.metadata()).containsEntry("executionAgentId", "backend-engineer");
                    assertThat(event.metadata()).containsEntry("reviewAgentId", "qa-engineer");
                });
    }

    @Test
    void existingEngineeringWorkflowUsesGovernedCompletion() {
        var workflow = workflows.start(new EngineeringWorkflowRequest(
                "Phase 12 governed engineering workflow",
                "Prove engineering completion goes through independent specialist verification",
                OperationMode.BALANCED));

        assertThat(workflow.getPhase()).isEqualTo(EngineeringWorkflowPhase.ENGINEERING);
        workflow = workflows.advance(workflow.getId());
        assertThat(workflow.getPhase()).isEqualTo(EngineeringWorkflowPhase.QA);
        workflow = workflows.advance(workflow.getId());
        assertThat(workflow.getPhase()).isEqualTo(EngineeringWorkflowPhase.VERIFICATION);
        workflow = workflows.advance(workflow.getId());

        assertThat(workflow.getPhase()).isEqualTo(EngineeringWorkflowPhase.COMPLETED);
        assertThat(tasks.getRequired(workflow.getTaskId()).getState()).isEqualTo(TaskState.COMPLETED);
        assertThat(events.history(workflow.getTaskId()))
                .anySatisfy(event -> assertThat(event.metadata())
                        .containsEntry("verificationDecision", "PASS"));
    }

    private TaskEntity preparedTask(String executorId, String reviewerId) {
        TaskEntity task = tasks.create(new CreateTaskRequest(
                "Phase 12 governed completion",
                "Exercise independent verification boundary",
                OperationMode.BALANCED));
        tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.PLANNING, "executive-planner", "Plan specialist task"));
        tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.RUNNING, executorId, "Specialist execution started"));
        tasks.recordProgress(task.getId(), executorId, "Specialist execution evidence recorded", Map.of("phase", "EXECUTION"));
        return tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.VERIFYING, reviewerId, "Execution handed to QA/review"));
    }
}
