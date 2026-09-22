package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.operator.BrowserOperatorService;
import io.aetheris.orchestrator.operator.BrowserTypes.BrowserExecuteRequest;
import io.aetheris.orchestrator.operator.BrowserTypes.BrowserExecutionResponse;
import io.aetheris.orchestrator.operator.BrowserTypes.BrowserPlanRequest;
import io.aetheris.orchestrator.operator.BrowserTypes.BrowserRuntimeStatus;
import io.aetheris.orchestrator.operator.BrowserTypes.BrowserWorkflowPlan;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orchestrator/operator/browser")
public class BrowserOperatorController {

    private final BrowserOperatorService browser;

    public BrowserOperatorController(BrowserOperatorService browser) {
        this.browser = browser;
    }

    @GetMapping("/status")
    public BrowserRuntimeStatus status() {
        return browser.status();
    }

    @PostMapping("/plan")
    public BrowserWorkflowPlan plan(@RequestBody BrowserPlanRequest request) {
        return browser.plan(request);
    }

    @PostMapping("/execute")
    public BrowserExecutionResponse execute(@RequestBody BrowserExecuteRequest request) {
        return browser.execute(request);
    }
}
