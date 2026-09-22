package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.runtime.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/orchestrator/work-queue")
public class WorkQueueController {
    private final DurableWorkQueueService queue; public WorkQueueController(DurableWorkQueueService queue){this.queue=queue;}
    @PostMapping public WorkItemEntity enqueue(@Valid @RequestBody EnqueueWorkItemRequest request){return queue.enqueue(request);}
    @GetMapping public List<WorkItemEntity> recent(){return queue.recent();}
    @GetMapping("/ready") public List<WorkItemEntity> ready(){return queue.ready();}
    @GetMapping("/task/{taskId}") public List<WorkItemEntity> forTask(@PathVariable UUID taskId){return queue.forTask(taskId);}
    @PostMapping("/{id}/claim") public WorkItemEntity claim(@PathVariable UUID id,@RequestBody(required=false) ClaimWorkItemRequest request){return request==null?queue.claim(id):queue.claim(id,request.workerId(),request.leaseSeconds()==null?60:request.leaseSeconds());}
    @PostMapping("/{id}/renew-lease") public WorkItemEntity renew(@PathVariable UUID id,@RequestBody ClaimWorkItemRequest request){return queue.renewLease(id,request.workerId(),request.leaseSeconds()==null?60:request.leaseSeconds());}
    @PostMapping("/recover-abandoned") public Map<String,Integer> recover(){return Map.of("recovered",queue.recoverAbandoned());}
    @PostMapping("/{id}/succeed") public WorkItemEntity succeed(@PathVariable UUID id){return queue.succeed(id);}
    @PostMapping("/{id}/fail") public WorkItemEntity fail(@PathVariable UUID id,@RequestBody(required=false) WorkItemFailureRequest request){return queue.fail(id,request==null?null:request.detail());}
    @PostMapping("/{id}/pause") public WorkItemEntity pause(@PathVariable UUID id){return queue.pause(id);}
    @PostMapping("/{id}/resume") public WorkItemEntity resume(@PathVariable UUID id){return queue.resume(id);}
    @PostMapping("/{id}/cancel") public WorkItemEntity cancel(@PathVariable UUID id){return queue.cancel(id);}
}
