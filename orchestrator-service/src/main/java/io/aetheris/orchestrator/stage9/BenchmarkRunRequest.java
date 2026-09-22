package io.aetheris.orchestrator.stage9;
public record BenchmarkRunRequest(String packId,String subjectType,String subjectId,String scenario,boolean success,int corrections,long latencyMs,double costUsd,double evidenceScore,double hallucinationPenalty){}
