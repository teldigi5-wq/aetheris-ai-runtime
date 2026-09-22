package io.aetheris.orchestrator.scheduler;

import io.aetheris.orchestrator.runtime.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;
import java.time.*;
import java.util.*;

@Service
public class SchedulerService {
    private final SchedulerTicketRepository tickets;
    private final WorkerHeartbeatRepository workers;
    private final DurableWorkQueueService queue;
    private final WorkerIdentityBindingService workerIdentities;
    private final int maxQueued;
    private final Duration heartbeatTimeout;

    public SchedulerService(SchedulerTicketRepository tickets,
                            WorkerHeartbeatRepository workers,
                            DurableWorkQueueService queue,
                            WorkerIdentityBindingService workerIdentities,
                            @Value("${aetheris.scheduler.max-queued:500}") int maxQueued,
                            @Value("${aetheris.scheduler.worker-timeout-seconds:45}") long timeoutSeconds){
        this.tickets=tickets;
        this.workers=workers;
        this.queue=queue;
        this.workerIdentities=workerIdentities;
        this.maxQueued=Math.max(10,maxQueued);
        this.heartbeatTimeout=Duration.ofSeconds(Math.max(10,timeoutSeconds));
    }

    @Transactional
    public WorkerHeartbeatEntity heartbeat(WorkerHeartbeatRequest r){
        String id=r.workerId().trim();
        String requestedAgentId=r.agentId()==null||r.agentId().isBlank()?null:workerIdentities.requireKnownAgent(r.agentId());
        WorkerHeartbeatEntity w=workers.findById(id)
                .orElseGet(()->new WorkerHeartbeatEntity(id,requestedAgentId,r.maxConcurrency(),r.activeLeases()));
        workerIdentities.bind(w,requestedAgentId);
        w.heartbeat(r.maxConcurrency(),r.activeLeases());
        return workers.save(w);
    }

    @Transactional public SchedulerTicketEntity schedule(ScheduleWorkRequest r){queue.getRequired(r.workItemId());Optional<SchedulerTicketEntity> existing=tickets.findByWorkItemId(r.workItemId());if(existing.isPresent())return existing.get();if(tickets.countByState(SchedulerTicketState.QUEUED)>=maxQueued)throw new IllegalStateException("Scheduler backpressure limit reached");return tickets.save(new SchedulerTicketEntity(UUID.randomUUID(),r.workItemId(),r.priority(),r.lane()));}
    public List<SchedulerTicketEntity> queued(){return tickets.findTop200ByStateOrderByPriorityDescCreatedAtAsc(SchedulerTicketState.QUEUED);}
    public List<WorkerHeartbeatEntity> workers(){return workers.findTop100ByOrderByUpdatedAtDesc();}

    @Transactional
    public SchedulerDispatchResult dispatch(String workerId,int leaseSeconds){
        WorkerHeartbeatEntity worker=workers.findById(workerId).orElseThrow(()->new NoSuchElementException("Unknown worker: "+workerId));
        Instant cutoff=Instant.now().minus(heartbeatTimeout);
        if(!worker.online(cutoff))return new SchedulerDispatchResult(workerId,false,"Worker heartbeat is stale",null,null);
        if(worker.getActiveLeases()>=worker.getMaxConcurrency())return new SchedulerDispatchResult(workerId,false,"Worker concurrency limit reached",null,null);
        Set<UUID> readyIds=new HashSet<>();
        for(WorkItemEntity i:queue.ready())readyIds.add(i.getId());
        boolean identityMismatch=false;
        for(SchedulerTicketEntity ticket:tickets.findTop200ByStateOrderByPriorityDescCreatedAtAsc(SchedulerTicketState.QUEUED)){
            if(!readyIds.contains(ticket.getWorkItemId()))continue;
            if(!queue.canWorkerExecute(ticket.getWorkItemId(),workerId)){identityMismatch=true;continue;}
            WorkItemEntity item=queue.claim(ticket.getWorkItemId(),workerId,Math.max(5,Math.min(leaseSeconds,900)));
            ticket.dispatched();
            worker.leaseClaimed();
            tickets.save(ticket);
            workers.save(worker);
            return new SchedulerDispatchResult(workerId,true,"Highest-priority ready work dispatched",ticket,item);
        }
        return new SchedulerDispatchResult(workerId,false,identityMismatch?"No ready scheduled work matches worker specialist identity":"No ready scheduled work",null,null);
    }

    @Transactional public WorkerHeartbeatEntity releaseLease(String workerId){WorkerHeartbeatEntity worker=workers.findById(workerId).orElseThrow(()->new NoSuchElementException("Unknown worker: "+workerId));worker.leaseReleased();return workers.save(worker);}
    @Transactional public int recoverStaleWorkers(){int count=0;Instant cutoff=Instant.now().minus(heartbeatTimeout);for(WorkerHeartbeatEntity worker:workers.findTop100ByOrderByUpdatedAtDesc()){if(!worker.online(cutoff)&&worker.getActiveLeases()>0){worker.clearLeases();workers.save(worker);count++;}}queue.recoverAbandoned();return count;}
}
