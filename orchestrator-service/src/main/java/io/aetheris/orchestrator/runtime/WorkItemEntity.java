package io.aetheris.orchestrator.runtime;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aetheris_work_items")
public class WorkItemEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID taskId;
    @Column(nullable = false, length = 120) private String workflowType;
    @Column(nullable = false, length = 16000) private String payloadJson;
    @Column(length = 120) private String requiredAgentId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private WorkItemState state;
    @Column(nullable = false) private int attempt;
    @Column(nullable = false) private int maxAttempts;
    private Instant nextAttemptAt;
    private Instant lockedAt;
    @Column(length=160) private String leaseOwner;
    private Instant leaseExpiresAt;
    @Column(length = 4000) private String lastError;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected WorkItemEntity() {}

    public WorkItemEntity(UUID id, UUID taskId, String workflowType, String payloadJson, int maxAttempts) {
        this(id,taskId,workflowType,payloadJson,maxAttempts,null);
    }

    public WorkItemEntity(UUID id, UUID taskId, String workflowType, String payloadJson, int maxAttempts, String requiredAgentId) {
        this.id = id;
        this.taskId = taskId;
        this.workflowType = workflowType;
        this.payloadJson = payloadJson;
        this.requiredAgentId = normalize(requiredAgentId);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.state = WorkItemState.QUEUED;
        this.attempt = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId(){return id;}
    public UUID getTaskId(){return taskId;}
    public String getWorkflowType(){return workflowType;}
    public String getPayloadJson(){return payloadJson;}
    public String getRequiredAgentId(){return requiredAgentId;}
    public WorkItemState getState(){return state;}
    public int getAttempt(){return attempt;}
    public int getMaxAttempts(){return maxAttempts;}
    public Instant getNextAttemptAt(){return nextAttemptAt;}
    public Instant getLockedAt(){return lockedAt;}
    public String getLeaseOwner(){return leaseOwner;}
    public Instant getLeaseExpiresAt(){return leaseExpiresAt;}
    public String getLastError(){return lastError;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;}
    public long getVersion(){return version;}

    public void claim(){claim("api-worker",60);}
    public void claim(String workerId,int leaseSeconds){
        if(state!=WorkItemState.QUEUED&&state!=WorkItemState.RETRY_WAIT)throw new IllegalStateException("Work item is not ready to claim: "+state);
        if(nextAttemptAt!=null&&nextAttemptAt.isAfter(Instant.now()))throw new IllegalStateException("Work item retry delay has not elapsed");
        if(workerId==null||workerId.isBlank())throw new IllegalArgumentException("Worker id is required");
        state=WorkItemState.RUNNING;
        attempt++;
        lockedAt=Instant.now();
        leaseOwner=workerId.trim();
        leaseExpiresAt=lockedAt.plusSeconds(Math.max(5,Math.min(leaseSeconds,900)));
        nextAttemptAt=null;
        updatedAt=lockedAt;
    }

    public void renewLease(String workerId,int leaseSeconds){requireRunning();if(leaseOwner==null||!leaseOwner.equals(workerId))throw new IllegalStateException("Work item lease is owned by another worker");leaseExpiresAt=Instant.now().plusSeconds(Math.max(5,Math.min(leaseSeconds,900)));updatedAt=Instant.now();}
    public boolean leaseExpired(Instant now){return state==WorkItemState.RUNNING&&leaseExpiresAt!=null&&!leaseExpiresAt.isAfter(now);}
    public void recoverExpiredLease(Instant now){if(!leaseExpired(now))throw new IllegalStateException("Work item lease has not expired");lastError="Worker lease expired and was recovered";lockedAt=null;leaseOwner=null;leaseExpiresAt=null;updatedAt=now;if(attempt<maxAttempts){state=WorkItemState.RETRY_WAIT;nextAttemptAt=now;}else{state=WorkItemState.FAILED;nextAttemptAt=null;}}
    public void succeed(){requireRunning();state=WorkItemState.SUCCEEDED;clearLease();lastError=null;updatedAt=Instant.now();}
    public void fail(String detail,Instant retryAt){requireRunning();lastError=detail;clearLease();updatedAt=Instant.now();if(attempt<maxAttempts&&retryAt!=null){state=WorkItemState.RETRY_WAIT;nextAttemptAt=retryAt;}else{state=WorkItemState.FAILED;nextAttemptAt=null;}}
    public void pause(){if(state!=WorkItemState.RUNNING&&state!=WorkItemState.QUEUED&&state!=WorkItemState.RETRY_WAIT)throw new IllegalStateException("Work item cannot be paused from "+state);state=WorkItemState.PAUSED;clearLease();updatedAt=Instant.now();}
    public void resume(){if(state!=WorkItemState.PAUSED)throw new IllegalStateException("Only paused work items can resume");state=WorkItemState.QUEUED;nextAttemptAt=null;updatedAt=Instant.now();}
    public void cancel(){if(state==WorkItemState.SUCCEEDED||state==WorkItemState.FAILED||state==WorkItemState.CANCELLED)return;state=WorkItemState.CANCELLED;clearLease();nextAttemptAt=null;updatedAt=Instant.now();}
    private void clearLease(){lockedAt=null;leaseOwner=null;leaseExpiresAt=null;}
    private void requireRunning(){if(state!=WorkItemState.RUNNING)throw new IllegalStateException("Work item is not running");}
    private static String normalize(String value){return value==null||value.isBlank()?null:value.trim();}
}
