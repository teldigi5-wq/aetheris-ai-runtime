package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.mcp.McpCapabilityGrantEntity;
import io.aetheris.orchestrator.mcp.McpCapabilityGrantRequest;
import io.aetheris.orchestrator.mcp.McpHealthUpdateRequest;
import io.aetheris.orchestrator.mcp.McpRegistryService;
import io.aetheris.orchestrator.mcp.McpServerEntity;
import io.aetheris.orchestrator.mcp.McpServerRegistrationRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/mcp")
public class McpRegistryController {

    private final McpRegistryService registry;

    public McpRegistryController(McpRegistryService registry) {
        this.registry = registry;
    }

    @PostMapping("/servers")
    public McpServerEntity register(@RequestBody McpServerRegistrationRequest request) {
        return registry.register(request);
    }

    @GetMapping("/servers")
    public List<McpServerEntity> list() {
        return registry.list();
    }

    @PostMapping("/servers/{serverId}/health")
    public McpServerEntity health(@PathVariable UUID serverId, @RequestBody McpHealthUpdateRequest request) {
        return registry.updateHealth(serverId, request);
    }

    @PostMapping("/servers/{serverId}/grants")
    public McpCapabilityGrantEntity grant(@PathVariable UUID serverId, @RequestBody McpCapabilityGrantRequest request) {
        return registry.grant(serverId, request);
    }

    @GetMapping("/servers/{serverId}/grants")
    public List<McpCapabilityGrantEntity> grants(@PathVariable UUID serverId) {
        return registry.grants(serverId);
    }

    @PostMapping("/grants/{grantId}/revoke")
    public McpCapabilityGrantEntity revoke(@PathVariable UUID grantId) {
        return registry.revoke(grantId);
    }
}
