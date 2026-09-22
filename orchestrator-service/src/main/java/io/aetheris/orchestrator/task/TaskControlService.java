package io.aetheris.orchestrator.task;

import io.aetheris.orchestrator.runtime.WorkItemEntity;
import io.aetheris.orchestrator.runtime.WorkItemRepository;
import io.aetheris.orchestrator.runtime.WorkItemState;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TaskControlService {

    private static final Set<TaskState> ACTIVE_STATES=EnumSet.of(TaskState.QUEUED,TaskState.PLANNING,TaskState.AWAITING_APPROVAL,TaskState.RUNNING,TaskState.PAUSED,TaskState.VERIFYING,TaskState.ROLLING_BACK);
    private static final Set<WorkItemState> ACTIVE_WORK_STATES=EnumSet.of(WorkItemState.QUEUED,WorkItemState.RUNNING,WorkItemState.RETRY_WAIT,WorkItemState.PAUSED);
    private final TaskRepository repository; private final TaskService tasks; private final WorkItemRepository workItems;
    private final AtomicBoolean emergencyStop=new AtomicBoolean(false);
    private volatile Instant changedAt=Instant.now(); private volatile String reason="Not engaged"; private volatile int cancelledTasks=0;

    public TaskControlService(TaskRepository repository,TaskService tasks,WorkItemRepository workItems){this.repository=repository;this.tasks=tasks;this.workItems=workItems;}
    public boolean isEmergencyStopActive(){return emergencyStop.get();}
    public EmergencyStopStatus status(){return new EmergencyStopStatus(emergencyStop.get(),changedAt,reason,cancelledTasks);}

    @Transactional
    public EmergencyStopStatus engage(String requestedReason){
        emergencyStop.set(true);changedAt=Instant.now();reason=requestedReason==null||requestedReason.isBlank()?"Owner emergency stop":requestedReason.trim();int cancelled=0;
        for(TaskEntity task:repository.findByStateIn(ACTIVE_STATES)){try{tasks.transition(task.getId(),new TaskTransitionRequest(TaskState.CANCELLED,"emergency-stop","Emergency stop: "+reason));cancelled++;}catch(IllegalStateException ignored){}}
        for(WorkItemEntity item:workItems.findTop100ByStateInOrderByCreatedAtAsc(ACTIVE_WORK_STATES)){item.cancel();workItems.save(item);}
        cancelledTasks=cancelled;return status();
    }
    public EmergencyStopStatus release(String requestedReason){emergencyStop.set(false);changedAt=Instant.now();reason=requestedReason==null||requestedReason.isBlank()?"Owner released emergency stop":requestedReason.trim();cancelledTasks=0;return status();}

    @Transactional
    public TaskEntity cancelTask(UUID taskId,String requestedReason){TaskEntity task=tasks.getRequired(taskId);cancelWorkItems(taskId);if(task.getState()==TaskState.COMPLETED||task.getState()==TaskState.CANCELLED||task.getState()==TaskState.FAILED)return task;
        String message=requestedReason==null||requestedReason.isBlank()?"Task cancelled by owner":"Task cancelled by owner: "+requestedReason.trim();return tasks.transition(taskId,new TaskTransitionRequest(TaskState.CANCELLED,"owner-control",message));}

    @Transactional
    public TaskEntity pauseTask(UUID taskId,String requestedReason){TaskEntity task=tasks.getRequired(taskId);if(task.getState()!=TaskState.RUNNING)throw new IllegalStateException("Only RUNNING tasks can be paused");
        pauseWorkItems(taskId);String message=requestedReason==null||requestedReason.isBlank()?"Task paused by owner":"Task paused by owner: "+requestedReason.trim();return tasks.transition(taskId,new TaskTransitionRequest(TaskState.PAUSED,"owner-control",message));}

    @Transactional
    public TaskEntity resumeTask(UUID taskId,String requestedReason){if(emergencyStop.get())throw new IllegalStateException("Emergency stop is active");TaskEntity task=tasks.getRequired(taskId);if(task.getState()!=TaskState.PAUSED)throw new IllegalStateException("Only PAUSED tasks can be resumed");
        resumeWorkItems(taskId);String message=requestedReason==null||requestedReason.isBlank()?"Task resumed by owner":"Task resumed by owner: "+requestedReason.trim();return tasks.transition(taskId,new TaskTransitionRequest(TaskState.RUNNING,"owner-control",message));}

    @Transactional
    public TaskEntity takeControl(UUID taskId,String requestedReason){TaskEntity paused=pauseTask(taskId,requestedReason==null?"Owner took control":requestedReason);tasks.recordProgress(taskId,"owner-control","Owner take-control active", Map.of("ownerControl",true));return paused;}

    private void cancelWorkItems(UUID taskId){for(WorkItemEntity item:workItems.findTop100ByTaskIdOrderByCreatedAtDesc(taskId)){if(ACTIVE_WORK_STATES.contains(item.getState())){item.cancel();workItems.save(item);}}}
    private void pauseWorkItems(UUID taskId){for(WorkItemEntity item:workItems.findTop100ByTaskIdOrderByCreatedAtDesc(taskId)){if(item.getState()==WorkItemState.RUNNING||item.getState()==WorkItemState.QUEUED||item.getState()==WorkItemState.RETRY_WAIT){item.pause();workItems.save(item);}}}
    private void resumeWorkItems(UUID taskId){for(WorkItemEntity item:workItems.findTop100ByTaskIdOrderByCreatedAtDesc(taskId)){if(item.getState()==WorkItemState.PAUSED){item.resume();workItems.save(item);}}}
}
