package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.mcp.McpToolInvocationRequest;
import io.aetheris.orchestrator.mcp.McpToolInvocationResult;
import io.aetheris.orchestrator.mcp.McpToolInvocationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orchestrator/mcp/tools")
public class McpToolInvocationController {

    private final McpToolInvocationService tools;

    public McpToolInvocationController(McpToolInvocationService tools) {
        this.tools = tools;
    }

    @PostMapping("/invoke")
    public McpToolInvocationResult invoke(@Valid @RequestBody McpToolInvocationRequest request) {
        return tools.invoke(request);
    }
}
