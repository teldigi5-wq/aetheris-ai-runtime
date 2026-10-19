package io.aetheris.orchestrator.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Persisted review INTENT only. An intent is never an authorization.
 * The owner-authentication and execution boundary is deliberately absent.
 */
@Entity
@Table(name = "enterprise_design_review_intents")
public class EnterpriseDesignReviewIntent {
    @Id
    @Column(nullable = false, length = 36, updatable = false)
    private String id;

    @Column(nullable = false, length = 80, updatable = false)
    private String projectId;

    @Column(nullable = false, length = 80, updatable = false)
    private String companyId;

    @Column(nullable = false, length = 80, updatable = false)
    private String divisionId;

    @Column(nullable = false, length = 64, updatable = false)
    private String briefDigest;

    @Column(nullable = false, length = 64, updatable = false)
    private String storyboardDigest;

    @Column(nullable = false, length = 64, updatable = false)
    private String manifestDigest;

    @Column(nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(nullable = false, length = 64, updatable = false)
    private String status;

    protected EnterpriseDesignReviewIntent() {}

    EnterpriseDesignReviewIntent(String id, String projectId, String companyId,
            String divisionId, String briefDigest, String storyboardDigest,
            String manifestDigest, Instant issuedAt, Instant expiresAt) {
        this.id = id;
        this.projectId = projectId;
        this.companyId = companyId;
        this.divisionId = divisionId;
        this.briefDigest = briefDigest;
        this.storyboardDigest = storyboardDigest;
        this.manifestDigest = manifestDigest;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.status = "PENDING_OWNER_AUTHENTICATION_UNAVAILABLE";
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getCompanyId() { return companyId; }
    public String getDivisionId() { return divisionId; }
    public String getBriefDigest() { return briefDigest; }
    public String getStoryboardDigest() { return storyboardDigest; }
    public String getManifestDigest() { return manifestDigest; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getStatus() { return status; }
}
