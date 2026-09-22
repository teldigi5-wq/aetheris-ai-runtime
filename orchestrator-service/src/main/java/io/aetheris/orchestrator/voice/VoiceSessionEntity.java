package io.aetheris.orchestrator.voice;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name="aetheris_voice_sessions")
public class VoiceSessionEntity {
 @Id private UUID id; private UUID missionId; private UUID taskId; @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private VoiceSessionStatus status; @Column(length=8000) private String latestTranscript; @Column(nullable=false) private Instant createdAt; @Column(nullable=false) private Instant updatedAt;
 protected VoiceSessionEntity(){} public VoiceSessionEntity(UUID id,UUID missionId,UUID taskId){this.id=id;this.missionId=missionId;this.taskId=taskId;this.status=VoiceSessionStatus.LISTENING;this.createdAt=Instant.now();this.updatedAt=this.createdAt;}
 public UUID getId(){return id;} public UUID getMissionId(){return missionId;} public UUID getTaskId(){return taskId;} public VoiceSessionStatus getStatus(){return status;} public String getLatestTranscript(){return latestTranscript;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
 public void transcript(String text){latestTranscript=text;status=VoiceSessionStatus.LISTENING;updatedAt=Instant.now();} public void speaking(){status=VoiceSessionStatus.SPEAKING;updatedAt=Instant.now();} public void interrupt(){status=VoiceSessionStatus.INTERRUPTED;updatedAt=Instant.now();} public void stop(){status=VoiceSessionStatus.STOPPED;updatedAt=Instant.now();}
}
