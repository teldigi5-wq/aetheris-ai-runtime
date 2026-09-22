package io.aetheris.orchestrator.connector.action;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class ProviderWriteEndpointRegistry {
    private final Environment environment;

    public ProviderWriteEndpointRegistry(Environment environment) {
        this.environment = environment;
    }

    public String gmailSendUrl() {
        return value(
                "AETHERIS_WRITE_GMAIL_SEND_URL",
                "https://gmail.googleapis.com/gmail/v1/users/me/messages/send"
        );
    }

    public String calendarEventsUrl(String calendarId) {
        String template = value(
                "AETHERIS_WRITE_CALENDAR_EVENTS_URL_TEMPLATE",
                "https://www.googleapis.com/calendar/v3/calendars/{calendarId}/events"
        );
        return template.replace("{calendarId}", pathSegment(calendarId));
    }

    public String githubIssuesUrl(String repository) {
        String[] parts = repository.split("/", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new ConnectorActionBlockedException("GitHub targetRef must be owner/repository");
        }
        String encodedRepository = pathSegment(parts[0]) + "/" + pathSegment(parts[1]);
        String template = value(
                "AETHERIS_WRITE_GITHUB_ISSUES_URL_TEMPLATE",
                "https://api.github.com/repos/{repository}/issues"
        );
        return template
                .replace("{repository}", encodedRepository)
                .replace("{owner}", pathSegment(parts[0]))
                .replace("{repo}", pathSegment(parts[1]));
    }

    private String value(String key, String fallback) {
        String result = environment.getProperty(key);
        return result == null || result.isBlank() ? fallback : result.trim();
    }

    private static String pathSegment(String value) {
        if (value == null || value.isBlank()) {
            throw new ConnectorActionBlockedException("Provider write target is required");
        }
        return URLEncoder.encode(value.trim(), StandardCharsets.UTF_8).replace("+", "%20");
    }
}
