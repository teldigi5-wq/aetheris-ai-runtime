package io.aetheris.orchestrator.dispatcher;

import io.aetheris.orchestrator.scheduler.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class AutonomousDispatcherService {
    private final SchedulerService scheduler; private final boolean enabled; private final int leaseSeconds; private volatile DispatcherTickResult last=new DispatcherTickResult(0,0,List.of("not run"));
    public AutonomousDispatcherService(SchedulerService scheduler,@Value("${aetheris.dispatcher.enabled:true}") boolean enabled,@Value("${aetheris.dispatcher.lease-seconds:60}") int leaseSeconds){this.scheduler=scheduler;this.enabled=enabled;this.leaseSeconds=Math.max(10,Math.min(leaseSeconds,900));}
    @Scheduled(fixedDelayString="${aetheris.dispatcher.poll-ms:2000}") public void scheduledTick(){if(enabled)last=tick();}
    public DispatcherTickResult tick(){scheduler.recoverStaleWorkers();List<WorkerHeartbeatEntity> workers=new ArrayList<>(scheduler.workers());workers.sort(Comparator.comparingInt(w->w.getActiveLeases()*100/Math.max(1,w.getMaxConcurrency())));int dispatched=0;List<String> details=new ArrayList<>();for(WorkerHeartbeatEntity worker:workers){int free=Math.max(0,worker.getMaxConcurrency()-worker.getActiveLeases());for(int i=0;i<free;i++){SchedulerDispatchResult result=scheduler.dispatch(worker.getWorkerId(),leaseSeconds);details.add(worker.getWorkerId()+": "+result.reason());if(!result.dispatched())break;dispatched++;}}last=new DispatcherTickResult(workers.size(),dispatched,List.copyOf(details));return last;}
    public DispatcherTickResult last(){return last;}
}
