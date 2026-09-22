package io.aetheris.orchestrator.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="aetheris_model_arena_measurements")
public class ModelArenaMeasurementEntity {
    @Id private UUID id;
    @Column(nullable=false,length=120) private String providerId;
    @Column(nullable=false,length=240) private String model;
    @Column(nullable=false,length=120) private String scenario;
    @Column(nullable=false) private boolean success;
    @Column(nullable=false) private long latencyMs;
    private Double qualityScore;
    @Column(length=2000) private String detail;
    @Column(nullable=false) private Instant createdAt;
    protected ModelArenaMeasurementEntity(){}
    public ModelArenaMeasurementEntity(UUID id,String providerId,String model,String scenario,boolean success,long latencyMs,Double qualityScore,String detail){this.id=id;this.providerId=providerId;this.model=model;this.scenario=scenario;this.success=success;this.latencyMs=Math.max(0,latencyMs);this.qualityScore=qualityScore;this.detail=detail;this.createdAt=Instant.now();}
    public UUID getId(){return id;} public String getProviderId(){return providerId;} public String getModel(){return model;} public String getScenario(){return scenario;} public boolean isSuccess(){return success;} public long getLatencyMs(){return latencyMs;} public Double getQualityScore(){return qualityScore;} public String getDetail(){return detail;} public Instant getCreatedAt(){return createdAt;}
}
