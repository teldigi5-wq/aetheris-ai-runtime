package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.scheduler.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/orchestrator/scheduler")
public class SchedulerController {
    private final SchedulerService scheduler; public SchedulerController(SchedulerService scheduler){this.scheduler=scheduler;}
    @PostMapping("/workers/heartbeat") public WorkerHeartbeatEntity heartbeat(@Valid @RequestBody WorkerHeartbeatRequest r){return scheduler.heartbeat(r);}
    @GetMapping("/workers") public List<WorkerHeartbeatEntity> workers(){return scheduler.workers();}
    @PostMapping("/tickets") public SchedulerTicketEntity schedule(@Valid @RequestBody ScheduleWorkRequest r){return scheduler.schedule(r);}
    @GetMapping("/tickets/queued") public List<SchedulerTicketEntity> queued(){return scheduler.queued();}
    @PostMapping("/workers/{workerId}/dispatch") public SchedulerDispatchResult dispatch(@PathVariable String workerId,@RequestParam(defaultValue="60") int leaseSeconds){return scheduler.dispatch(workerId,leaseSeconds);}
    @PostMapping("/workers/{workerId}/release") public WorkerHeartbeatEntity release(@PathVariable String workerId){return scheduler.releaseLease(workerId);}
    @PostMapping("/recover-stale") public Map<String,Integer> recover(){return Map.of("workersRecovered",scheduler.recoverStaleWorkers());}
}
