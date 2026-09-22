package io.aetheris.orchestrator.connector.sync;

import io.aetheris.orchestrator.connector.ConnectorProvider;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ProviderReadEndpointRegistry {
    private final Environment environment;

    public ProviderReadEndpointRegistry(Environment environment) {
        this.environment = environment;
    }

    public String collectionUrl(ConnectorProvider provider) {
        return switch (provider) {
            case GITHUB -> value(
                    "AETHERIS_SYNC_GITHUB_REPOS_URL",
                    "https://api.github.com/user/repos?sort=pushed&per_page=20"
            );
            case GMAIL -> value(
                    "AETHERIS_SYNC_GMAIL_MESSAGES_URL",
                    "https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=20&q=newer_than:7d"
            );
            case CALENDAR -> value(
                    "AETHERIS_SYNC_CALENDAR_EVENTS_URL",
                    "https://www.googleapis.com/calendar/v3/calendars/primary/events?singleEvents=true&orderBy=startTime&maxResults=20"
            );
            default -> throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Read-only provider synchronization is not enabled for provider: " + provider
            );
        };
    }

    public String gmailMessageBaseUrl() {
        return value(
                "AETHERIS_SYNC_GMAIL_MESSAGE_BASE_URL",
                "https://gmail.googleapis.com/gmail/v1/users/me/messages/"
        );
    }

    private String value(String key, String fallback) {
        String result = environment.getProperty(key);
        return result == null || result.isBlank() ? fallback : result.trim();
    }
}
