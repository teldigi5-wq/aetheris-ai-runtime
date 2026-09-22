package io.aetheris.orchestrator.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="aetheris_task_model_objectives")
public class TaskModelObjectiveEntity {
    @Id private UUID taskId;
    @Column(nullable=false) private long maxLatencyMs;
    @Column(nullable=false) private double minQualityScore;
    @Column(nullable=false,precision=18,scale=8) private BigDecimal maxCostUsdPerRequest;
    @Column(nullable=false) private boolean preferLocal;
    @Column(nullable=false) private boolean allowPaid;
    @Column(nullable=false) private Instant updatedAt;
    protected TaskModelObjectiveEntity(){}
    public TaskModelObjectiveEntity(UUID taskId){this.taskId=taskId;update(8000,60d,BigDecimal.ZERO,true,false);}
    public UUID getTaskId(){return taskId;} public long getMaxLatencyMs(){return maxLatencyMs;} public double getMinQualityScore(){return minQualityScore;} public BigDecimal getMaxCostUsdPerRequest(){return maxCostUsdPerRequest;} public boolean isPreferLocal(){return preferLocal;} public boolean isAllowPaid(){return allowPaid;} public Instant getUpdatedAt(){return updatedAt;}
    public void update(long maxLatencyMs,double minQualityScore,BigDecimal maxCostUsdPerRequest,boolean preferLocal,boolean allowPaid){this.maxLatencyMs=Math.max(100,Math.min(maxLatencyMs,120000));this.minQualityScore=Math.max(0,Math.min(minQualityScore,100));this.maxCostUsdPerRequest=maxCostUsdPerRequest==null?BigDecimal.ZERO:maxCostUsdPerRequest.max(BigDecimal.ZERO);this.preferLocal=preferLocal;this.allowPaid=allowPaid;this.updatedAt=Instant.now();}
}
