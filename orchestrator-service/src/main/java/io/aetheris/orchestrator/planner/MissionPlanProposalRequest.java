package io.aetheris.orchestrator.planner;
import io.aetheris.orchestrator.policy.OperationMode;
public record MissionPlanProposalRequest(String objective,OperationMode mode,boolean protectedData,boolean allowPaid,boolean materialize){}
