package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.rules.OwnerRuleEntity;
import io.aetheris.orchestrator.rules.OwnerRuleRevisionRequest;
import io.aetheris.orchestrator.rules.OwnerRuleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orchestrator/rules")
public class OwnerRuleController {

    private final OwnerRuleService rules;

    public OwnerRuleController(OwnerRuleService rules) {
        this.rules = rules;
    }

    @PostMapping("/revisions")
    public OwnerRuleEntity createRevision(@Valid @RequestBody OwnerRuleRevisionRequest request) {
        return rules.createRevision(request);
    }

    @GetMapping("/active")
    public List<OwnerRuleEntity> active() {
        return rules.activeRules();
    }

    @GetMapping("/{ruleKey}/history")
    public List<OwnerRuleEntity> history(@PathVariable String ruleKey) {
        return rules.history(ruleKey);
    }
}
