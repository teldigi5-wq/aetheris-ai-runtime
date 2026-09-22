package io.aetheris.orchestrator.model;
import java.time.Instant;
public record ProviderRateLimitSnapshot(String providerId,boolean blocked,Instant blockedUntil,Long remainingUnits,Instant quotaResetAt,String reason){}
