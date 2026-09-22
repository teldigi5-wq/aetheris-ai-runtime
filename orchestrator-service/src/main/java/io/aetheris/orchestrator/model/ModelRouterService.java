package io.aetheris.orchestrator.model;

import io.aetheris.orchestrator.agent.ModelClass;
import io.aetheris.orchestrator.policy.OperationMode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ModelRouterService {

    private final ModelProviderRegistry providers;

    public ModelRouterService(ModelProviderRegistry providers) {
        this.providers = providers;
    }

    public ModelRouteDecision route(ModelRouteRequest request) {
        OperationMode mode = request.mode() == null ? OperationMode.BALANCED : request.mode();
        ModelClass modelClass = request.modelClass() == null ? ModelClass.GENERAL : request.modelClass();
        ModelProviderAdapter provider = providers.select(request);

        if (provider != null) {
            ModelProviderSnapshot snapshot = provider.snapshot();
            return new ModelRouteDecision(
                    true,
                    provider.id(),
                    snapshot.model(),
                    provider.local(),
                    provider.zeroCost(),
                    "Selected healthy provider for " + modelClass + " in " + mode + " mode.");
        }

        if (mode == OperationMode.PRIVATE || request.protectedData()) {
            return ModelRouteDecision.unavailable(
                    "No approved local provider is currently reachable. Private/protected-data routing will not fall back off-device.");
        }
        if (mode == OperationMode.ZERO_COST || !request.allowPaid()) {
            return ModelRouteDecision.unavailable(
                    "No healthy zero-cost provider is currently reachable. Aetheris will not fabricate provider availability or silently use paid inference.");
        }
        return ModelRouteDecision.unavailable(
                "No healthy approved provider can satisfy this request. Configure an owner-approved provider adapter before cloud/paid routing is enabled.");
    }

    public List<ModelProviderSnapshot> providers() {
        return providers.snapshots();
    }
}
