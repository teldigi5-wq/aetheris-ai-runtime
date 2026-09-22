package io.aetheris.orchestrator.mission;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
@Entity @Table(name="aetheris_mission_sessions")
public class MissionSessionEntity {
    @Id private UUID id; @Column(nullable=false,length=240) private String title; @Column(nullable=false,length=8000) private String objective; @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private MissionStatus status; @Column(nullable=false,length=8000) private String taskIdsCsv; @Column(nullable=false) private Instant createdAt; @Column(nullable=false) private Instant updatedAt; @Version private long version;
    protected MissionSessionEntity(){}
    public MissionSessionEntity(UUID id,String title,String objective){this.id=id;this.title=title;this.objective=objective;this.status=MissionStatus.ACTIVE;this.taskIdsCsv="";this.createdAt=Instant.now();this.updatedAt=createdAt;}
    public UUID getId(){return id;} public String getTitle(){return title;} public String getObjective(){return objective;} public MissionStatus getStatus(){return status;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public Set<UUID> getTaskIds(){if(taskIdsCsv.isBlank())return Set.of();return Arrays.stream(taskIdsCsv.split(",")).map(UUID::fromString).collect(Collectors.toCollection(LinkedHashSet::new));}
    public void attach(UUID taskId){Set<UUID> ids=new LinkedHashSet<>(getTaskIds());ids.add(taskId);taskIdsCsv=ids.stream().map(UUID::toString).collect(Collectors.joining(","));updatedAt=Instant.now();}
    public void status(MissionStatus next){if(status==MissionStatus.COMPLETED||status==MissionStatus.CANCELLED)throw new IllegalStateException("Mission is terminal");status=next;updatedAt=Instant.now();}
}
