package io.aetheris.orchestrator.github;

import io.aetheris.orchestrator.vault.ScopedCredentialAccessService;
import io.aetheris.orchestrator.vault.SecretAccessRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.aetheris.orchestrator.vault.SecretAccessRequest.Caller.GITHUB_ADAPTER;
import static io.aetheris.orchestrator.vault.SecretAccessRequest.Purpose.GITHUB_PUBLISH;
import static io.aetheris.orchestrator.vault.SecretAccessRequest.Purpose.GITHUB_READ;

@Service
public class GitHubAdapterService {

    private final GitHubChangeProposalRepository proposals;
    private final RestClient client;
    private final ScopedCredentialAccessService secretAccess;
    private final String credentialAlias;
    private final Set<String> allowedRepositories;

    public GitHubAdapterService(
            GitHubChangeProposalRepository proposals,
            RestClient.Builder builder,
            ScopedCredentialAccessService secretAccess,
            @Value("${aetheris.execution.github.credential-alias:github-api-token}") String credentialAlias,
            @Value("${aetheris.execution.github.allowed-repositories:}") String allowedRepositories) {
        this.proposals = proposals;
        this.client = builder.baseUrl("https://api.github.com").build();
        this.secretAccess = secretAccess;
        this.credentialAlias = credentialAlias.trim();
        this.allowedRepositories = parseCsv(allowedRepositories);
    }

    @SuppressWarnings("unchecked")
    public GitHubFileReadResult readFile(String repository, String path, String ref) {
        String normalizedRepo = requireAllowed(repository);
        String[] parts = normalizedRepo.split("/", 2);
        if (path == null || path.isBlank()) throw new IllegalArgumentException("GitHub path is required");
        String effectiveRef = ref == null || ref.isBlank() ? "main" : ref.trim();
        char[] secret = secretAccess.resolve(new SecretAccessRequest(GITHUB_ADAPTER, GITHUB_READ, credentialAlias)).orElse(null);
        try {
            RestClient.RequestHeadersSpec<?> request = client.get()
                    .uri(uriBuilder -> uriBuilder.path("/repos/{owner}/{repo}/contents/{path}")
                            .queryParam("ref", effectiveRef).build(parts[0], parts[1], path));
            request.header(HttpHeaders.ACCEPT, "application/vnd.github+json");
            request.header(HttpHeaders.USER_AGENT, "aetheris-platform");
            if (secret != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + new String(secret));
            Map<String, Object> body = request.retrieve().body(Map.class);
            if (body == null || body.get("content") == null) throw new IllegalStateException("GitHub returned no file content");
            String content = new String(Base64.getMimeDecoder().decode(String.valueOf(body.get("content"))), StandardCharsets.UTF_8);
            return new GitHubFileReadResult(normalizedRepo, path, effectiveRef,
                    String.valueOf(body.getOrDefault("sha", "")), content,
                    body.get("html_url") == null ? null : String.valueOf(body.get("html_url")));
        } finally {
            if (secret != null) Arrays.fill(secret, '\0');
        }
    }

    public GitHubChangeProposalEntity propose(UUID taskId, String agentId, String repository, String path, String baseRef, String content, String summary) {
        String normalizedRepo = requireAllowed(repository);
        if (path == null || path.isBlank()) throw new IllegalArgumentException("GitHub proposal path is required");
        String effectiveRef = baseRef == null || baseRef.isBlank() ? "main" : baseRef.trim();
        String effectiveSummary = summary == null || summary.isBlank() ? "Aetheris proposed change" : summary.trim();
        return proposals.save(new GitHubChangeProposalEntity(UUID.randomUUID(), taskId, agentId, normalizedRepo, path.trim(), effectiveRef,
                content == null ? "" : content, effectiveSummary));
    }

    @SuppressWarnings("unchecked")
    public String publishExistingFile(GitHubChangeProposalEntity proposal) {
        if (proposal.getStatus() != GitHubProposalStatus.PROPOSED) throw new IllegalStateException("Only PROPOSED GitHub changes can be published");
        String normalizedRepo = requireAllowed(proposal.getRepository());
        GitHubFileReadResult current = readFile(normalizedRepo, proposal.getPath(), proposal.getBaseRef());
        if (current.sha() == null || current.sha().isBlank()) throw new IllegalStateException("Existing GitHub file SHA is required for guarded publish");
        char[] secret = secretAccess.resolve(new SecretAccessRequest(GITHUB_ADAPTER, GITHUB_PUBLISH, credentialAlias))
                .orElseThrow(() -> new IllegalStateException("GitHub credential is unavailable"));
        try {
            String[] parts = normalizedRepo.split("/", 2);
            Map<String, Object> body = Map.of(
                    "message", proposal.getSummary(),
                    "content", Base64.getEncoder().encodeToString(proposal.getProposedContent().getBytes(StandardCharsets.UTF_8)),
                    "sha", current.sha(),
                    "branch", proposal.getBaseRef());
            Map<String, Object> response = client.put()
                    .uri(uriBuilder -> uriBuilder.path("/repos/{owner}/{repo}/contents/{path}").build(parts[0], parts[1], proposal.getPath()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header(HttpHeaders.USER_AGENT, "aetheris-platform")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + new String(secret))
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (response == null || !(response.get("commit") instanceof Map<?, ?> commit) || commit.get("sha") == null) {
                throw new IllegalStateException("GitHub publish returned no commit SHA");
            }
            return String.valueOf(commit.get("sha"));
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    public List<GitHubChangeProposalEntity> recentProposals() { return proposals.findTop100ByOrderByCreatedAtDesc(); }

    private String requireAllowed(String repository) {
        if (repository == null || !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("GitHub repository must be in owner/name format");
        String normalized = repository.toLowerCase(Locale.ROOT);
        if (allowedRepositories.isEmpty() || !allowedRepositories.contains(normalized)) throw new IllegalArgumentException("GitHub repository is not allowlisted: " + repository);
        return normalized;
    }

    private Set<String> parseCsv(String csv) {
        Set<String> values = new HashSet<>();
        if (csv == null) return Set.of();
        for (String value : csv.split(",")) if (!value.isBlank()) values.add(value.trim().toLowerCase(Locale.ROOT));
        return Set.copyOf(values);
    }
}
