package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.tool.SafeToolRegistryService;
import io.aetheris.orchestrator.tool.ToolAccessDecision;
import io.aetheris.orchestrator.tool.ToolAccessRequest;
import io.aetheris.orchestrator.tool.ToolDescriptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orchestrator/tools")
public class ToolRegistryController {

    private final SafeToolRegistryService tools;

    public ToolRegistryController(SafeToolRegistryService tools) {
        this.tools = tools;
    }

    @GetMapping
    public List<ToolDescriptor> list() {
        return tools.list();
    }

    @PostMapping("/evaluate")
    public ToolAccessDecision evaluate(@RequestBody ToolAccessRequest request) {
        return tools.evaluate(request);
    }
}
