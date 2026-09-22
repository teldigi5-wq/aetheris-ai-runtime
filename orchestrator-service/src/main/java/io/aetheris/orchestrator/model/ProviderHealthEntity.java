package io.aetheris.orchestrator.model;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name="aetheris_provider_health")
public class ProviderHealthEntity {
    @Id @Column(length=120) private String providerId;
    @Column(nullable=false) private long successCount;
    @Column(nullable=false) private long failureCount;
    @Column(nullable=false) private int consecutiveFailures;
    @Column(nullable=false) private long totalLatencyMs;
    private Instant circuitOpenUntil;
    private Instant lastSuccessAt;
    private Instant lastFailureAt;
    @Column(length=2000) private String lastError;
    @Version private long version;
    protected ProviderHealthEntity(){}
    public ProviderHealthEntity(String providerId){this.providerId=providerId;}
    public String getProviderId(){return providerId;} public long getSuccessCount(){return successCount;} public long getFailureCount(){return failureCount;} public int getConsecutiveFailures(){return consecutiveFailures;} public long getTotalLatencyMs(){return totalLatencyMs;} public Instant getCircuitOpenUntil(){return circuitOpenUntil;} public Instant getLastSuccessAt(){return lastSuccessAt;} public Instant getLastFailureAt(){return lastFailureAt;} public String getLastError(){return lastError;}
    public boolean circuitOpen(Instant now){return circuitOpenUntil!=null&&circuitOpenUntil.isAfter(now);}
    public void success(long latencyMs){successCount++;consecutiveFailures=0;totalLatencyMs+=Math.max(0,latencyMs);lastSuccessAt=Instant.now();lastError=null;circuitOpenUntil=null;}
    public void failure(String detail,int threshold,Duration cooldown){failureCount++;consecutiveFailures++;lastFailureAt=Instant.now();lastError=detail;if(consecutiveFailures>=threshold)circuitOpenUntil=Instant.now().plus(cooldown);}
}
