package io.aetheris.orchestrator.mcp;

import io.aetheris.orchestrator.execution.InvocationAuditEntity;
import io.aetheris.orchestrator.execution.InvocationAuditService;
import io.aetheris.orchestrator.execution.InvocationKind;
import io.aetheris.orchestrator.execution.InvocationStatus;
import io.aetheris.orchestrator.task.TaskControlService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class McpClientConnectionService {

    public static final String PROTOCOL_VERSION = "2026-07-28";

    private final McpRegistryService registry;
    private final InvocationAuditService audit;
    private final TaskControlService control;
    private final RestClient client;
    private final Set<String> allowedLocalHosts;

    public McpClientConnectionService(
            McpRegistryService registry,
            InvocationAuditService audit,
            TaskControlService control,
            RestClient.Builder builder,
            @Value("${aetheris.mcp.allowed-local-hosts:localhost,127.0.0.1,::1,host.docker.internal}") String allowedLocalHosts) {
        this.registry = registry;
        this.audit = audit;
        this.control = control;
        this.client = builder.build();
        this.allowedLocalHosts = parseCsv(allowedLocalHosts);
    }

    public McpDiscoveryResult discover(java.util.UUID serverId) {
        McpServerEntity server = registry.getRequired(serverId);
        InvocationAuditEntity entry = audit.start(null, "mcp-integration-engineer", InvocationKind.MCP,
                server.getServerKey(), Map.of("operation", "server/discover", "protocolVersion", PROTOCOL_VERSION));

        if (control.isEmergencyStopActive()) {
            audit.finish(entry.getId(), InvocationStatus.CANCELLED, "Emergency stop is active", Map.of());
            return new McpDiscoveryResult(serverId, false, PROTOCOL_VERSION, Set.of(), List.of(), "Emergency stop is active", entry.getId());
        }
        if (!server.isEnabled()) {
            audit.finish(entry.getId(), InvocationStatus.BLOCKED, "MCP server is disabled", Map.of());
            return new McpDiscoveryResult(serverId, false, PROTOCOL_VERSION, Set.of(), List.of(), "MCP server is disabled", entry.getId());
        }

        try {
            URI endpoint = validateEndpoint(server);
            Map<?, ?> discovery = post(endpoint, "server/discover", Map.of());
            Set<String> capabilities = readCapabilities(discovery);
            Map<?, ?> toolsResponse = post(endpoint, "tools/list", Map.of());
            List<String> tools = readToolNames(toolsResponse);
            registry.updateHealth(serverId, new McpHealthUpdateRequest(true, "MCP " + PROTOCOL_VERSION + " discovery succeeded"));
            audit.finish(entry.getId(), InvocationStatus.SUCCEEDED, "MCP discovery completed", Map.of("toolCount", tools.size()));
            return new McpDiscoveryResult(serverId, true, PROTOCOL_VERSION, capabilities, tools, "MCP discovery completed", entry.getId());
        } catch (RuntimeException exception) {
            String detail = safeMessage(exception);
            registry.updateHealth(serverId, new McpHealthUpdateRequest(false, detail));
            audit.finish(entry.getId(), InvocationStatus.FAILED, detail, Map.of());
            return new McpDiscoveryResult(serverId, false, PROTOCOL_VERSION, Set.of(), List.of(), detail, entry.getId());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> post(URI endpoint, String method, Map<String, Object> params) {
        Map<String, Object> mergedParams = new java.util.LinkedHashMap<>(params);
        mergedParams.put("_meta", Map.of(
                "io.modelcontextprotocol/clientInfo", Map.of("name", "aetheris-platform", "version", "0.4.0"),
                "io.modelcontextprotocol/clientCapabilities", Map.of()));
        Map<String, Object> body = Map.of(
                "jsonrpc", "2.0",
                "id", java.util.UUID.randomUUID().toString(),
                "method", method,
                "params", mergedParams);

        Map<?, ?> response = client.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .header("MCP-Protocol-Version", PROTOCOL_VERSION)
                .header("Mcp-Method", method)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (response == null) throw new IllegalStateException("MCP server returned no JSON-RPC response");
        Object error = response.get("error");
        if (error != null) throw new IllegalStateException("MCP server returned an error: " + error);
        return response;
    }

    private URI validateEndpoint(McpServerEntity server) {
        URI endpoint = URI.create(server.getEndpoint());
        String scheme = endpoint.getScheme() == null ? "" : endpoint.getScheme().toLowerCase(Locale.ROOT);
        String host = endpoint.getHost() == null ? "" : endpoint.getHost().toLowerCase(Locale.ROOT);
        if (server.isLocal()) {
            if (!(scheme.equals("http") || scheme.equals("https"))) throw new IllegalArgumentException("Local MCP HTTP endpoint must use http or https");
            if (!allowedLocalHosts.contains(host)) throw new IllegalArgumentException("Local MCP host is not allowlisted: " + host);
        } else if (!scheme.equals("https")) {
            throw new IllegalArgumentException("Remote MCP endpoints must use HTTPS");
        }
        return endpoint;
    }

    private Set<String> readCapabilities(Map<?, ?> response) {
        Object resultObject = response.get("result");
        if (!(resultObject instanceof Map<?, ?> result)) return Set.of();
        Object capabilitiesObject = result.get("capabilities");
        if (!(capabilitiesObject instanceof Map<?, ?> capabilities)) return Set.of();
        Set<String> names = new HashSet<>();
        for (Object key : capabilities.keySet()) names.add(String.valueOf(key));
        return names;
    }

    private List<String> readToolNames(Map<?, ?> response) {
        Object resultObject = response.get("result");
        if (!(resultObject instanceof Map<?, ?> result)) return List.of();
        Object toolsObject = result.get("tools");
        if (!(toolsObject instanceof List<?> rawTools)) return List.of();
        List<String> names = new ArrayList<>();
        for (Object rawTool : rawTools) {
            if (rawTool instanceof Map<?, ?> tool && tool.get("name") != null) names.add(String.valueOf(tool.get("name")));
        }
        return names;
    }

    private Set<String> parseCsv(String csv) {
        Set<String> values = new HashSet<>();
        for (String value : csv.split(",")) {
            if (!value.isBlank()) values.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(values);
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
