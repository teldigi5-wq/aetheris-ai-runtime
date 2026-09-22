package io.aetheris.orchestrator.github;

import java.util.UUID;

public record GitHubPublishResult(
        UUID proposalId,
        boolean published,
        String commitSha,
        String detail
) {
}
