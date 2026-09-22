package io.aetheris.orchestrator.connector.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aetheris.orchestrator.connector.*;
import io.aetheris.orchestrator.executive.ExecutiveSignalType;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class VerifiedWebhookAdapterService {
    private static final Pattern SECRET_REF = Pattern.compile("[A-Z0-9][A-Z0-9_]{2,79}");
    private static final int DEFAULT_CLOCK_SKEW_SECONDS = 300;
    private static final int MAX_BODY_BYTES = 1_048_576;

    private final ConnectorConnectionRepository connections;
    private final ConnectorAdapterConfigRepository configs;
    private final ConnectorIntegrationService integration;
    private final EnvironmentConnectorSecretResolver secrets;
    private final ObjectMapper mapper;

    public VerifiedWebhookAdapterService(ConnectorConnectionRepository connections,
                                         ConnectorAdapterConfigRepository configs,
                                         ConnectorIntegrationService integration,
                                         EnvironmentConnectorSecretResolver secrets,
                                         ObjectMapper mapper) {
        this.connections = connections;
        this.configs = configs;
        this.integration = integration;
        this.secrets = secrets;
        this.mapper = mapper;
    }

    @Transactional
    public ConnectorAdapterView configure(UUID connectionId, ConfigureConnectorAdapterRequest request) {
        if (connectionId == null) throw badRequest("connectionId is required");
        if (request == null || request.adapterType() == null) throw badRequest("adapterType is required");
        String secretReference = text(request.secretReference());
        if (!SECRET_REF.matcher(secretReference).matches()) {
            throw badRequest("secretReference must contain only uppercase letters, digits and underscores");
        }
        int clockSkew = request.maxClockSkewSeconds() == null
                ? DEFAULT_CLOCK_SKEW_SECONDS : request.maxClockSkewSeconds();
        if (clockSkew < 30 || clockSkew > 900) {
            throw badRequest("maxClockSkewSeconds must be between 30 and 900");
        }

        ConnectorConnectionEntity connection = connection(connectionId);
        assertProviderMatches(connection, request.adapterType());

        ConnectorAdapterConfigEntity config = configs.findByConnectionId(connectionId).orElse(null);
        if (config == null) {
            config = new ConnectorAdapterConfigEntity(
                    UUID.randomUUID(), connectionId, request.adapterType(), secretReference, clockSkew);
        } else {
            config.reconfigure(request.adapterType(), secretReference, clockSkew);
        }
        return view(configs.save(config));
    }

    public ConnectorAdapterView adapter(UUID connectionId) {
        return view(requireConfig(connectionId));
    }

    public ConnectorIngestResult ingestGeneric(UUID connectionId, String externalEventId,
                                                String timestampHeader, String signatureHeader,
                                                byte[] rawBody) {
        ConnectorAdapterConfigEntity config = requireConfig(connectionId, ConnectorAdapterType.GENERIC_HMAC_SHA256);
        requireBody(rawBody);
        String eventId = requiredHeader(externalEventId, "X-Aetheris-Event-Id");
        String timestampText = requiredHeader(timestampHeader, "X-Aetheris-Timestamp");
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampText);
        } catch (NumberFormatException error) {
            throw unauthorized("Webhook timestamp is invalid");
        }

        long now = Instant.now().getEpochSecond();
        long skew = config.getMaxClockSkewSeconds();
        if (timestamp < now - skew || timestamp > now + skew) {
            throw unauthorized("Webhook timestamp is outside the allowed freshness window");
        }

        byte[] secret = secrets.resolve(config.getSecretReference());
        byte[] signedPayload = join(timestampText, rawBody);
        verifySignature(signatureHeader, secret, signedPayload);

        GenericSignedWebhookPayload payload;
        try {
            payload = mapper.readValue(rawBody, GenericSignedWebhookPayload.class);
        } catch (IOException error) {
            throw badRequest("Webhook JSON payload is invalid");
        }
        if (payload.type() == null) throw badRequest("Webhook payload type is required");

        return integration.ingest(new ConnectorInboundEvent(
                connectionId,
                eventId,
                payload.type(),
                limit(payload.subject(), 600),
                limit(payload.detail(), 1800),
                payload.trustedLowRisk(),
                true,
                Math.max(0, payload.signups()),
                text(payload.verificationStatus()),
                Math.max(0, payload.verificationRuns()),
                payload.occurredAt()
        ));
    }

    public ConnectorIngestResult ingestGitHub(UUID connectionId, String deliveryId,
                                               String eventName, String signatureHeader,
                                               byte[] rawBody) {
        ConnectorAdapterConfigEntity config = requireConfig(connectionId, ConnectorAdapterType.GITHUB_HMAC_SHA256);
        requireBody(rawBody);
        String delivery = requiredHeader(deliveryId, "X-GitHub-Delivery");
        String event = requiredHeader(eventName, "X-GitHub-Event").toLowerCase(Locale.ROOT);
        byte[] secret = secrets.resolve(config.getSecretReference());
        verifySignature(signatureHeader, secret, rawBody);

        JsonNode root;
        try {
            root = mapper.readTree(rawBody);
        } catch (IOException error) {
            throw badRequest("GitHub webhook JSON payload is invalid");
        }

        ConnectorInboundEvent normalized = normalizeGitHub(connectionId, delivery, event, root);
        return integration.ingest(normalized);
    }

    private ConnectorInboundEvent normalizeGitHub(UUID connectionId, String delivery, String event, JsonNode root) {
        String externalEventId = "github:" + delivery;
        return switch (event) {
            case "push" -> new ConnectorInboundEvent(
                    connectionId,
                    externalEventId,
                    ExecutiveSignalType.CODE_UPDATE,
                    limit("GitHub push to " + nodeText(root, "ref", "unknown ref"), 600),
                    limit(nodeText(root.path("head_commit"), "message", "GitHub push received"), 1800),
                    false,
                    true,
                    0,
                    "PENDING",
                    0,
                    parseInstant(nodeText(root.path("head_commit"), "timestamp", ""))
            );
            case "workflow_run" -> {
                JsonNode run = root.path("workflow_run");
                String conclusion = nodeText(run, "conclusion", "unknown");
                yield new ConnectorInboundEvent(
                        connectionId,
                        externalEventId,
                        ExecutiveSignalType.CODE_UPDATE,
                        limit("GitHub workflow: " + nodeText(run, "name", "workflow"), 600),
                        limit("status=" + nodeText(run, "status", "unknown") + ", conclusion=" + conclusion, 1800),
                        false,
                        true,
                        0,
                        "success".equalsIgnoreCase(conclusion) ? "PASS" : "PENDING",
                        1,
                        parseInstant(nodeText(run, "updated_at", ""))
                );
            }
            case "issues" -> {
                JsonNode issue = root.path("issue");
                yield new ConnectorInboundEvent(
                        connectionId, externalEventId, ExecutiveSignalType.DM,
                        limit(nodeText(issue, "title", "GitHub issue"), 600),
                        limit(nodeText(issue, "body", "GitHub issue event"), 1800),
                        false, true, 0, "", 0,
                        parseInstant(nodeText(issue, "updated_at", ""))
                );
            }
            case "issue_comment" -> {
                JsonNode issue = root.path("issue");
                JsonNode comment = root.path("comment");
                yield new ConnectorInboundEvent(
                        connectionId, externalEventId, ExecutiveSignalType.DM,
                        limit(nodeText(issue, "title", "GitHub issue comment"), 600),
                        limit(nodeText(comment, "body", "GitHub issue comment event"), 1800),
                        false, true, 0, "", 0,
                        parseInstant(nodeText(comment, "updated_at", ""))
                );
            }
            default -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Unsupported GitHub webhook event: " + event);
        };
    }

    private ConnectorAdapterConfigEntity requireConfig(UUID connectionId) {
        if (connectionId == null) throw badRequest("connectionId is required");
        return configs.findByConnectionId(connectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Connector adapter configuration was not found"));
    }

    private ConnectorAdapterConfigEntity requireConfig(UUID connectionId, ConnectorAdapterType expected) {
        ConnectorAdapterConfigEntity config = requireConfig(connectionId);
        if (config.getAdapterType() != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Connector adapter type does not match this endpoint");
        }
        ConnectorConnectionEntity connection = connection(connectionId);
        assertProviderMatches(connection, expected);
        return config;
    }

    private ConnectorConnectionEntity connection(UUID connectionId) {
        return connections.findById(connectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connector connection was not found"));
    }

    private static void assertProviderMatches(ConnectorConnectionEntity connection, ConnectorAdapterType type) {
        boolean valid = switch (type) {
            case GENERIC_HMAC_SHA256 -> connection.getProvider() == ConnectorProvider.GENERIC_WEBHOOK;
            case GITHUB_HMAC_SHA256 -> connection.getProvider() == ConnectorProvider.GITHUB;
        };
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Connector provider does not match the configured adapter type");
        }
    }

    private static void verifySignature(String signatureHeader, byte[] secret, byte[] signedPayload) {
        String signature = requiredHeader(signatureHeader, "signature");
        if (!signature.startsWith("sha256=")) throw unauthorized("Webhook signature format is invalid");
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(signature.substring("sha256=".length()));
        } catch (IllegalArgumentException error) {
            throw unauthorized("Webhook signature format is invalid");
        }
        byte[] expected;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            expected = mac.doFinal(signedPayload);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", error);
        }
        if (!MessageDigest.isEqual(expected, provided)) {
            throw unauthorized("Webhook signature verification failed");
        }
    }

    private static byte[] join(String timestamp, byte[] body) {
        byte[] prefix = (timestamp + ".").getBytes(StandardCharsets.UTF_8);
        byte[] signed = Arrays.copyOf(prefix, prefix.length + body.length);
        System.arraycopy(body, 0, signed, prefix.length, body.length);
        return signed;
    }

    private static void requireBody(byte[] body) {
        if (body == null || body.length == 0) throw badRequest("Webhook body is required");
        if (body.length > MAX_BODY_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Webhook body exceeds the 1 MiB limit");
        }
    }

    private static String requiredHeader(String value, String name) {
        String normalized = text(value);
        if (normalized.isBlank()) throw badRequest(name + " header is required");
        return normalized;
    }

    private ConnectorAdapterView view(ConnectorAdapterConfigEntity config) {
        return new ConnectorAdapterView(
                config.getConnectionId(), config.getAdapterType(), true,
                config.getMaxClockSkewSeconds(), config.getCreatedAt(), config.getUpdatedAt());
    }

    private static String nodeText(JsonNode node, String field, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) return fallback;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return fallback;
        String text = value.asText("").trim();
        return text.isBlank() ? fallback : text;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String limit(String value, int max) {
        String normalized = text(value);
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private static ResponseStatusException unauthorized(String reason) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, reason);
    }
}
