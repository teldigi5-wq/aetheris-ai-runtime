package io.aetheris.orchestrator.tool;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.policy.CompiledPolicyDecision;
import io.aetheris.orchestrator.policy.OwnerRuleCompilerService;
import io.aetheris.orchestrator.policy.PolicyEvaluationRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SafeToolRegistryService {

    private final List<ToolDescriptor> tools;
    private final Map<String, ToolDescriptor> byId;
    private final SpecialistToolAuthorizationService specialistTools;
    private final OwnerRuleCompilerService policy;

    public SafeToolRegistryService(
            SpecialistToolAuthorizationService specialistTools,
            OwnerRuleCompilerService policy) {
        this.specialistTools = specialistTools;
        this.policy = policy;
        this.tools = List.of(
                new ToolDescriptor("github.read", "GitHub Read", ToolTransport.OFFICIAL_API, Set.of("repo:read"), RiskLevel.LOW, true, true),
                new ToolDescriptor("github.propose-change", "GitHub Proposed Change", ToolTransport.OFFICIAL_API, Set.of("repo:write-draft"), RiskLevel.HIGH, false, true),
                new ToolDescriptor("files.read-workspace", "Workspace Files Read", ToolTransport.FILESYSTEM, Set.of("workspace:read"), RiskLevel.LOW, true, false),
                new ToolDescriptor("files.write-workspace", "Workspace Files Write", ToolTransport.FILESYSTEM, Set.of("workspace:write"), RiskLevel.MEDIUM, false, false),
                new ToolDescriptor("terminal.inspect", "Terminal Inspect", ToolTransport.CLI, Set.of("process:read", "workspace:read"), RiskLevel.MEDIUM, true, false),
                new ToolDescriptor("terminal.execute-workspace", "Terminal Workspace Execute", ToolTransport.CLI, Set.of("workspace:execute"), RiskLevel.HIGH, false, false)
        );
        this.byId = tools.stream().collect(Collectors.toUnmodifiableMap(
                ToolDescriptor::id, Function.identity()));
    }

    public List<ToolDescriptor> list() {
        return tools;
    }

    public ToolDescriptor getRequired(String id) {
        ToolDescriptor descriptor = byId.get(id);
        if (descriptor == null) throw new NoSuchElementException("Unknown tool: " + id);
        return descriptor;
    }

    public ToolAccessDecision evaluate(ToolAccessRequest request) {
        ToolDescriptor tool = getRequired(request.toolId());
        SpecialistToolAuthorizationService.Decision specialistDecision =
                specialistTools.evaluate(request.agentId(), tool);

        Map<String, String> metadata = new LinkedHashMap<>(request.metadata());
        metadata.put("toolId", tool.id());
        metadata.put("transport", tool.transport().name());
        metadata.put("readOnly", Boolean.toString(tool.readOnly()));
        metadata.put("specialistAllowed", Boolean.toString(specialistDecision.allowed()));
        if (specialistDecision.toolFamily() != null) {
            metadata.put("specialistToolFamily", specialistDecision.toolFamily());
        }

        if (!specialistDecision.allowed()) {
            return new ToolAccessDecision(tool, new CompiledPolicyDecision(
                    false,
                    false,
                    false,
                    false,
                    List.of(specialistDecision.reason()),
                    List.of()));
        }

        return new ToolAccessDecision(tool, policy.evaluate(new PolicyEvaluationRequest(
                request.agentId(),
                "tool:" + tool.id(),
                "tool",
                tool.riskLevel(),
                request.billable(),
                request.sendsDataOffDevice(),
                request.mode(),
                metadata)));
    }
}
