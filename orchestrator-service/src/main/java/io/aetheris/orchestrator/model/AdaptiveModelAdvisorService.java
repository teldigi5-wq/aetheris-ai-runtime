package io.aetheris.orchestrator.model;

import io.aetheris.orchestrator.agent.ModelClass;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.task.TaskService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.math.*;
import java.util.*;

@Service
public class AdaptiveModelAdvisorService {
    private final TaskModelObjectiveRepository objectives; private final TaskService tasks; private final ModelProviderRegistry providers; private final ProviderReliabilityService reliability; private final ProviderRateLimitService rateLimits; private final ModelArenaService arena;
    public AdaptiveModelAdvisorService(TaskModelObjectiveRepository objectives,TaskService tasks,ModelProviderRegistry providers,ProviderReliabilityService reliability,ProviderRateLimitService rateLimits,ModelArenaService arena){this.objectives=objectives;this.tasks=tasks;this.providers=providers;this.reliability=reliability;this.rateLimits=rateLimits;this.arena=arena;}
    @Transactional public TaskModelObjectiveEntity set(UUID taskId,TaskModelObjectiveRequest r){tasks.getRequired(taskId);TaskModelObjectiveEntity e=objectives.findById(taskId).orElseGet(()->new TaskModelObjectiveEntity(taskId));e.update(r.maxLatencyMs()<=0?8000:r.maxLatencyMs(),r.minQualityScore(),r.maxCostUsdPerRequest(),r.preferLocal(),r.allowPaid());return objectives.save(e);}
    public TaskModelObjectiveEntity get(UUID taskId){tasks.getRequired(taskId);return objectives.findById(taskId).orElseGet(()->new TaskModelObjectiveEntity(taskId));}
    public List<ProviderCandidateScore> rank(UUID taskId,ModelClass modelClass,OperationMode mode,boolean protectedData,long estimatedUnits){TaskModelObjectiveEntity objective=get(taskId);ModelRouteRequest route=new ModelRouteRequest(modelClass,mode,protectedData,objective.isAllowPaid());List<ProviderCandidateScore> scores=new ArrayList<>();long units=Math.max(1,estimatedUnits);for(ModelProviderAdapter p:providers.candidates(route)){ProviderHealthSnapshot health=reliability.snapshot(p.id());double quality=arena.averageQuality(p.id());BigDecimal cost=p.zeroCost()?BigDecimal.ZERO:p.costPerThousandUnitsUsd().multiply(BigDecimal.valueOf(units)).divide(BigDecimal.valueOf(1000),8,RoundingMode.HALF_UP);int score=100;List<String> reasons=new ArrayList<>();if(objective.isPreferLocal()&&p.local()){score+=20;reasons.add("local preference");}if(health.averageLatencyMs()>0){if(health.averageLatencyMs()>objective.getMaxLatencyMs()){score-=45;reasons.add("latency above objective");}else{score+=10;reasons.add("latency within objective");}}if(quality>0){if(quality<objective.getMinQualityScore()){score-=35;reasons.add("quality below objective");}else{score+=Math.min(20,(int)Math.round(quality/5));reasons.add("quality evidence meets objective");}}if(!rateLimits.canAttempt(p.id())){score-=1000;reasons.add("rate limited");}if(!p.zeroCost()&&objective.getMaxCostUsdPerRequest().signum()<=0){score-=1000;reasons.add("task has zero paid budget");}else if(cost.compareTo(objective.getMaxCostUsdPerRequest())>0&&!p.zeroCost()){score-=1000;reasons.add("estimated request cost exceeds objective");}scores.add(new ProviderCandidateScore(p.id(),p.snapshot().model(),score,health.averageLatencyMs(),quality,cost,p.local(),p.zeroCost(),reasons.isEmpty()?"eligible":String.join(", ",reasons)));}return scores.stream().sorted(Comparator.comparingInt(ProviderCandidateScore::score).reversed()).toList();}
}
