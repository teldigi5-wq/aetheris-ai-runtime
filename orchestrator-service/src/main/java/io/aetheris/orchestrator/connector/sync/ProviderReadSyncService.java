package io.aetheris.orchestrator.connector.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aetheris.orchestrator.connector.*;
import io.aetheris.orchestrator.connector.oauth.InMemoryConnectorCredentialVault;
import io.aetheris.orchestrator.connector.oauth.ProviderCredentialEntity;
import io.aetheris.orchestrator.connector.oauth.ProviderCredentialRepository;
import io.aetheris.orchestrator.connector.oauth.ProviderCredentialStatus;
import io.aetheris.orchestrator.executive.ExecutiveSignalType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class ProviderReadSyncService {
    private final ConnectorConnectionRepository connections;
    private final ProviderCredentialRepository credentials;
    private final InMemoryConnectorCredentialVault vault;
    private final ProviderReadEndpointRegistry endpoints;
    private final ConnectorIntegrationService integration;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public ProviderReadSyncService(ConnectorConnectionRepository connections,
                                   ProviderCredentialRepository credentials,
                                   InMemoryConnectorCredentialVault vault,
                                   ProviderReadEndpointRegistry endpoints,
                                   ConnectorIntegrationService integration,
                                   ObjectMapper mapper) {
        this.connections = connections;
        this.credentials = credentials;
        this.vault = vault;
        this.endpoints = endpoints;
        this.integration = integration;
        this.mapper = mapper;
    }

    public ProviderSyncResult sync(UUID connectionId) {
        if (connectionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "connectionId is required");
        }
        ConnectorConnectionEntity connection = connections.findById(connectionId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Connector connection was not found"));
        if (connection.getProvider() != ConnectorProvider.GITHUB
                && connection.getProvider() != ConnectorProvider.GMAIL
                && connection.getProvider() != ConnectorProvider.CALENDAR) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Read-only provider synchronization is not enabled for provider: " + connection.getProvider()
            );
        }
        if (connection.getStatus() != ConnectorStatus.ENABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Connector must be ENABLED before synchronization");
        }

        ProviderCredentialEntity credential = credentials.findByConnectionId(connectionId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider credential was not found"));
        if (credential.getProvider() != connection.getProvider()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Provider credential does not match connector");
        }
        if (credential.getStatus() != ProviderCredentialStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Provider credential is not ACTIVE");
        }
        if (credential.getExpiresAt() != null && Instant.now().isAfter(credential.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Provider access token is expired; refresh is required");
        }

        String accessToken = vault.require(credential.getAccessTokenReference());
        Accumulator accumulator = new Accumulator();
        switch (connection.getProvider()) {
            case GITHUB -> syncGithub(connection, accessToken, accumulator);
            case GMAIL -> syncGmail(connection, accessToken, accumulator);
            case CALENDAR -> syncCalendar(connection, accessToken, accumulator);
            default -> throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Read-only provider synchronization is not enabled for provider: " + connection.getProvider()
            );
        }

        return new ProviderSyncResult(
                connectionId,
                connection.getProvider(),
                accumulator.fetched,
                accumulator.ingested,
                accumulator.duplicates,
                accumulator.failed,
                Instant.now(),
                List.copyOf(accumulator.results),
                List.copyOf(accumulator.errors)
        );
    }

    private void syncGithub(ConnectorConnectionEntity connection, String accessToken, Accumulator accumulator) {
        JsonNode root = getJson(endpoints.collectionUrl(ConnectorProvider.GITHUB), accessToken);
        if (!root.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub repository response was not an array");
        }
        accumulator.fetched += root.size();

        for (JsonNode repository : root) {
            try {
                String providerId = text(repository, "id");
                String pushedAt = text(repository, "pushed_at");
                String fullName = text(repository, "full_name");
                if (providerId.isBlank() || pushedAt.isBlank()) {
                    throw new IllegalArgumentException("GitHub repository item is missing id or pushed_at");
                }
                if (fullName.isBlank()) fullName = text(repository, "name");
                if (fullName.isBlank()) fullName = "repository-" + providerId;
                String description = text(repository, "description");
                String detail = "Repository " + fullName + " reported a read-only update at " + pushedAt;
                if (!description.isBlank()) detail += ". " + description;

                ingest(connection, new ConnectorInboundEvent(
                        connection.getId(),
                        stableExternalId("github", providerId + "|" + pushedAt),
                        ExecutiveSignalType.CODE_UPDATE,
                        "GitHub update: " + fullName,
                        detail,
                        false,
                        true,
                        0,
                        "OBSERVED_READ_ONLY",
                        1,
                        parseInstant(pushedAt, Instant.now())
                ), accumulator);
            } catch (RuntimeException error) {
                fail("GitHub item", error, accumulator);
            }
        }
    }

    private void syncGmail(ConnectorConnectionEntity connection, String accessToken, Accumulator accumulator) {
        JsonNode root = getJson(endpoints.collectionUrl(ConnectorProvider.GMAIL), accessToken);
        JsonNode messages = root.path("messages");
        if (!messages.isMissingNode() && !messages.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gmail message list was not an array");
        }
        if (!messages.isArray()) return;
        accumulator.fetched += messages.size();

        for (JsonNode item : messages) {
            try {
                String messageId = text(item, "id");
                if (messageId.isBlank()) throw new IllegalArgumentException("Gmail message item is missing id");
                String url = appendQuery(
                        endpoints.gmailMessageBaseUrl() + pathSegment(messageId),
                        "format=metadata&metadataHeaders=Subject&metadataHeaders=From&metadataHeaders=Date"
                );
                JsonNode message = getJson(url, accessToken);
                String subject = header(message, "Subject");
                String from = header(message, "From");
                String snippet = text(message, "snippet");
                if (subject.isBlank()) subject = "Inbox message " + messageId;
                String detail = "Read-only Gmail item";
                if (!from.isBlank()) detail += " from " + from;
                if (!snippet.isBlank()) detail += ". " + snippet;

                ingest(connection, new ConnectorInboundEvent(
                        connection.getId(),
                        stableExternalId("gmail", messageId),
                        ExecutiveSignalType.EMAIL,
                        subject,
                        detail,
                        false,
                        true,
                        0,
                        "",
                        0,
                        gmailOccurredAt(message)
                ), accumulator);
            } catch (RuntimeException error) {
                fail("Gmail item", error, accumulator);
            }
        }
    }

    private void syncCalendar(ConnectorConnectionEntity connection, String accessToken, Accumulator accumulator) {
        String url = appendQuery(
                endpoints.collectionUrl(ConnectorProvider.CALENDAR),
                "timeMin=" + encode(Instant.now().toString())
        );
        JsonNode root = getJson(url, accessToken);
        JsonNode items = root.path("items");
        if (!items.isMissingNode() && !items.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Calendar event list was not an array");
        }
        if (!items.isArray()) return;
        accumulator.fetched += items.size();

        for (JsonNode event : items) {
            try {
                if ("cancelled".equalsIgnoreCase(text(event, "status"))) continue;
                String eventId = text(event, "id");
                if (eventId.isBlank()) throw new IllegalArgumentException("Calendar item is missing id");
                String updated = text(event, "updated");
                String summary = text(event, "summary");
                if (summary.isBlank()) summary = "Calendar event " + eventId;
                String start = calendarTime(event.path("start"));
                String end = calendarTime(event.path("end"));
                String detail = "Upcoming read-only calendar item";
                if (!start.isBlank()) detail += " starting " + start;
                if (!end.isBlank()) detail += " and ending " + end;

                ingest(connection, new ConnectorInboundEvent(
                        connection.getId(),
                        stableExternalId("calendar", eventId + "|" + updated),
                        ExecutiveSignalType.CALENDAR_EVENT,
                        summary,
                        detail,
                        false,
                        true,
                        0,
                        "",
                        0,
                        parseCalendarInstant(start, Instant.now())
                ), accumulator);
            } catch (RuntimeException error) {
                fail("Calendar item", error, accumulator);
            }
        }
    }

    private void ingest(ConnectorConnectionEntity connection,
                        ConnectorInboundEvent event,
                        Accumulator accumulator) {
        ConnectorIngestResult result = integration.ingest(event);
        accumulator.results.add(result);
        if (result.duplicate()) accumulator.duplicates++;
        else accumulator.ingested++;
    }

    private JsonNode getJson(String url, String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .header("User-Agent", "Aetheris-Syntra-Connector/1.0")
                .GET()
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Provider read failed with HTTP " + response.statusCode()
            );
        }
        try {
            return mapper.readTree(response.body());
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Provider read returned invalid JSON", error);
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Provider read request failed", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Provider read request was interrupted", error);
        }
    }

    private static String header(JsonNode message, String name) {
        JsonNode headers = message.path("payload").path("headers");
        if (!headers.isArray()) return "";
        for (JsonNode header : headers) {
            if (name.equalsIgnoreCase(text(header, "name"))) return text(header, "value");
        }
        return "";
    }

    private static Instant gmailOccurredAt(JsonNode message) {
        String raw = text(message, "internalDate");
        if (!raw.isBlank()) {
            try {
                return Instant.ofEpochMilli(Long.parseLong(raw));
            } catch (NumberFormatException ignored) {
                // Fall through to the current instant when a provider returns an invalid timestamp.
            }
        }
        return Instant.now();
    }

    private static String calendarTime(JsonNode node) {
        String dateTime = text(node, "dateTime");
        return dateTime.isBlank() ? text(node, "date") : dateTime;
    }

    private static Instant parseCalendarInstant(String value, Instant fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            try {
                return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (RuntimeException ignoredAgain) {
                return fallback;
            }
        }
    }

    private static Instant parseInstant(String value, Instant fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String stableExternalId(String prefix, String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return prefix + "-" + HexFormat.of().formatHex(
                    digest.digest(raw.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static String pathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String appendQuery(String url, String query) {
        return url + (url.contains("?") ? "&" : "?") + query;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }

    private static void fail(String source, RuntimeException error, Accumulator accumulator) {
        accumulator.failed++;
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        if (message.length() > 180) message = message.substring(0, 180);
        accumulator.errors.add(source + ": " + message);
    }

    private static final class Accumulator {
        int fetched;
        int ingested;
        int duplicates;
        int failed;
        final List<ConnectorIngestResult> results = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
    }
}
