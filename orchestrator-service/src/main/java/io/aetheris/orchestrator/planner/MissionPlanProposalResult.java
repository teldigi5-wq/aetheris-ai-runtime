package io.aetheris.orchestrator.planner;
import java.util.List;
public record MissionPlanProposalResult(boolean modelSucceeded,boolean valid,boolean materialized,String rawPlan,List<PlanStepRequest> steps,List<String> warnings,String detail){}
