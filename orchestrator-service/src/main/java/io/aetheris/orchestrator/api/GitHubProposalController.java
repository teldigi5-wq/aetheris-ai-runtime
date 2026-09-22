package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.approval.ApprovalEntity;
import io.aetheris.orchestrator.github.GitHubAdapterService;
import io.aetheris.orchestrator.github.GitHubChangeProposalEntity;
import io.aetheris.orchestrator.github.GitHubProposalPublishService;
import io.aetheris.orchestrator.github.GitHubPublishResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/github/proposals")
public class GitHubProposalController {

    private final GitHubAdapterService github;
    private final GitHubProposalPublishService publisher;

    public GitHubProposalController(GitHubAdapterService github, GitHubProposalPublishService publisher) {
        this.github = github;
        this.publisher = publisher;
    }

    @GetMapping
    public List<GitHubChangeProposalEntity> recent() { return github.recentProposals(); }

    @PostMapping("/{id}/request-publish-approval")
    public ApprovalEntity requestPublishApproval(@PathVariable UUID id) { return publisher.requestApproval(id); }

    @PostMapping("/{id}/publish")
    public GitHubPublishResult publish(@PathVariable UUID id) { return publisher.publish(id); }
}
