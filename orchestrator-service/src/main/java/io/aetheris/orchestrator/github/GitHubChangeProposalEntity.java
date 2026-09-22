package io.aetheris.orchestrator.github;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aetheris_github_change_proposals")
public class GitHubChangeProposalEntity {

    @Id private UUID id;
    private UUID taskId;
    @Column(nullable = false, length = 120) private String agentId;
    @Column(nullable = false, length = 240) private String repository;
    @Column(nullable = false, length = 1200) private String path;
    @Column(nullable = false, length = 240) private String baseRef;
    @Column(nullable = false, length = 32000) private String proposedContent;
    @Column(nullable = false, length = 4000) private String summary;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private GitHubProposalStatus status;
    @Column(nullable = false) private Instant createdAt;
    private Instant publishedAt;
    @Column(length = 160) private String publishedCommitSha;
    @Column(length = 2000) private String failureDetail;

    protected GitHubChangeProposalEntity() {}

    public GitHubChangeProposalEntity(UUID id, UUID taskId, String agentId, String repository, String path, String baseRef, String proposedContent, String summary) {
        this.id = id; this.taskId = taskId; this.agentId = agentId; this.repository = repository; this.path = path;
        this.baseRef = baseRef; this.proposedContent = proposedContent; this.summary = summary;
        this.status = GitHubProposalStatus.PROPOSED; this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTaskId() { return taskId; }
    public String getAgentId() { return agentId; }
    public String getRepository() { return repository; }
    public String getPath() { return path; }
    public String getBaseRef() { return baseRef; }
    public String getProposedContent() { return proposedContent; }
    public String getSummary() { return summary; }
    public GitHubProposalStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getPublishedCommitSha() { return publishedCommitSha; }
    public String getFailureDetail() { return failureDetail; }

    public void markPublished(String commitSha) {
        this.status = GitHubProposalStatus.PUBLISHED;
        this.publishedCommitSha = commitSha;
        this.publishedAt = Instant.now();
        this.failureDetail = null;
    }

    public void markFailed(String detail) {
        this.status = GitHubProposalStatus.FAILED;
        this.failureDetail = detail;
    }
}
