package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.policy.ActionRequest;
import io.aetheris.orchestrator.policy.CompiledPolicyDecision;
import io.aetheris.orchestrator.policy.OwnerPolicyService;
import io.aetheris.orchestrator.policy.OwnerRuleCompilerService;
import io.aetheris.orchestrator.policy.PolicyDecision;
import io.aetheris.orchestrator.policy.PolicyEvaluationRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orchestrator/policy")
public class PolicyController {

    private final OwnerPolicyService ownerPolicyService;
    private final OwnerRuleCompilerService compiler;

    public PolicyController(OwnerPolicyService ownerPolicyService, OwnerRuleCompilerService compiler) {
        this.ownerPolicyService = ownerPolicyService;
        this.compiler = compiler;
    }

    @PostMapping("/evaluate")
    public PolicyDecision evaluate(@RequestBody ActionRequest request) {
        return ownerPolicyService.evaluate(request);
    }

    @PostMapping("/compile")
    public CompiledPolicyDecision compile(@RequestBody PolicyEvaluationRequest request) {
        return compiler.evaluate(request);
    }
}
