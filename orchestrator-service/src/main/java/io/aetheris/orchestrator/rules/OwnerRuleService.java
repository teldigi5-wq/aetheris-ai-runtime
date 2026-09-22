package io.aetheris.orchestrator.rules;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class OwnerRuleService {

    private final OwnerRuleRepository repository;

    public OwnerRuleService(OwnerRuleRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public OwnerRuleEntity createRevision(OwnerRuleRevisionRequest request) {
        String key = normalizeKey(request.ruleKey());
        int nextVersion = repository.findTopByRuleKeyOrderByVersionNumberDesc(key)
                .map(previous -> {
                    previous.deactivate();
                    repository.save(previous);
                    return previous.getVersionNumber() + 1;
                })
                .orElse(1);

        OwnerRuleEntity revision = new OwnerRuleEntity(
                UUID.randomUUID(),
                key,
                request.description().trim(),
                request.scope().trim(),
                request.conditionExpression().trim(),
                request.effect(),
                request.priority(),
                request.enabled(),
                nextVersion);
        return repository.save(revision);
    }

    public List<OwnerRuleEntity> activeRules() {
        return repository.findTop100ByEnabledTrueOrderByPriorityDescCreatedAtDesc();
    }

    public List<OwnerRuleEntity> history(String ruleKey) {
        return repository.findTop100ByRuleKeyOrderByVersionNumberDesc(normalizeKey(ruleKey));
    }

    private String normalizeKey(String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("-+", "-");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Rule key must contain at least one letter or number");
        }
        return normalized;
    }
}
