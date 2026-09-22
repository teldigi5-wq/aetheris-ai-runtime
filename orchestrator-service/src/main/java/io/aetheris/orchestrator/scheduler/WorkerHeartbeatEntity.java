package io.aetheris.orchestrator.scheduler;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="aetheris_worker_heartbeats")
public class WorkerHeartbeatEntity {
    @Id @Column(length=160) private String workerId;
    @Column(length=120) private String agentId;
    @Column(nullable=false) private int maxConcurrency;
    @Column(nullable=false) private int activeLeases;
    @Column(nullable=false) private Instant lastHeartbeatAt;
    @Column(nullable=false) private Instant updatedAt;

    protected WorkerHeartbeatEntity(){}

    public WorkerHeartbeatEntity(String workerId,int maxConcurrency,int activeLeases){
        this(workerId,null,maxConcurrency,activeLeases);
    }

    public WorkerHeartbeatEntity(String workerId,String agentId,int maxConcurrency,int activeLeases){
        this.workerId=workerId;
        bindAgent(agentId);
        heartbeat(maxConcurrency,activeLeases);
    }

    public String getWorkerId(){return workerId;}
    public String getAgentId(){return agentId;}
    public int getMaxConcurrency(){return maxConcurrency;}
    public int getActiveLeases(){return activeLeases;}
    public Instant getLastHeartbeatAt(){return lastHeartbeatAt;}
    public Instant getUpdatedAt(){return updatedAt;}

    public void bindAgent(String requestedAgentId){
        if(requestedAgentId==null||requestedAgentId.isBlank())return;
        String normalized=requestedAgentId.trim();
        if(agentId!=null&&!agentId.equals(normalized))throw new IllegalStateException("Worker identity is already bound to specialist "+agentId);
        agentId=normalized;
    }

    public void heartbeat(int maxConcurrency,int activeLeases){
        this.maxConcurrency=Math.max(1,Math.min(maxConcurrency,64));
        this.activeLeases=Math.max(0,Math.min(activeLeases,this.maxConcurrency));
        this.lastHeartbeatAt=Instant.now();
        this.updatedAt=this.lastHeartbeatAt;
    }

    public boolean online(Instant cutoff){return !lastHeartbeatAt.isBefore(cutoff);}
    public void leaseClaimed(){activeLeases=Math.min(maxConcurrency,activeLeases+1);updatedAt=Instant.now();}
    public void leaseReleased(){activeLeases=Math.max(0,activeLeases-1);updatedAt=Instant.now();}
    public void clearLeases(){activeLeases=0;updatedAt=Instant.now();}
}
