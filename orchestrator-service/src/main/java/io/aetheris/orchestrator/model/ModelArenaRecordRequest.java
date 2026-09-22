package io.aetheris.orchestrator.model;
public record ModelArenaRecordRequest(String providerId,String model,String scenario,boolean success,long latencyMs,Double qualityScore,String detail) {}
