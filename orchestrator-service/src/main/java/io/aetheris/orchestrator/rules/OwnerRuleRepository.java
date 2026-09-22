package io.aetheris.orchestrator.rules;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OwnerRuleRepository extends JpaRepository<OwnerRuleEntity, UUID> {
    Optional<OwnerRuleEntity> findTopByRuleKeyOrderByVersionNumberDesc(String ruleKey);
    List<OwnerRuleEntity> findTop100ByEnabledTrueOrderByPriorityDescCreatedAtDesc();
    List<OwnerRuleEntity> findTop100ByRuleKeyOrderByVersionNumberDesc(String ruleKey);
}
