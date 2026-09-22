package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.executive.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orchestrator/executive")
public class ExecutiveAgentController {
    private final ExecutiveAgentService service;

    public ExecutiveAgentController(ExecutiveAgentService service) {
        this.service = service;
    }

    @PostMapping("/overnight-runs")
    public ExecutiveOvernightBriefing run(@RequestBody ExecutiveOvernightRunRequest request) {
        return service.run(request);
    }

    @GetMapping("/briefings")
    public List<ExecutiveBriefingEntity> recent() {
        return service.recent();
    }
}
