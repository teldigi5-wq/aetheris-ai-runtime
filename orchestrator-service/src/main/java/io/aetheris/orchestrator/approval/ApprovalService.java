package io.aetheris.orchestrator.approval;

import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.task.TaskState;
import io.aetheris.orchestrator.task.TaskTransitionRequest;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ApprovalService {

    private final ApprovalRepository repository;
    private final TaskService tasks;

    public ApprovalService(ApprovalRepository repository, TaskService tasks) { this.repository = repository; this.tasks = tasks; }

    @Transactional
    public ApprovalEntity request(CreateApprovalRequest request) {
        TaskEntity task = tasks.getRequired(request.taskId());
        if (task.getState() == TaskState.PLANNING || task.getState() == TaskState.RUNNING) {
            task = tasks.transition(task.getId(), new TaskTransitionRequest(
                    TaskState.AWAITING_APPROVAL, task.getActiveAgentId(), "Task paused for owner approval"));
        }
        if (task.getState() != TaskState.AWAITING_APPROVAL) {
            throw new IllegalStateException("Approvals can only be requested while a task is PLANNING, RUNNING or AWAITING_APPROVAL");
        }
        return repository.save(new ApprovalEntity(UUID.randomUUID(), request.taskId(), request.actionType(), request.summary(), request.riskLevel()));
    }

    public List<ApprovalEntity> pending() { return repository.findTop100ByStatusOrderByCreatedAtDesc(ApprovalStatus.PENDING); }
    public List<ApprovalEntity> forTask(UUID taskId) { tasks.getRequired(taskId); return repository.findTop100ByTaskIdOrderByCreatedAtDesc(taskId); }
    public boolean hasApproved(UUID taskId, String actionType) { return repository.existsByTaskIdAndActionTypeAndStatus(taskId, actionType, ApprovalStatus.APPROVED); }
    public ApprovalEntity getRequired(UUID id) { return repository.findById(id).orElseThrow(() -> new NoSuchElementException("Unknown approval: " + id)); }

    @Transactional
    public ApprovalEntity decide(UUID id, ApprovalDecisionRequest request) {
        ApprovalEntity approval = getRequired(id); approval.decide(request.approved(), request.note()); ApprovalEntity saved = repository.save(approval);
        TaskEntity task = tasks.getRequired(saved.getTaskId());
        if (!request.approved() && task.getState() == TaskState.AWAITING_APPROVAL) {
            tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.CANCELLED, task.getActiveAgentId(), "Owner rejected required approval: " + saved.getActionType()));
        } else if (request.approved() && task.getState() == TaskState.AWAITING_APPROVAL
                && repository.countByTaskIdAndStatus(task.getId(), ApprovalStatus.PENDING) == 0) {
            tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.RUNNING, task.getActiveAgentId(), "All required owner approvals granted; task resumed"));
        }
        return saved;
    }
}
