package io.aetheris.orchestrator.connector.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aetheris.orchestrator.connector.oauth.InMemoryConnectorCredentialVault;
import io.aetheris.orchestrator.connector.oauth.ProviderCredentialEntity;
import io.aetheris.orchestrator.connector.oauth.ProviderCredentialRepository;
import io.aetheris.orchestrator.connector.oauth.ProviderCredentialStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class LiveConnectorWriteExecutor {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern GITHUB_REPOSITORY = Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");
    private static final Pattern SAFE_GITHUB_PERMISSION_HEADER = Pattern.compile("^[A-Za-z0-9_=,;:\\- .]{0,256}$");
    private static final String USER_AGENT = "Aetheris-Syntra-Connector/1.0";
    private static final String GITHUB_ACCEPT = "application/vnd.github+json";
    private static final String GITHUB_API_VERSION = "2022-11-28";

    private final ProviderCredentialRepository credentials;
    private final InMemoryConnectorCredentialVault vault;
    private final ProviderAccountContinuityService accountContinuity;
    private final ProviderWriteEndpointRegistry endpoints;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public LiveConnectorWriteExecutor(
            ProviderCredentialRepository credentials,
            InMemoryConnectorCredentialVault vault,
            ProviderAccountContinuityService accountContinuity,
            ProviderWriteEndpointRegistry endpoints,
            ObjectMapper mapper) {
        this.credentials = credentials;
        this.vault = vault;
        this.accountContinuity = accountContinuity;
        this.endpoints = endpoints;
        this.mapper = mapper;
    }

    public String execute(ConnectorActionEntity action) {
        ProviderCredentialEntity credential = credentials.findByConnectionId(action.getConnectionId())
                .orElseThrow(() -> new ConnectorActionBlockedException(
                        "Active OAuth credential is required before live connector writes"));
        if (credential.getProvider() != action.getProvider()) {
            throw new ConnectorActionBlockedException("OAuth credential provider does not match connector action");
        }
        if (credential.getStatus() != ProviderCredentialStatus.ACTIVE) {
            throw new ConnectorActionBlockedException("OAuth credential must be ACTIVE before live connector writes");
        }
        if (credential.getExpiresAt() != null && Instant.now().isAfter(credential.getExpiresAt())) {
            throw new ConnectorActionBlockedException("OAuth access token is expired; refresh is required");
        }
        requireWriteScope(action.getActionKind(), credential.getScopesCsv());
        String accessToken = vault.require(credential.getAccessTokenReference());
        accountContinuity.assertCurrent(action, accessToken);

        return switch (action.getActionKind()) {
            case GMAIL_SEND_EMAIL -> sendGmail(action, accessToken);
            case CALENDAR_CREATE_EVENT -> createCalendarEvent(action, accessToken);
            case GITHUB_CREATE_ISSUE -> createGithubIssue(action, accessToken);
        };
    }

    private String sendGmail(ConnectorActionEntity action, String accessToken) {
        String recipient = action.getTargetRef().trim();
        if (!EMAIL.matcher(recipient).matches()) {
            throw new ConnectorActionBlockedException("Gmail targetRef must be a valid recipient email address");
        }
        String subject = oneLine(action.getSummary());
        if (subject.length() > 160) subject = subject.substring(0, 160);
        String mime = "To: " + recipient + "\r\n"
                + "Subject: " + subject + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Message-ID: <aetheris-" + action.getId() + "@aetheris.invalid>\r\n\r\n"
                + action.getSummary();
        String raw = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mime.getBytes(StandardCharsets.UTF_8));
        JsonNode response = postJson(ProviderKind.GMAIL, endpoints.gmailSendUrl(), accessToken,
                Map.of("raw", raw), Map.of());
        String messageId = text(response, "id");
        if (messageId.isBlank()) {
            throw new ConnectorActionBlockedException("Gmail write response did not include a message id");
        }
        return "gmail:" + messageId;
    }

    private String createCalendarEvent(ConnectorActionEntity action, String accessToken) {
        String[] target = action.getTargetRef().split("\\|", -1);
        if (target.length != 3 || target[0].isBlank() || target[1].isBlank() || target[2].isBlank()) {
            throw new ConnectorActionBlockedException(
                    "Calendar targetRef must be calendarId|start-rfc3339|end-rfc3339");
        }
        OffsetDateTime start;
        OffsetDateTime end;
        try {
            start = OffsetDateTime.parse(target[1].trim());
            end = OffsetDateTime.parse(target[2].trim());
        } catch (RuntimeException error) {
            throw new ConnectorActionBlockedException("Calendar start/end values must be RFC3339 timestamps");
        }
        if (!end.isAfter(start)) {
            throw new ConnectorActionBlockedException("Calendar end time must be after start time");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", action.getId().toString().replace("-", ""));
        payload.put("summary", action.getSummary());
        payload.put("description", "Created by Aetheris after explicit owner approval. Action " + action.getId());
        payload.put("start", Map.of("dateTime", start.toString()));
        payload.put("end", Map.of("dateTime", end.toString()));
        JsonNode response = postJson(ProviderKind.GOOGLE_CALENDAR,
                endpoints.calendarEventsUrl(target[0].trim()), accessToken, payload, Map.of());
        String eventId = text(response, "id");
        if (eventId.isBlank()) {
            throw new ConnectorActionBlockedException("Calendar write response did not include an event id");
        }
        return "calendar:" + eventId;
    }

    private String createGithubIssue(ConnectorActionEntity action, String accessToken) {
        String repository = action.getTargetRef().trim();
        if (!GITHUB_REPOSITORY.matcher(repository).matches()) {
            throw new ConnectorActionBlockedException("GitHub targetRef must be owner/repository");
        }
        String title = oneLine(action.getSummary());
        if (title.length() > 240) title = title.substring(0, 240);
        String body = action.getSummary() + "\n\n<!-- aetheris-action:" + action.getId() + " -->";
        JsonNode response = postJson(ProviderKind.GITHUB, endpoints.githubIssuesUrl(repository), accessToken,
                Map.of("title", title, "body", body),
                Map.of("Accept", GITHUB_ACCEPT, "X-GitHub-Api-Version", GITHUB_API_VERSION));
        String htmlUrl = text(response, "html_url");
        if (!htmlUrl.isBlank()) return htmlUrl;
        String number = text(response, "number");
        if (number.isBlank()) {
            throw new ConnectorActionBlockedException("GitHub write response did not include an issue reference");
        }
        return "github:issue:" + number;
    }

    private JsonNode postJson(ProviderKind provider, String url, String accessToken,
                              Object payload, Map<String, String> extraHeaders) {
        try {
            String json = mapper.writeValueAsString(payload);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("User-Agent", USER_AGENT);
            extraHeaders.forEach(builder::setHeader);
            HttpResponse<String> response = http.send(
                    builder.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                String acceptedPermissions = provider == ProviderKind.GITHUB
                        ? safeGithubPermissionHint(response) : null;
                throw new LiveProviderWriteException(status, acceptedPermissions);
            }
            if (response.body() == null || response.body().isBlank()) return mapper.createObjectNode();
            return mapper.readTree(response.body());
        } catch (LiveProviderWriteException | ConnectorActionBlockedException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ConnectorActionBlockedException("Live provider write was interrupted");
        } catch (IOException | IllegalArgumentException error) {
            throw new ConnectorActionBlockedException("Live provider write could not be completed");
        }
    }

    private static String safeGithubPermissionHint(HttpResponse<?> response) {
        String value = response.headers().firstValue("X-Accepted-GitHub-Permissions").orElse("").trim();
        if (value.isBlank() || value.length() > 256) return null;
        if (!SAFE_GITHUB_PERMISSION_HEADER.matcher(value).matches()) return null;
        return value;
    }

    private static void requireWriteScope(ConnectorActionKind kind, String scopesCsv) {
        Set<String> scopes = scopesCsv == null ? Set.of() : Arrays.stream(scopesCsv.split("[,\\s]+"))
                .map(String::trim).filter(value -> !value.isBlank()).collect(Collectors.toSet());
        boolean allowed = switch (kind) {
            case GMAIL_SEND_EMAIL -> scopes.contains("https://www.googleapis.com/auth/gmail.send")
                    || scopes.contains("https://mail.google.com/");
            case CALENDAR_CREATE_EVENT -> scopes.contains("https://www.googleapis.com/auth/calendar.events")
                    || scopes.contains("https://www.googleapis.com/auth/calendar");
            case GITHUB_CREATE_ISSUE -> scopes.contains("repo")
                    || scopes.contains("public_repo") || scopes.contains("issues:write");
        };
        if (!allowed) {
            throw new ConnectorActionBlockedException(
                    "OAuth credential does not include the required provider write scope for " + kind);
        }
    }

    private static String oneLine(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) return "";
        return value.isTextual() ? value.asText().trim() : value.asText("").trim();
    }

    private enum ProviderKind { GITHUB, GMAIL, GOOGLE_CALENDAR }
}
