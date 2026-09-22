package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.mcp.McpClientConnectionService;
import io.aetheris.orchestrator.mcp.McpDiscoveryResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/mcp")
public class McpDiscoveryController {

    private final McpClientConnectionService connections;

    public McpDiscoveryController(McpClientConnectionService connections) {
        this.connections = connections;
    }

    @PostMapping("/servers/{serverId}/discover")
    public McpDiscoveryResult discover(@PathVariable UUID serverId) {
        return connections.discover(serverId);
    }
}
