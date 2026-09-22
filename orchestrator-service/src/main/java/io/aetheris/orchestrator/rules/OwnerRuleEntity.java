package io.aetheris.orchestrator.rules;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aetheris_owner_rules")
public class OwnerRuleEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String ruleKey;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false, length = 120)
    private String scope;

    @Column(nullable = false, length = 2000)
    private String conditionExpression;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RuleEffect effect;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private int versionNumber;

    @Column(nullable = false)
    private Instant createdAt;

    protected OwnerRuleEntity() {
    }

    public OwnerRuleEntity(
            UUID id,
            String ruleKey,
            String description,
            String scope,
            String conditionExpression,
            RuleEffect effect,
            int priority,
            boolean enabled,
            int versionNumber) {
        this.id = id;
        this.ruleKey = ruleKey;
        this.description = description;
        this.scope = scope;
        this.conditionExpression = conditionExpression;
        this.effect = effect;
        this.priority = priority;
        this.enabled = enabled;
        this.versionNumber = versionNumber;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getRuleKey() { return ruleKey; }
    public String getDescription() { return description; }
    public String getScope() { return scope; }
    public String getConditionExpression() { return conditionExpression; }
    public RuleEffect getEffect() { return effect; }
    public int getPriority() { return priority; }
    public boolean isEnabled() { return enabled; }
    public int getVersionNumber() { return versionNumber; }
    public Instant getCreatedAt() { return createdAt; }

    public void deactivate() {
        this.enabled = false;
    }
}
