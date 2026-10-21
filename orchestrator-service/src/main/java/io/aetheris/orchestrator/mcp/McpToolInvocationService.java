package io.aetheris.orchestrator.mcp;

import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.execution.InvocationAuditEntity;
import io.aetheris.orchestrator.execution.InvocationAuditService;
import io.aetheris.orchestrator.execution.InvocationKind;
import io.aetheris.orchestrator.execution.InvocationStatus;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.task.DirectExecutionAuthorityService;
import io.aetheris.orchestrator.task.TaskControlService;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.task.TaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class McpToolInvocationService {

    private final McpRegistryService registry;
    private final McpCapabilityGrantRepository grants;
    private final AgentCatalogService agents;
    private final TaskService tasks;
    private final DirectExecutionAuthorityService authority;
    private final TaskControlService control;
    private final InvocationAuditService audit;
    private final RestClient client;
    private final McpEndpointPolicy endpointPolicy;

    public McpToolInvocationService(
            McpRegistryService registry,
            McpCapabilityGrantRepository grants,
            AgentCatalogService agents,
            TaskService tasks,
            DirectExecutionAuthorityService authority,
            TaskControlService control,
            InvocationAuditService audit,
            RestClient.Builder builder,
            McpEndpointPolicy endpointPolicy) {
        this.registry = registry;
        this.grants = grants;
        this.agents = agents;
        this.tasks = tasks;
        this.authority = authority;
        this.control = control;
        this.audit = audit;
        this.client = builder.requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NEVER).build())).build();
        this.endpointPolicy = endpointPolicy;
    }

    public McpToolInvocationResult invoke(McpToolInvocationRequest request) {
        agents.getRequired(request.agentId());
        if (request.taskId() != null) tasks.getRequired(request.taskId());
        McpServerEntity server = registry.getRequired(request.serverId());
        InvocationAuditEntity entry = audit.start(request.taskId(), request.agentId(), InvocationKind.MCP,
                server.getServerKey() + ":" + request.toolName(),
                Map.of("operation", "tools/call", "capability", request.capability(), "dataClass", request.dataClass()));

        try {
            if (control.isEmergencyStopActive()) return blocked(entry, request, "Emergency stop is active", InvocationStatus.CANCELLED);
            if (!server.isEnabled()) return blocked(entry, request, "MCP server is disabled", InvocationStatus.BLOCKED);
            if (server.getStatus() != McpServerStatus.HEALTHY) return blocked(entry, request, "MCP server must pass discovery/health before tool invocation", InvocationStatus.BLOCKED);
            if (!server.getApprovedCapabilities().contains(request.capability())) return blocked(entry, request, "Capability is not approved for this MCP server", InvocationStatus.BLOCKED);
            if (!server.getAllowedDataClasses().contains(request.dataClass())) return blocked(entry, request, "Data class is not allowed for this MCP server", InvocationStatus.BLOCKED);
            if (grants.findTopByServerIdAndAgentIdAndCapabilityAndDataClassAndEnabledTrueOrderByCreatedAtDesc(
                    server.getId(), request.agentId(), request.capability(), request.dataClass()).isEmpty()) {
                return blocked(entry, request, "Agent has no active MCP capability grant", InvocationStatus.BLOCKED);
            }

            TaskEntity task;
            try {
                task = authority.requireRunningSpecialist(request.taskId(), request.agentId(), "mcp");
            } catch (RuntimeException exception) {
                return blocked(entry, request, "Direct execution authority denied: " + safeMessage(exception), InvocationStatus.BLOCKED);
            }
            if (task.getMode() == OperationMode.PRIVATE && !server.isLocal()) {
                return blocked(entry, request, "PRIVATE mode blocks remote MCP tool execution", InvocationStatus.BLOCKED);
            }

            URI endpoint = validateEndpoint(server);
            Map<?, ?> listed = post(endpoint, "tools/list", Map.of());
            Map<?, ?> tool = findTool(listed, request.toolName());
            validateArguments(tool, request.arguments());
            Map<?, ?> response = post(endpoint, "tools/call", Map.of("name", request.toolName(), "arguments", request.arguments()));
            Map<String, Object> result = readResult(response);
            audit.finish(entry.getId(), InvocationStatus.SUCCEEDED, "MCP tool invocation completed", Map.of("tool", request.toolName()));
            return new McpToolInvocationResult(true, server.getId(), request.toolName(), result, "MCP tool invocation completed", entry.getId());
        } catch (RuntimeException exception) {
            String detail = safeMessage(exception);
            audit.finish(entry.getId(), InvocationStatus.FAILED, detail, Map.of("tool", request.toolName()));
            return new McpToolInvocationResult(false, server.getId(), request.toolName(), Map.of(), detail, entry.getId());
        }
    }

    private McpToolInvocationResult blocked(InvocationAuditEntity entry, McpToolInvocationRequest request, String detail, InvocationStatus status) {
        audit.finish(entry.getId(), status, detail, Map.of("tool", request.toolName()));
        return new McpToolInvocationResult(false, request.serverId(), request.toolName(), Map.of(), detail, entry.getId());
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> post(URI endpoint, String method, Map<String, Object> params) {
        Map<String, Object> body = Map.of(
                "jsonrpc", "2.0",
                "id", java.util.UUID.randomUUID().toString(),
                "method", method,
                "params", params);
        Map<?, ?> response = client.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .header("MCP-Protocol-Version", McpClientConnectionService.PROTOCOL_VERSION)
                .header("Mcp-Method", method)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (response == null) throw new IllegalStateException("MCP server returned no JSON-RPC response");
        if (response.get("error") != null) throw new IllegalStateException("MCP server returned an error: " + response.get("error"));
        return response;
    }

    private Map<?, ?> findTool(Map<?, ?> response, String toolName) {
        Object resultObject = response.get("result");
        if (!(resultObject instanceof Map<?, ?> result) || !(result.get("tools") instanceof List<?> tools)) {
            throw new IllegalStateException("MCP tools/list response did not include tools");
        }
        for (Object item : tools) {
            if (item instanceof Map<?, ?> tool && toolName.equals(String.valueOf(tool.get("name")))) return tool;
        }
        throw new IllegalArgumentException("MCP tool is not advertised by the server: " + toolName);
    }

    private void validateArguments(Map<?, ?> tool, Map<String, Object> arguments) {
        Object schemaObject = tool.get("inputSchema");
        if (!(schemaObject instanceof Map<?, ?> schema)) return;
        if (schema.get("required") instanceof List<?> required) {
            for (Object key : required) {
                String name = String.valueOf(key);
                if (!arguments.containsKey(name)) throw new IllegalArgumentException("Missing required MCP tool argument: " + name);
            }
        }
        if (!(schema.get("properties") instanceof Map<?, ?> properties)) return;
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            Object propertyObject = properties.get(entry.getKey());
            if (!(propertyObject instanceof Map<?, ?> property)) continue;
            String type = property.get("type") == null ? "" : String.valueOf(property.get("type"));
            if (!matchesType(type, entry.getValue())) throw new IllegalArgumentException("MCP tool argument has invalid type: " + entry.getKey());
        }
    }

    private boolean matchesType(String type, Object value) {
        if (value == null || type.isBlank()) return true;
        return switch (type) {
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "object" -> value instanceof Map<?, ?>;
            case "array" -> value instanceof List<?>;
            default -> true;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readResult(Map<?, ?> response) {
        Object result = response.get("result");
        if (result instanceof Map<?, ?> map) {
            java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>();
            map.forEach((key, value) -> copy.put(String.valueOf(key), value));
            return Map.copyOf(copy);
        }
        return Map.of("value", result == null ? "" : result);
    }

    private URI validateEndpoint(McpServerEntity server) {
        return endpointPolicy.requireAllowed(server.getEndpoint(), server.isLocal());
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank() ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
