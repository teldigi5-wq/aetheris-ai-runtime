package io.aetheris.orchestrator.planner;
import java.util.Set;
public record PlanStepRequest(String key,String title,String command,String agentId,int priority,Set<String> dependsOn){public PlanStepRequest{dependsOn=dependsOn==null?Set.of():Set.copyOf(dependsOn);}}
