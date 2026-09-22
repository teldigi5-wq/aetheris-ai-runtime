package io.aetheris.orchestrator.model;
import java.time.Instant;
public record ProviderHealthSnapshot(String providerId,long successCount,long failureCount,int consecutiveFailures,long averageLatencyMs,boolean circuitOpen,Instant circuitOpenUntil,String lastError) {}
