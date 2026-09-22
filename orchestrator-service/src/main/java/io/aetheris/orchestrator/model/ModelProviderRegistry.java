package io.aetheris.orchestrator.model;

import io.aetheris.orchestrator.agent.ModelClass;
import io.aetheris.orchestrator.policy.OperationMode;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ModelProviderRegistry {
    private final List<ModelProviderAdapter> adapters; private final ProviderReliabilityService reliability; private final ProviderRateLimitService rateLimits;
    public ModelProviderRegistry(List<ModelProviderAdapter> adapters,ProviderReliabilityService reliability,ProviderRateLimitService rateLimits){this.adapters=List.copyOf(adapters);this.reliability=reliability;this.rateLimits=rateLimits;}
    public List<ModelProviderSnapshot> snapshots(){return adapters.stream().map(ModelProviderAdapter::snapshot).toList();}
    public ModelProviderAdapter getRequired(String providerId){return adapters.stream().filter(a->a.id().equals(providerId)).findFirst().orElseThrow(()->new NoSuchElementException("Unknown model provider: "+providerId));}
    public ModelProviderAdapter select(ModelRouteRequest request){return candidates(request).stream().findFirst().orElse(null);}
    public List<ModelProviderAdapter> candidates(ModelRouteRequest request){OperationMode mode=request.mode()==null?OperationMode.BALANCED:request.mode();ModelClass modelClass=request.modelClass()==null?ModelClass.GENERAL:request.modelClass();return adapters.stream().filter(a->a.supportedClasses().contains(modelClass)).filter(a->a.snapshot().available()).filter(a->!(mode==OperationMode.PRIVATE||request.protectedData())||a.local()).filter(a->!(mode==OperationMode.ZERO_COST||!request.allowPaid())||a.zeroCost()).filter(a->reliability.canAttempt(a.id())).filter(a->rateLimits.canAttempt(a.id())).sorted(Comparator.comparingInt((ModelProviderAdapter a)->score(a,mode)).reversed()).toList();}
    private int score(ModelProviderAdapter a,OperationMode mode){int score=reliability.scoreAdjustment(a.id());if(a.local())score+=30;if(a.zeroCost())score+=20;if(mode==OperationMode.TURBO&&!a.local())score+=5;if(mode==OperationMode.PRIVATE&&a.local())score+=100;if(mode==OperationMode.ZERO_COST&&a.zeroCost())score+=100;return score;}
}
