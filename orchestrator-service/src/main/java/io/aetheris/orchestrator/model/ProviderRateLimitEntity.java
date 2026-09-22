package io.aetheris.orchestrator.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="aetheris_provider_rate_limits")
public class ProviderRateLimitEntity {
    @Id @Column(length=120) private String providerId;
    private Instant blockedUntil;
    private Long remainingUnits;
    private Instant quotaResetAt;
    @Column(length=2000) private String reason;
    @Column(nullable=false) private Instant updatedAt;
    protected ProviderRateLimitEntity(){}
    public ProviderRateLimitEntity(String providerId){this.providerId=providerId;this.updatedAt=Instant.now();}
    public String getProviderId(){return providerId;} public Instant getBlockedUntil(){return blockedUntil;} public Long getRemainingUnits(){return remainingUnits;} public Instant getQuotaResetAt(){return quotaResetAt;} public String getReason(){return reason;} public Instant getUpdatedAt(){return updatedAt;}
    public boolean blocked(Instant now){return blockedUntil!=null&&blockedUntil.isAfter(now);}
    public void mark(long retryAfterSeconds,Long remainingUnits,Instant quotaResetAt,String reason){Instant now=Instant.now();this.blockedUntil=now.plusSeconds(Math.max(1,Math.min(retryAfterSeconds,86400)));this.remainingUnits=remainingUnits;this.quotaResetAt=quotaResetAt;this.reason=reason==null?"Provider rate limited":reason.trim();this.updatedAt=now;}
    public void clear(){this.blockedUntil=null;this.reason=null;this.updatedAt=Instant.now();}
}
