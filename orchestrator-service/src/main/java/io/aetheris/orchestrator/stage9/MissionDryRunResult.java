package io.aetheris.orchestrator.stage9;
import java.util.List;
public record MissionDryRunResult(boolean valid,int stepCount,int dependencyEdges,int maxParallelism,String riskLevel,boolean approvalRequired,double estimatedMaxCostUsd,List<String> evidenceRequirements,List<String> warnings,String detail){}
