package io.aetheris.orchestrator.model;
import java.time.Instant;
public record ProviderRateLimitUpdateRequest(long retryAfterSeconds,Long remainingUnits,Instant quotaResetAt,String reason){}
