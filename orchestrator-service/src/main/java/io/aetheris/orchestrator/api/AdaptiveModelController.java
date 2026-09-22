package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.agent.ModelClass;
import io.aetheris.orchestrator.model.*;
import io.aetheris.orchestrator.policy.OperationMode;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/orchestrator/models/adaptive")
public class AdaptiveModelController {
 private final AdaptiveModelAdvisorService advisor; private final ProviderRateLimitService rateLimits;
 public AdaptiveModelController(AdaptiveModelAdvisorService advisor,ProviderRateLimitService rateLimits){this.advisor=advisor;this.rateLimits=rateLimits;}
 @PutMapping("/tasks/{taskId}/objective") public TaskModelObjectiveEntity objective(@PathVariable UUID taskId,@RequestBody TaskModelObjectiveRequest r){return advisor.set(taskId,r);}
 @GetMapping("/tasks/{taskId}/objective") public TaskModelObjectiveEntity objective(@PathVariable UUID taskId){return advisor.get(taskId);}
 @GetMapping("/tasks/{taskId}/rank") public List<ProviderCandidateScore> rank(@PathVariable UUID taskId,@RequestParam(defaultValue="GENERAL") ModelClass modelClass,@RequestParam(defaultValue="BALANCED") OperationMode mode,@RequestParam(defaultValue="false") boolean protectedData,@RequestParam(defaultValue="1000") long estimatedUnits){return advisor.rank(taskId,modelClass,mode,protectedData,estimatedUnits);}
 @GetMapping("/rate-limits") public List<ProviderRateLimitSnapshot> rateLimits(){return rateLimits.snapshots();}
 @PostMapping("/rate-limits/{providerId}") public ProviderRateLimitSnapshot mark(@PathVariable String providerId,@RequestBody ProviderRateLimitUpdateRequest r){return rateLimits.mark(providerId,r.retryAfterSeconds(),r.remainingUnits(),r.quotaResetAt(),r.reason());}
 @DeleteMapping("/rate-limits/{providerId}") public ProviderRateLimitSnapshot clear(@PathVariable String providerId){return rateLimits.clear(providerId);}
}
