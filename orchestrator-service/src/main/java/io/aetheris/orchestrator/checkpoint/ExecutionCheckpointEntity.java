package io.aetheris.orchestrator.checkpoint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aetheris_execution_checkpoints")
public class ExecutionCheckpointEntity {
    @Id private UUID id;
    @Column(nullable=false) private UUID taskId;
    @Column(nullable=false, length=80) private String type;
    @Column(nullable=false, length=240) private String label;
    @Column(nullable=false, length=2000) private String reference;
    @Column(nullable=false, length=16000) private String metadataJson;
    @Column(nullable=false) private Instant createdAt;
    protected ExecutionCheckpointEntity() {}
    public ExecutionCheckpointEntity(UUID id, UUID taskId, String type, String label, String reference, String metadataJson) {
        this.id=id; this.taskId=taskId; this.type=type; this.label=label; this.reference=reference; this.metadataJson=metadataJson; this.createdAt=Instant.now();
    }
    public UUID getId(){return id;} public UUID getTaskId(){return taskId;} public String getType(){return type;} public String getLabel(){return label;}
    public String getReference(){return reference;} public String getMetadataJson(){return metadataJson;} public Instant getCreatedAt(){return createdAt;}
}
