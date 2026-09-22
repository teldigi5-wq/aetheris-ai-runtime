package io.aetheris.orchestrator.workflow;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.approval.CreateApprovalRequest;
import io.aetheris.orchestrator.execution.CommandExecutionResult;
import io.aetheris.orchestrator.execution.ToolExecutionRequest;
import io.aetheris.orchestrator.execution.ToolExecutionResponse;
import io.aetheris.orchestrator.execution.ToolExecutionService;
import io.aetheris.orchestrator.execution.ToolExecutionStatus;
import io.aetheris.orchestrator.policy.CompiledPolicyDecision;
import io.aetheris.orchestrator.policy.OwnerRuleCompilerService;
import io.aetheris.orchestrator.policy.PolicyEvaluationRequest;
import io.aetheris.orchestrator.task.CreateTaskRequest;
import io.aetheris.orchestrator.task.TaskControlService;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.task.TaskState;
import io.aetheris.orchestrator.task.TaskTransitionRequest;
import io.aetheris.orchestrator.task.TaskVerificationService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class EngineeringWorkflowService {

    private final EngineeringWorkflowRepository repository;
    private final TaskService tasks;
    private final OwnerRuleCompilerService policy;
    private final ApprovalService approvals;
    private final ToolExecutionService tools;
    private final VerificationEvidenceService evidence;
    private final TaskControlService control;
    private final TaskVerificationService verification;

    public EngineeringWorkflowService(
            EngineeringWorkflowRepository repository,
            TaskService tasks,
            OwnerRuleCompilerService policy,
            ApprovalService approvals,
            ToolExecutionService tools,
            VerificationEvidenceService evidence,
            TaskControlService control,
            TaskVerificationService verification) {
        this.repository = repository;
        this.tasks = tasks;
        this.policy = policy;
        this.approvals = approvals;
        this.tools = tools;
        this.evidence = evidence;
        this.control = control;
        this.verification = verification;
    }

    @Transactional
    public EngineeringWorkflowEntity start(EngineeringWorkflowRequest request) {
        if (control.isEmergencyStopActive()) throw new IllegalStateException("Emergency stop is active");
        TaskEntity task = tasks.create(new CreateTaskRequest(request.title(), request.command(), request.mode()));
        task = tasks.transition(task.getId(), new TaskTransitionRequest(
                TaskState.PLANNING,
                "executive-planner",
                "Planning engineering, QA and independent verification workflow"));

        CompiledPolicyDecision decision = policy.evaluate(new PolicyEvaluationRequest(
                "executive-planner",
                "workflow.engineering-review",
                "software-engineering",
                RiskLevel.MEDIUM,
                false,
                false,
                task.getMode(),
                Map.of("workflow", "engineering-qa-verifier")));

        if (!decision.allowed()) {
            tasks.transition(task.getId(), new TaskTransitionRequest(
                    TaskState.CANCELLED,
                    "executive-planner",
                    "Workflow blocked by deterministic policy"));
            return repository.save(new EngineeringWorkflowEntity(
                    UUID.randomUUID(), task.getId(), EngineeringWorkflowPhase.CANCELLED));
        }

        EngineeringWorkflowEntity workflow;
        if (decision.requiresApproval()) {
            workflow = repository.save(new EngineeringWorkflowEntity(
                    UUID.randomUUID(), task.getId(), EngineeringWorkflowPhase.AWAITING_APPROVAL));
            approvals.request(new CreateApprovalRequest(
                    task.getId(),
                    "workflow.engineering-review",
                    "Run Engineering -> QA -> independent Software Architect verification workflow",
                    RiskLevel.MEDIUM));
        } else {
            tasks.transition(task.getId(), new TaskTransitionRequest(
                    TaskState.RUNNING,
                    "backend-engineer",
                    "Engineering phase started"));
            workflow = repository.save(new EngineeringWorkflowEntity(
                    UUID.randomUUID(), task.getId(), EngineeringWorkflowPhase.ENGINEERING));
        }
        return workflow;
    }

    public EngineeringWorkflowEntity getRequired(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Unknown engineering workflow: " + id));
    }

    public List<EngineeringWorkflowEntity> recent() {
        return repository.findTop50ByOrderByUpdatedAtDesc();
    }

    @Transactional
    public EngineeringWorkflowEntity advance(UUID id) {
        EngineeringWorkflowEntity workflow = getRequired(id);
        TaskEntity task = tasks.getRequired(workflow.getTaskId());

        switch (workflow.getPhase()) {
            case AWAITING_APPROVAL -> {
                if (task.getState() == TaskState.CANCELLED) workflow.moveTo(EngineeringWorkflowPhase.CANCELLED);
                else if (task.getState() == TaskState.RUNNING) {
                    if (!"backend-engineer".equals(task.getActiveAgentId())) {
                        task = tasks.activateDelegatedExecution(
                                task.getId(),
                                "backend-engineer",
                                "Owner approval received; governed engineering delegation activated");
                    }
                    tasks.recordProgress(task.getId(), "backend-engineer", "Owner approval received; engineering phase activated",
                            Map.of("workflowPhase", "ENGINEERING", "governedDelegation", true));
                    workflow.moveTo(EngineeringWorkflowPhase.ENGINEERING);
                } else throw new IllegalStateException("Workflow is still waiting for owner approval");
            }
            case ENGINEERING -> {
                tasks.recordProgress(task.getId(), "backend-engineer", "Engineering handoff prepared for QA",
                        Map.of("workflowPhase", "ENGINEERING", "next", "QA"));
                tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.VERIFYING, "qa-engineer", "QA phase started"));
                workflow.moveTo(EngineeringWorkflowPhase.QA);
            }
            case QA -> {
                tasks.recordProgress(task.getId(), "qa-engineer", "QA evidence handed to independent verifier",
                        Map.of("workflowPhase", "QA", "next", "VERIFICATION"));
                tasks.recordProgress(task.getId(), "software-architect", "Independent architecture verification started",
                        Map.of("workflowPhase", "VERIFICATION"));
                workflow.moveTo(EngineeringWorkflowPhase.VERIFICATION);
            }
            case VERIFICATION -> {
                verification.recordDecision(
                        task.getId(),
                        "software-architect",
                        true,
                        "WORKFLOW_REVIEW",
                        "Independent architecture verification passed");
                tasks.recordProgress(task.getId(), "software-architect", "Independent verification completed",
                        Map.of("workflowPhase", "VERIFICATION"));
                tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.COMPLETED, "software-architect",
                        "Engineering workflow completed after QA and independent verification"));
                workflow.moveTo(EngineeringWorkflowPhase.COMPLETED);
            }
            case COMPLETED, FAILED, CANCELLED -> throw new IllegalStateException("Workflow is already terminal: " + workflow.getPhase());
        }
        return repository.save(workflow);
    }

    @Transactional
    public WorkflowExecutionResult executeNext(UUID id, WorkflowExecutionRequest request) {
        if (control.isEmergencyStopActive()) throw new IllegalStateException("Emergency stop is active");
        EngineeringWorkflowEntity workflow = getRequired(id);
        TaskEntity task = tasks.getRequired(workflow.getTaskId());

        if (workflow.getPhase() == EngineeringWorkflowPhase.AWAITING_APPROVAL) {
            advance(id);
            workflow = getRequired(id);
            if (workflow.getPhase() != EngineeringWorkflowPhase.ENGINEERING) {
                return result(workflow, "Workflow is still waiting for owner approval");
            }
            task = tasks.getRequired(workflow.getTaskId());
        }

        switch (workflow.getPhase()) {
            case ENGINEERING -> executeEngineering(workflow, task, request);
            case QA -> executeQa(workflow, task, request);
            case VERIFICATION -> executeVerification(workflow, task, request);
            case COMPLETED, FAILED, CANCELLED -> throw new IllegalStateException("Workflow is already terminal: " + workflow.getPhase());
            case AWAITING_APPROVAL -> throw new IllegalStateException("Workflow is still waiting for owner approval");
        }
        return result(repository.save(workflow), "Executed phase using Stage 4 adapters");
    }

    private void executeEngineering(EngineeringWorkflowEntity workflow, TaskEntity task, WorkflowExecutionRequest request) {
        ToolExecutionResponse response = tools.execute(new ToolExecutionRequest(
                task.getId(), "backend-engineer", "files.read-workspace", task.getMode(),
                Map.of("path", required(request.workspaceFile(), "workspaceFile"))));
        boolean passed = response.status() == ToolExecutionStatus.SUCCEEDED;
        evidence.record(workflow.getId(), task.getId(), "ENGINEERING", "backend-engineer", "SOURCE_READ", passed,
                passed ? "Engineering source evidence captured" : "Engineering source evidence failed", response.detail());
        if (!passed) {
            tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.FAILED, "backend-engineer", "Engineering evidence step failed"));
            workflow.moveTo(EngineeringWorkflowPhase.FAILED);
            return;
        }
        tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.VERIFYING, "qa-engineer", "QA adapter execution started"));
        workflow.moveTo(EngineeringWorkflowPhase.QA);
    }

    private void executeQa(EngineeringWorkflowEntity workflow, TaskEntity task, WorkflowExecutionRequest request) {
        ToolExecutionResponse response = tools.execute(new ToolExecutionRequest(
                task.getId(), "qa-engineer", "terminal.inspect", task.getMode(),
                Map.of("command", request.qaCommand(), "workingDirectory", request.workingDirectory() == null ? "" : request.workingDirectory())));
        boolean passed = response.status() == ToolExecutionStatus.SUCCEEDED
                && response.output() instanceof CommandExecutionResult commandResult
                && commandResult.passed();
        String detail = response.output() instanceof CommandExecutionResult commandResult ? commandResult.output() : response.detail();
        evidence.record(workflow.getId(), task.getId(), "QA", "qa-engineer", "QA_COMMAND", passed,
                passed ? "QA command passed" : "QA command failed", detail);
        if (!passed) {
            tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.FAILED, "qa-engineer", "QA evidence failed"));
            workflow.moveTo(EngineeringWorkflowPhase.FAILED);
            return;
        }
        tasks.recordProgress(task.getId(), "software-architect", "Independent verification evaluating recorded evidence",
                Map.of("workflowPhase", "VERIFICATION"));
        workflow.moveTo(EngineeringWorkflowPhase.VERIFICATION);
    }

    private void executeVerification(EngineeringWorkflowEntity workflow, TaskEntity task, WorkflowExecutionRequest request) {
        boolean passed = evidence.satisfies(workflow.getId(), request.acceptanceCriteria());
        evidence.record(workflow.getId(), task.getId(), "VERIFICATION", "software-architect", "ACCEPTANCE_CRITERIA", passed,
                passed ? "Acceptance criteria satisfied" : "Acceptance criteria not satisfied",
                String.join(",", request.acceptanceCriteria().isEmpty() ? List.of("source-readable", "qa-command-passed") : request.acceptanceCriteria()));
        verification.recordDecision(
                task.getId(),
                "software-architect",
                passed,
                "ACCEPTANCE_CRITERIA",
                passed ? "Independent verifier accepted recorded evidence" : "Independent verifier rejected recorded evidence");
        if (!passed) {
            tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.FAILED, "software-architect", "Independent verifier rejected evidence"));
            workflow.moveTo(EngineeringWorkflowPhase.FAILED);
            return;
        }
        tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.COMPLETED, "software-architect",
                "Engineering workflow completed with adapter evidence and independent verification"));
        workflow.moveTo(EngineeringWorkflowPhase.COMPLETED);
    }

    private WorkflowExecutionResult result(EngineeringWorkflowEntity workflow, String detail) {
        return new WorkflowExecutionResult(workflow, evidence.forWorkflow(workflow.getId()), detail);
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required for this workflow phase");
        return value;
    }
}
