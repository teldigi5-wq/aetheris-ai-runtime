package io.aetheris.orchestrator.runtime;

import io.aetheris.orchestrator.scheduler.WorkerIdentityBindingService;
import io.aetheris.orchestrator.task.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class DurableWorkQueueService {
    private final WorkItemRepository repository;
    private final TaskService tasks;
    private final TaskControlService control;
    private final WorkerIdentityBindingService workerIdentities;

    public DurableWorkQueueService(WorkItemRepository repository,
                                   TaskService tasks,
                                   TaskControlService control,
                                   WorkerIdentityBindingService workerIdentities) {
        this.repository=repository;
        this.tasks=tasks;
        this.control=control;
        this.workerIdentities=workerIdentities;
    }

    @Transactional
    public WorkItemEntity enqueue(EnqueueWorkItemRequest request){
        if(control.isEmergencyStopActive())throw new IllegalStateException("Emergency stop is active");
        TaskEntity task=tasks.getRequired(request.taskId());
        String requiredAgentId=workerIdentities.knownAgentOrNull(task.getActiveAgentId());
        return enqueueBound(request,requiredAgentId);
    }

    @Transactional
    public WorkItemEntity enqueueForSpecialist(EnqueueWorkItemRequest request,String requiredAgentId){
        if(control.isEmergencyStopActive())throw new IllegalStateException("Emergency stop is active");
        tasks.getRequired(request.taskId());
        return enqueueBound(request,workerIdentities.requireKnownAgent(requiredAgentId));
    }

    private WorkItemEntity enqueueBound(EnqueueWorkItemRequest request,String requiredAgentId){
        String payload=request.payloadJson()==null||request.payloadJson().isBlank()?"{}":request.payloadJson();
        return repository.save(new WorkItemEntity(
                UUID.randomUUID(),
                request.taskId(),
                request.workflowType().trim(),
                payload,
                request.maxAttempts()<=0?3:Math.min(request.maxAttempts(),10),
                requiredAgentId));
    }

    public List<WorkItemEntity> recent(){return repository.findTop100ByOrderByUpdatedAtDesc();}
    public List<WorkItemEntity> forTask(UUID taskId){tasks.getRequired(taskId);return repository.findTop100ByTaskIdOrderByCreatedAtDesc(taskId);}
    public WorkItemEntity getRequired(UUID id){return repository.findById(id).orElseThrow(()->new NoSuchElementException("Unknown work item: "+id));}
    public List<WorkItemEntity> ready(){Instant now=Instant.now();return repository.findTop100ByStateInOrderByCreatedAtAsc(Set.of(WorkItemState.QUEUED,WorkItemState.RETRY_WAIT)).stream().filter(i->i.getNextAttemptAt()==null||!i.getNextAttemptAt().isAfter(now)).toList();}

    public boolean canWorkerExecute(UUID id,String workerId){return workerIdentities.canExecute(getRequired(id),workerId);}

    @Transactional public WorkItemEntity claim(UUID id){return claim(id,"api-worker",60);}
    @Transactional public WorkItemEntity claim(UUID id,String workerId,int leaseSeconds){
        if(control.isEmergencyStopActive())throw new IllegalStateException("Emergency stop is active");
        WorkItemEntity item=getRequired(id);
        workerIdentities.assertCanExecute(item,workerId);
        item.claim(workerId,leaseSeconds);
        return repository.save(item);
    }
    @Transactional public WorkItemEntity renewLease(UUID id,String workerId,int leaseSeconds){WorkItemEntity item=getRequired(id);workerIdentities.assertCanExecute(item,workerId);item.renewLease(workerId,leaseSeconds);return repository.save(item);}
    @Transactional public int recoverAbandoned(){int count=0;Instant now=Instant.now();for(WorkItemEntity item:repository.findTop100ByStateInOrderByCreatedAtAsc(Set.of(WorkItemState.RUNNING))){if(item.leaseExpired(now)){item.recoverExpiredLease(now);repository.save(item);count++;}}return count;}
    @Transactional public WorkItemEntity succeed(UUID id){WorkItemEntity item=getRequired(id);item.succeed();return repository.save(item);}
    @Transactional public WorkItemEntity fail(UUID id,String detail){WorkItemEntity item=getRequired(id);long delay=Math.min(300L,5L*(1L<<Math.min(Math.max(item.getAttempt()-1,0),6)));Instant retry=item.getAttempt()<item.getMaxAttempts()?Instant.now().plusSeconds(delay):null;item.fail(detail==null||detail.isBlank()?"Work item failed":detail.trim(),retry);return repository.save(item);}
    @Transactional public WorkItemEntity pause(UUID id){WorkItemEntity item=getRequired(id);item.pause();return repository.save(item);}
    @Transactional public WorkItemEntity resume(UUID id){WorkItemEntity item=getRequired(id);item.resume();return repository.save(item);}
    @Transactional public WorkItemEntity cancel(UUID id){WorkItemEntity item=getRequired(id);item.cancel();return repository.save(item);}
    @Transactional public int cancelActiveForTask(UUID taskId){int count=0;for(WorkItemEntity item:repository.findTop100ByTaskIdOrderByCreatedAtDesc(taskId)){if(item.getState()!=WorkItemState.SUCCEEDED&&item.getState()!=WorkItemState.FAILED&&item.getState()!=WorkItemState.CANCELLED){item.cancel();repository.save(item);count++;}}return count;}
}
