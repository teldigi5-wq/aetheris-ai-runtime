package io.aetheris.orchestrator.planner;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name="aetheris_mission_plan_nodes")
public class MissionPlanNodeEntity {
    @Id private UUID id; @Column(nullable=false) private UUID missionId; @Column(nullable=false,length=120) private String stepKey; @Column(nullable=false) private UUID taskId; @Column(nullable=false,length=4000) private String dependencyTaskIdsCsv; @Column(nullable=false) private int priority; @Column(length=120) private String agentId; @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private MissionPlanNodeState state; @Column(nullable=false) private Instant createdAt; @Column(nullable=false) private Instant updatedAt;
    protected MissionPlanNodeEntity(){}
    public MissionPlanNodeEntity(UUID id,UUID missionId,String stepKey,UUID taskId,Collection<UUID> dependencies,int priority,String agentId){this.id=id;this.missionId=missionId;this.stepKey=stepKey;this.taskId=taskId;this.dependencyTaskIdsCsv=dependencies.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));this.priority=Math.max(0,Math.min(priority,100));this.agentId=agentId;this.state=dependencies.isEmpty()?MissionPlanNodeState.READY:MissionPlanNodeState.BLOCKED;this.createdAt=Instant.now();this.updatedAt=this.createdAt;}
    public UUID getId(){return id;} public UUID getMissionId(){return missionId;} public String getStepKey(){return stepKey;} public UUID getTaskId(){return taskId;} public int getPriority(){return priority;} public String getAgentId(){return agentId;} public MissionPlanNodeState getState(){return state;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
    public Set<UUID> getDependencyTaskIds(){if(dependencyTaskIdsCsv==null||dependencyTaskIdsCsv.isBlank())return Set.of();Set<UUID> out=new LinkedHashSet<>();for(String s:dependencyTaskIdsCsv.split(","))out.add(UUID.fromString(s));return Set.copyOf(out);}
    public void ready(){if(state==MissionPlanNodeState.BLOCKED){state=MissionPlanNodeState.READY;updatedAt=Instant.now();}}
    public void enqueued(){if(state!=MissionPlanNodeState.READY)throw new IllegalStateException("Plan node is not ready");state=MissionPlanNodeState.ENQUEUED;updatedAt=Instant.now();}
    public void completed(){state=MissionPlanNodeState.COMPLETED;updatedAt=Instant.now();}
    public void failed(){state=MissionPlanNodeState.FAILED;updatedAt=Instant.now();}
}
