package io.aetheris.orchestrator.stage9;
import io.aetheris.orchestrator.planner.PlanStepRequest;
import io.aetheris.orchestrator.policy.OperationMode;
import java.util.List;
public record MissionDryRunRequest(OperationMode mode,List<PlanStepRequest> steps,boolean protectedData,boolean allowPaid,long expectedInputTokens){public MissionDryRunRequest{steps=steps==null?List.of():List.copyOf(steps);}}
