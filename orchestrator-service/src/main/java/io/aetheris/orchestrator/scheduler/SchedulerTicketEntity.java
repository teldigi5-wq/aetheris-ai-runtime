package io.aetheris.orchestrator.scheduler;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="aetheris_scheduler_tickets")
public class SchedulerTicketEntity {
    @Id private UUID id;
    @Column(nullable=false,unique=true) private UUID workItemId;
    @Column(nullable=false) private int priority;
    @Column(nullable=false,length=80) private String lane;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private SchedulerTicketState state;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    protected SchedulerTicketEntity(){}
    public SchedulerTicketEntity(UUID id,UUID workItemId,int priority,String lane){this.id=id;this.workItemId=workItemId;this.priority=Math.max(0,Math.min(priority,100));this.lane=lane==null||lane.isBlank()?"default":lane.trim();this.state=SchedulerTicketState.QUEUED;this.createdAt=Instant.now();this.updatedAt=this.createdAt;}
    public UUID getId(){return id;} public UUID getWorkItemId(){return workItemId;} public int getPriority(){return priority;} public String getLane(){return lane;} public SchedulerTicketState getState(){return state;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
    public void dispatched(){if(state!=SchedulerTicketState.QUEUED)throw new IllegalStateException("Scheduler ticket is not queued");state=SchedulerTicketState.DISPATCHED;updatedAt=Instant.now();}
    public void cancel(){if(state==SchedulerTicketState.DISPATCHED)throw new IllegalStateException("Dispatched scheduler ticket cannot be cancelled directly");state=SchedulerTicketState.CANCELLED;updatedAt=Instant.now();}
}
