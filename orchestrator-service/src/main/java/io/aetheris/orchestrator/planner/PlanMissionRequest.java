package io.aetheris.orchestrator.planner;
import io.aetheris.orchestrator.policy.OperationMode;
import java.util.List;
public record PlanMissionRequest(OperationMode mode,List<PlanStepRequest> steps){public PlanMissionRequest{steps=steps==null?List.of():List.copyOf(steps);}}
