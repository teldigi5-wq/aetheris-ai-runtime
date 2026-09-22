package io.aetheris.orchestrator.mcp;

import io.aetheris.orchestrator.agent.AgentCatalogService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class McpRegistryService {

    private final McpServerRepository servers;
    private final McpCapabilityGrantRepository grants;
    private final AgentCatalogService agents;

    public McpRegistryService(McpServerRepository servers, McpCapabilityGrantRepository grants, AgentCatalogService agents) {
        this.servers = servers;
        this.grants = grants;
        this.agents = agents;
    }

    @Transactional
    public McpServerEntity register(McpServerRegistrationRequest request) {
        String key = normalizeKey(request.serverKey());
        if (request.displayName() == null || request.displayName().isBlank()) throw new IllegalArgumentException("MCP display name is required");
        if (request.endpoint() == null || request.endpoint().isBlank()) throw new IllegalArgumentException("MCP endpoint is required");

        McpServerEntity server = servers.findByServerKey(key)
                .orElseGet(() -> new McpServerEntity(
                        UUID.randomUUID(), key, request.displayName().trim(), request.endpoint().trim(), request.local(), request.enabled(),
                        request.approvedCapabilities(), request.allowedDataClasses()));

        if (server.getId() != null && servers.existsById(server.getId())) {
            server.reconfigure(request.displayName().trim(), request.endpoint().trim(), request.local(), request.enabled(),
                    request.approvedCapabilities(), request.allowedDataClasses());
        }
        return servers.save(server);
    }

    public List<McpServerEntity> list() {
        return servers.findTop100ByOrderByDisplayNameAsc();
    }

    public McpServerEntity getRequired(UUID id) {
        return servers.findById(id).orElseThrow(() -> new NoSuchElementException("Unknown MCP server: " + id));
    }

    @Transactional
    public McpServerEntity updateHealth(UUID id, McpHealthUpdateRequest request) {
        McpServerEntity server = getRequired(id);
        server.recordHealth(request.healthy(), request.detail());
        return servers.save(server);
    }

    @Transactional
    public McpCapabilityGrantEntity grant(UUID serverId, McpCapabilityGrantRequest request) {
        McpServerEntity server = getRequired(serverId);
        agents.getRequired(request.agentId());
        if (!server.isEnabled()) throw new IllegalStateException("Cannot grant capabilities from a disabled MCP server");
        if (!server.getApprovedCapabilities().contains(request.capability())) {
            throw new IllegalArgumentException("Capability is not approved for this MCP server: " + request.capability());
        }
        String dataClass = request.dataClass() == null || request.dataClass().isBlank() ? "public" : request.dataClass().trim();
        if (!server.getAllowedDataClasses().isEmpty() && !server.getAllowedDataClasses().contains(dataClass)) {
            throw new IllegalArgumentException("Data class is not allowed for this MCP server: " + dataClass);
        }

        return grants.findTopByServerIdAndAgentIdAndCapabilityAndDataClassAndEnabledTrueOrderByCreatedAtDesc(
                        serverId, request.agentId(), request.capability(), dataClass)
                .orElseGet(() -> grants.save(new McpCapabilityGrantEntity(
                        UUID.randomUUID(), serverId, request.agentId(), request.capability(), dataClass)));
    }

    public List<McpCapabilityGrantEntity> grants(UUID serverId) {
        getRequired(serverId);
        return grants.findTop200ByServerIdOrderByCreatedAtDesc(serverId);
    }

    @Transactional
    public McpCapabilityGrantEntity revoke(UUID grantId) {
        McpCapabilityGrantEntity grant = grants.findById(grantId)
                .orElseThrow(() -> new NoSuchElementException("Unknown MCP grant: " + grantId));
        grant.revoke();
        return grants.save(grant);
    }

    private String normalizeKey(String raw) {
        if (raw == null) throw new IllegalArgumentException("MCP server key is required");
        String normalized = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("-+", "-");
        if (normalized.isBlank()) throw new IllegalArgumentException("MCP server key is invalid");
        return normalized;
    }
}
