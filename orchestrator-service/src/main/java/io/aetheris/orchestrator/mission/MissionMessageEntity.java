package io.aetheris.orchestrator.mission;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="aetheris_mission_messages")
public class MissionMessageEntity {
    @Id private UUID id; @Column(nullable=false) private UUID missionId; private UUID taskId; @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private MissionSpeaker speaker; @Column(nullable=false,length=16000) private String content; @Column(nullable=false) private Instant createdAt;
    protected MissionMessageEntity(){} public MissionMessageEntity(UUID id,UUID missionId,UUID taskId,MissionSpeaker speaker,String content){this.id=id;this.missionId=missionId;this.taskId=taskId;this.speaker=speaker;this.content=content;this.createdAt=Instant.now();}
    public UUID getId(){return id;} public UUID getMissionId(){return missionId;} public UUID getTaskId(){return taskId;} public MissionSpeaker getSpeaker(){return speaker;} public String getContent(){return content;} public Instant getCreatedAt(){return createdAt;}
}
