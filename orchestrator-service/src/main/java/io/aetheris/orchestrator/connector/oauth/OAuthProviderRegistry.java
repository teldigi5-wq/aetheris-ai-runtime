package io.aetheris.orchestrator.connector.oauth;

import io.aetheris.orchestrator.connector.ConnectorProvider;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class OAuthProviderRegistry {
    private final Environment environment;

    public OAuthProviderRegistry(Environment environment) {
        this.environment = environment;
    }

    public ProviderOAuthConfig config(ConnectorProvider provider) {
        return switch (provider) {
            case GITHUB -> new ProviderOAuthConfig(
                    provider,
                    required("AETHERIS_OAUTH_GITHUB_CLIENT_ID"),
                    required("AETHERIS_OAUTH_GITHUB_CLIENT_SECRET"),
                    value("AETHERIS_OAUTH_GITHUB_AUTH_URL", "https://github.com/login/oauth/authorize"),
                    value("AETHERIS_OAUTH_GITHUB_TOKEN_URL", "https://github.com/login/oauth/access_token"),
                    value("AETHERIS_OAUTH_GITHUB_USERINFO_URL", "https://api.github.com/user"),
                    githubScopes(),
                    true
            );
            case GMAIL -> google(provider, gmailScopes());
            case CALENDAR -> google(provider, calendarScopes());
            default -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "OAuth live-provider foundation is not enabled for provider: " + provider);
        };
    }

    private List<String> githubScopes() {
        List<String> scopes = new ArrayList<>(List.of("read:user", "user:email"));
        if (writeScopesEnabled()) {
            scopes.add(value("AETHERIS_OAUTH_GITHUB_WRITE_SCOPE", "public_repo"));
        }
        return distinct(scopes);
    }

    private List<String> gmailScopes() {
        List<String> scopes = new ArrayList<>(List.of(
                "openid", "email", "profile", "https://www.googleapis.com/auth/gmail.readonly"));
        if (writeScopesEnabled()) {
            scopes.add("https://www.googleapis.com/auth/gmail.send");
        }
        return distinct(scopes);
    }

    private List<String> calendarScopes() {
        List<String> scopes = new ArrayList<>(List.of(
                "openid", "email", "profile", "https://www.googleapis.com/auth/calendar.readonly"));
        if (writeScopesEnabled()) {
            scopes.add("https://www.googleapis.com/auth/calendar.events");
        }
        return distinct(scopes);
    }

    private boolean writeScopesEnabled() {
        return Boolean.parseBoolean(value("AETHERIS_CONNECTORS_WRITE_SCOPES_ENABLED", "false"));
    }

    private ProviderOAuthConfig google(ConnectorProvider provider, List<String> scopes) {
        return new ProviderOAuthConfig(
                provider,
                required("AETHERIS_OAUTH_GOOGLE_CLIENT_ID"),
                required("AETHERIS_OAUTH_GOOGLE_CLIENT_SECRET"),
                value("AETHERIS_OAUTH_GOOGLE_AUTH_URL", "https://accounts.google.com/o/oauth2/v2/auth"),
                value("AETHERIS_OAUTH_GOOGLE_TOKEN_URL", "https://oauth2.googleapis.com/token"),
                value("AETHERIS_OAUTH_GOOGLE_USERINFO_URL", "https://openidconnect.googleapis.com/v1/userinfo"),
                scopes,
                true
        );
    }

    private static List<String> distinct(List<String> scopes) {
        return List.copyOf(new LinkedHashSet<>(scopes));
    }

    private String required(String key) {
        String result = environment.getProperty(key);
        if (result == null || result.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Provider OAuth runtime configuration is incomplete: " + key);
        }
        return result.trim();
    }

    private String value(String key, String fallback) {
        String result = environment.getProperty(key);
        return result == null || result.isBlank() ? fallback : result.trim();
    }
}
