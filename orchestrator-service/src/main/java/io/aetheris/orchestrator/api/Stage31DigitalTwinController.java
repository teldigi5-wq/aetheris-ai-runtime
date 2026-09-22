package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.stage31.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stage31")
public class Stage31DigitalTwinController {
    private final Stage31DigitalTwinService service;

    public Stage31DigitalTwinController(Stage31DigitalTwinService service) {
        this.service = service;
    }

    @PostMapping("/assess")
    public Stage31AssessmentResult assess(@RequestBody Stage31AssessmentRequest request) {
        return service.assess(request);
    }

    @PostMapping("/recovery/plan")
    public RecoveryPlan planRecovery(@RequestBody RecoveryAction action) {
        return service.planRecovery(action);
    }

    @PostMapping("/updates/transition")
    public UpdateTransition transitionUpdate(@RequestBody UpdateTransitionRequest request) {
        return service.transitionUpdate(request);
    }

    @PostMapping("/models/rank")
    public List<ModelBenchmarkScore> rankModels(@RequestBody ModelBenchmarkRequest request) {
        return service.rankModels(request);
    }
}
