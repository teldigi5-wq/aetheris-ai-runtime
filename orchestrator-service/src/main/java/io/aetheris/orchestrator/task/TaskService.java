package io.aetheris.orchestrator.task;

import io.aetheris.orchestrator.policy.OperationMode;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class TaskService {

    private static final Map<TaskState, Set<TaskState>> ALLOWED_TRANSITIONS = allowedTransitions();
    private final TaskRepository repository;
    private final TaskEventStreamService eventStream;
    private final TaskVerificationService verification;
    private final TaskDelegationService delegation;

    public TaskService(TaskRepository repository,
                       TaskEventStreamService eventStream,
                       TaskVerificationService verification,
                       TaskDelegationService delegation) {
        this.repository = repository;
        this.eventStream = eventStream;
        this.verification = verification;
        this.delegation = delegation;
    }

    @Transactional
    public TaskEntity create(CreateTaskRequest request) {
        OperationMode mode = request.mode() == null ? OperationMode.BALANCED : request.mode();
        TaskEntity task = repository.save(new TaskEntity(UUID.randomUUID(), request.title(), request.command(), mode));
        eventStream.publish(event(task, null, "Task queued", Map.of("mode", mode.name())));
        return task;
    }

    public List<TaskEntity> recent() { return repository.findTop50ByOrderByUpdatedAtDesc(); }
    public TaskEntity getRequired(UUID id) { return repository.findById(id).orElseThrow(() -> new NoSuchElementException("Unknown task: " + id)); }

    @Transactional
    public TaskEntity transition(UUID id, TaskTransitionRequest request) {
        TaskEntity task = getRequired(id); TaskState current = task.getState(); TaskState next = request.state();
        if (current == next) throw new IllegalStateException("Task is already in state " + current);
        Set<TaskState> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(next)) throw new IllegalStateException("Invalid task transition: " + current + " -> " + next);

        if ((current == TaskState.PLANNING || current == TaskState.AWAITING_APPROVAL)
                && next == TaskState.RUNNING
                && !sameAgent(task.getActiveAgentId(), request.agentId())) {
            delegation.recordExecutionAssignment(task, task.getActiveAgentId(), request.agentId());
        }
        if (current == TaskState.VERIFYING && next == TaskState.COMPLETED) {
            verification.assertCompletionAllowed(task.getId(), request.agentId());
        }

        task.transitionTo(next, request.agentId());
        TaskEntity saved = repository.save(task);
        String message = request.message() == null || request.message().isBlank() ? "Task moved to " + next : request.message();
        eventStream.publish(event(saved, request.agentId(), message, Map.of("previousState", current.name())));
        return saved;
    }

    @Transactional
    public TaskEntity activateDelegatedExecution(UUID id, String delegateId, String message) {
        TaskEntity task = getRequired(id);
        if (task.getState() != TaskState.RUNNING) {
            throw new IllegalStateException("Post-approval delegation requires a RUNNING task");
        }
        if (sameAgent(task.getActiveAgentId(), delegateId)) {
            throw new IllegalStateException("Task execution is already assigned to " + normalizeAgent(delegateId));
        }

        String delegatorId = task.getActiveAgentId();
        delegation.recordExecutionAssignment(task, delegatorId, delegateId);
        task.transitionTo(TaskState.RUNNING, delegateId);
        TaskEntity saved = repository.save(task);
        String normalizedMessage = message == null || message.isBlank()
                ? "Owner-approved task execution delegated to " + normalizeAgent(delegateId)
                : message;
        eventStream.publish(event(saved, delegateId, normalizedMessage, Map.of(
                "previousState", TaskState.RUNNING.name(),
                "delegatedFrom", normalizeAgent(delegatorId),
                "governedDelegation", true)));
        return saved;
    }

    public TaskEvent recordProgress(UUID id, String agentId, String message, Map<String, Object> metadata) {
        TaskEntity task = getRequired(id);
        TaskEvent progress = event(task, agentId, message == null || message.isBlank() ? "Task progress update" : message,
                metadata == null ? Map.of() : metadata);
        eventStream.publish(progress); return progress;
    }

    public TaskEvent snapshotEvent(UUID id) { TaskEntity task = getRequired(id); return event(task, task.getActiveAgentId(), "Current task snapshot", Map.of("mode", task.getMode().name())); }
    private TaskEvent event(TaskEntity task, String agentId, String message, Map<String, Object> metadata) { return new TaskEvent(task.getId(), Instant.now(), task.getState(), agentId, message, metadata); }

    private boolean sameAgent(String left, String right) {
        return Objects.equals(normalizeAgent(left), normalizeAgent(right));
    }

    private String normalizeAgent(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<TaskState, Set<TaskState>> allowedTransitions() {
        EnumMap<TaskState, Set<TaskState>> map = new EnumMap<>(TaskState.class);
        map.put(TaskState.QUEUED, Set.of(TaskState.PLANNING, TaskState.CANCELLED));
        map.put(TaskState.PLANNING, Set.of(TaskState.AWAITING_APPROVAL, TaskState.RUNNING, TaskState.FAILED, TaskState.CANCELLED));
        map.put(TaskState.AWAITING_APPROVAL, Set.of(TaskState.RUNNING, TaskState.FAILED, TaskState.CANCELLED));
        map.put(TaskState.RUNNING, Set.of(TaskState.AWAITING_APPROVAL, TaskState.PAUSED, TaskState.VERIFYING, TaskState.FAILED, TaskState.CANCELLED, TaskState.ROLLING_BACK));
        map.put(TaskState.PAUSED, Set.of(TaskState.RUNNING, TaskState.CANCELLED, TaskState.ROLLING_BACK));
        map.put(TaskState.VERIFYING, Set.of(TaskState.COMPLETED, TaskState.FAILED, TaskState.CANCELLED, TaskState.ROLLING_BACK));
        map.put(TaskState.FAILED, Set.of(TaskState.ROLLING_BACK));
        map.put(TaskState.ROLLING_BACK, Set.of(TaskState.FAILED, TaskState.CANCELLED));
        map.put(TaskState.COMPLETED, Set.of()); map.put(TaskState.CANCELLED, Set.of());
        return Map.copyOf(map);
    }
}
