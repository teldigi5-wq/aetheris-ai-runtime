package io.aetheris.orchestrator.evaluation;
public record EvaluationRequest(String subjectType,String subjectId,String scenario,boolean success,int corrections,long latencyMs,double costUsd,double evidenceScore,double hallucinationPenalty){}
