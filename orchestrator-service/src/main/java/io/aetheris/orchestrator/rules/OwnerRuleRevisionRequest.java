package io.aetheris.orchestrator.rules;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OwnerRuleRevisionRequest(
        @NotBlank String ruleKey,
        @NotBlank String description,
        @NotBlank String scope,
        @NotBlank String conditionExpression,
        @NotNull RuleEffect effect,
        int priority,
        boolean enabled
) {
}
