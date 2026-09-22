package io.aetheris.orchestrator.model;
import java.math.BigDecimal;
public record ProviderCandidateScore(String providerId,String model,int score,long averageLatencyMs,double averageQualityScore,BigDecimal estimatedCostUsd,boolean local,boolean zeroCost,String reason){}
