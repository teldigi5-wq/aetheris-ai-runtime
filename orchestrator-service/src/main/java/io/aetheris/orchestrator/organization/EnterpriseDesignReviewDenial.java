package io.aetheris.orchestrator.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Immutable append-only denial evidence; not authenticated owner identity. */
@Entity
@Table(name = "enterprise_design_review_denials")
public class EnterpriseDesignReviewDenial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36, updatable = false)
    private String intentId;

    @Column(nullable = false, length = 64, updatable = false)
    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    protected EnterpriseDesignReviewDenial() {}

    EnterpriseDesignReviewDenial(String intentId, String reason, Instant occurredAt) {
        this.intentId = intentId;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public String getIntentId() { return intentId; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
}
