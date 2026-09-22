package io.aetheris.orchestrator.connector.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aetheris.orchestrator.connector.ConnectorProvider;
import io.aetheris.orchestrator.connector.action.ConnectorActionBlockedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class ProviderIdentityResolver {
    private static final String USER_AGENT = "Aetheris-Syntra-Connector/1.0";
    private static final String GITHUB_ACCEPT = "application/vnd.github+json";

    private final OAuthProviderRegistry providers;
    private final ObjectMapper mapper;
    private final HttpClient http;

    @Autowired
    public ProviderIdentityResolver(OAuthProviderRegistry providers, ObjectMapper mapper) {
        this(providers, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
    }

    ProviderIdentityResolver(OAuthProviderRegistry providers, ObjectMapper mapper, HttpClient http) {
        this.providers = providers;
        this.mapper = mapper;
        this.http = http;
    }

    public String resolve(ConnectorProvider provider, String accessToken) {
        if (provider == null || accessToken == null || accessToken.isBlank()) throw blocked();
        try {
            ProviderOAuthConfig config = providers.config(provider);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.userInfoEndpoint()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .GET();
            if (provider == ConnectorProvider.GITHUB) {
                builder.setHeader("Accept", GITHUB_ACCEPT);
                builder.header("X-GitHub-Api-Version", "2022-11-28");
            }
            HttpResponse<String> response = http.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300
                    || response.body() == null || response.body().isBlank()) throw blocked();
            JsonNode body = mapper.readTree(response.body());
            JsonNode identity = switch (provider) {
                case GITHUB -> body.get("id");
                case GMAIL, CALENDAR -> body.get("sub");
                default -> null;
            };
            String stableId = identity == null || identity.isNull() ? "" : identity.asText("").trim();
            if (stableId.isBlank()) throw blocked();
            return stableId;
        } catch (ConnectorActionBlockedException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw blocked();
        } catch (IOException | RuntimeException error) {
            throw blocked();
        }
    }

    private ConnectorActionBlockedException blocked() {
        return new ConnectorActionBlockedException(
                "Current provider account identity could not be verified before live connector execution");
    }
}
