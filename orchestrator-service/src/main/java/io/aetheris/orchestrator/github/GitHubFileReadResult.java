package io.aetheris.orchestrator.github;

public record GitHubFileReadResult(
        String repository,
        String path,
        String ref,
        String sha,
        String content,
        String htmlUrl
) {
}
