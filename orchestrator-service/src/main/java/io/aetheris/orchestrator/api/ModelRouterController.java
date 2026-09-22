package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.model.ModelProviderSnapshot;
import io.aetheris.orchestrator.model.ModelRouteDecision;
import io.aetheris.orchestrator.model.ModelRouteRequest;
import io.aetheris.orchestrator.model.ModelRouterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orchestrator/models")
public class ModelRouterController {

    private final ModelRouterService router;

    public ModelRouterController(ModelRouterService router) {
        this.router = router;
    }

    @PostMapping("/route")
    public ModelRouteDecision route(@RequestBody ModelRouteRequest request) {
        return router.route(request);
    }

    @GetMapping("/providers")
    public List<ModelProviderSnapshot> providers() {
        return router.providers();
    }
}
