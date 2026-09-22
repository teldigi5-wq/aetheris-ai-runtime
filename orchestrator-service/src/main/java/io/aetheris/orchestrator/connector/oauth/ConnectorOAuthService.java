package io.aetheris.orchestrator.connector.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aetheris.orchestrator.connector.*;
import jakarta.transaction.Transactional;
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
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ConnectorOAuthService {
    private static final Duration SESSION_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConnectorConnectionRepository connections;
    private final OAuthAuthorizationSessionRepository sessions;
    private final ProviderCredentialRepository credentials;
    private final OAuthProviderRegistry providers;
    private final InMemoryConnectorCredentialVault vault;
    private final OAuthLifecycleAccountContinuityService accountContinuity;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public ConnectorOAuthService(ConnectorConnectionRepository connections,
                                 OAuthAuthorizationSessionRepository sessions,
                                 ProviderCredentialRepository credentials,
                                 OAuthProviderRegistry providers,
                                 InMemoryConnectorCredentialVault vault,
                                 OAuthLifecycleAccountContinuityService accountContinuity,
                                 ObjectMapper mapper) {
        this.connections = connections;
        this.sessions = sessions;
        this.credentials = credentials;
        this.providers = providers;
        this.vault = vault;
        this.accountContinuity = accountContinuity;
        this.mapper = mapper;
    }

    @Transactional
    public OAuthAuthorizationView start(UUID connectionId, StartOAuthAuthorizationRequest request) {
        ConnectorConnectionEntity connection = connection(connectionId);
        ProviderOAuthConfig config = providers.config(connection.getProvider());
        String redirectUri = validRedirectUri(request == null ? null : request.redirectUri());

        UUID sessionId = UUID.randomUUID();
        String state = randomUrlToken(32);
        String verifier = randomUrlToken(48);
        String challenge = base64Url(sha256(verifier.getBytes(StandardCharsets.UTF_8)));
        String pkceReference = vault.put("pkce-" + sessionId, verifier);
        Instant expiresAt = Instant.now().plus(SESSION_TTL);
        String scopesCsv = String.join(",", config.scopes());

        OAuthAuthorizationSessionEntity session = sessions.save(new OAuthAuthorizationSessionEntity(
                sessionId, connectionId, connection.getProvider(), sha256Hex(state), pkceReference,
                redirectUri, scopesCsv, expiresAt));

        String authorizationUrl = authorizationUrl(config, redirectUri, state, challenge);
        return new OAuthAuthorizationView(session.getId(), connectionId, connection.getProvider(),
                authorizationUrl, state, expiresAt, List.copyOf(config.scopes()));
    }

    @Transactional
    public ProviderCredentialView complete(UUID sessionId, CompleteOAuthAuthorizationRequest request) {
        OAuthAuthorizationSessionEntity session = session(sessionId);
        if (session.getStatus() != OAuthSessionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OAuth authorization session is not pending");
        }
        if (Instant.now().isAfter(session.getExpiresAt())) {
            session.expire();
            sessions.save(session);
            vault.delete(session.getPkceReference());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OAuth authorization session expired");
        }
        String state = required(request == null ? null : request.state(), "state");
        if (!MessageDigest.isEqual(session.getStateHash().getBytes(StandardCharsets.UTF_8),
                sha256Hex(state).getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OAuth state validation failed");
        }
        String code = required(request.code(), "authorization code");
        String verifier = vault.require(session.getPkceReference());
        ProviderOAuthConfig config = providers.config(session.getProvider());

        TokenExchange token;
        try {
            token = exchangeAuthorizationCode(config, code, session.getRedirectUri(), verifier);
        } catch (RuntimeException error) {
            session.fail();
            sessions.save(session);
            throw error;
        }

        ConnectorConnectionEntity connection = connection(session.getConnectionId());
        accountContinuity.assertCurrent(connection, token.accessToken());
        ProviderCredentialEntity credential = credentials.findByConnectionId(session.getConnectionId()).orElse(null);
        requireCredentialProvider(connection, credential);

        String accessReference = vault.put("access-" + session.getConnectionId(), token.accessToken());
        String refreshReference = token.refreshToken() == null || token.refreshToken().isBlank()
                ? null : vault.put("refresh-" + session.getConnectionId(), token.refreshToken());
        Instant expiresAt = token.expiresInSeconds() > 0
                ? Instant.now().plusSeconds(token.expiresInSeconds()) : null;
        String scopesCsv = token.scope().isBlank() ? session.getScopesCsv() : normalizeScopeCsv(token.scope());

        if (credential == null) {
            credential = new ProviderCredentialEntity(UUID.randomUUID(), session.getConnectionId(), session.getProvider(),
                    accessReference, refreshReference, scopesCsv, expiresAt);
        } else {
            vault.delete(credential.getAccessTokenReference());
            if (refreshReference != null) vault.delete(credential.getRefreshTokenReference());
            credential.rotate(accessReference, refreshReference, scopesCsv, expiresAt);
        }
        credential = credentials.save(credential);
        session.complete();
        sessions.save(session);
        vault.delete(session.getPkceReference());

        connection.updateStatus(ConnectorStatus.ENABLED);
        connections.save(connection);
        return view(credential);
    }

    public ProviderCredentialView credential(UUID connectionId) {
        return view(credentialEntity(connectionId));
    }

    @Transactional
    public ProviderHealthView health(UUID connectionId) {
        ConnectorConnectionEntity connection = connection(connectionId);
        ProviderCredentialEntity credential = credentialEntity(connectionId);
        requireCredentialProvider(connection, credential);
        if (credential.getStatus() == ProviderCredentialStatus.REVOKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Provider credential is revoked");
        }
        if (credential.getExpiresAt() != null && Instant.now().isAfter(credential.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Provider access token is expired; refresh is required");
        }
        String accessToken = vault.require(credential.getAccessTokenReference());
        ProviderOAuthConfig config = providers.config(connection.getProvider());
        ProviderIdentity identity = readIdentity(config, accessToken);
        boolean healthy = identity.healthy()
                && accountContinuity.matchesResolvedIdentity(connection, identity.accountId());
        credential.validated(healthy);
        credentials.save(credential);
        if (!healthy && identity.healthy()) {
            return new ProviderHealthView(connectionId, connection.getProvider(), false,
                    "", "", "Provider account continuity validation failed", credential.getLastValidatedAt());
        }
        return new ProviderHealthView(connectionId, connection.getProvider(), healthy,
                healthy ? identity.accountId() : "",
                healthy ? identity.displayName() : "",
                identity.message(), credential.getLastValidatedAt());
    }

    @Transactional
    public ProviderCredentialView refresh(UUID connectionId) {
        ConnectorConnectionEntity connection = connection(connectionId);
        ProviderCredentialEntity credential = credentialEntity(connectionId);
        requireCredentialProvider(connection, credential);
        if (credential.getStatus() == ProviderCredentialStatus.REVOKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Provider credential is revoked");
        }
        if (credential.getRefreshTokenReference() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Provider refresh token is not configured");
        }
        ProviderOAuthConfig config = providers.config(connection.getProvider());
        if (!config.refreshSupported()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Provider token refresh is not supported");
        }
        String refreshToken = vault.require(credential.getRefreshTokenReference());
        TokenExchange token = exchangeRefreshToken(config, refreshToken);
        accountContinuity.assertCurrent(connection, token.accessToken());
        String oldAccessRef = credential.getAccessTokenReference();
        String oldRefreshRef = credential.getRefreshTokenReference();
        String accessRef = vault.put("access-" + connectionId, token.accessToken());
        String refreshRef = token.refreshToken() == null || token.refreshToken().isBlank()
                ? null : vault.put("refresh-" + connectionId, token.refreshToken());
        Instant expiresAt = token.expiresInSeconds() > 0
                ? Instant.now().plusSeconds(token.expiresInSeconds()) : credential.getExpiresAt();
        String scopesCsv = token.scope().isBlank() ? credential.getScopesCsv() : normalizeScopeCsv(token.scope());
        credential.rotate(accessRef, refreshRef, scopesCsv, expiresAt);
        credential = credentials.save(credential);
        vault.delete(oldAccessRef);
        if (refreshRef != null) vault.delete(oldRefreshRef);
        return view(credential);
    }

    @Transactional
    public ProviderCredentialView disconnect(UUID connectionId) {
        ConnectorConnectionEntity connection = connection(connectionId);
        ProviderCredentialEntity credential = credentialEntity(connectionId);
        vault.delete(credential.getAccessTokenReference());
        vault.delete(credential.getRefreshTokenReference());
        credential.revoke();
        credential = credentials.save(credential);
        connection.updateStatus(ConnectorStatus.SUSPENDED);
        connections.save(connection);
        return view(credential);
    }

    private TokenExchange exchangeAuthorizationCode(ProviderOAuthConfig config, String code,
                                                     String redirectUri, String verifier) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("client_id", config.clientId());
        form.put("client_secret", config.clientSecret());
        form.put("redirect_uri", redirectUri);
        form.put("code_verifier", verifier);
        return tokenRequest(config.tokenEndpoint(), form);
    }

    private TokenExchange exchangeRefreshToken(ProviderOAuthConfig config, String refreshToken) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        form.put("client_id", config.clientId());
        form.put("client_secret", config.clientSecret());
        return tokenRequest(config.tokenEndpoint(), form);
    }

    private TokenExchange tokenRequest(String endpoint, Map<String, String> form) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(form)))
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Provider token exchange failed with HTTP " + response.statusCode());
        }
        try {
            JsonNode root = mapper.readTree(response.body());
            String access = text(root, "access_token");
            if (access.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Provider token response did not contain access_token");
            return new TokenExchange(access, text(root, "refresh_token"), root.path("expires_in").asLong(0),
                    text(root, "scope"));
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Provider token response was invalid JSON", error);
        }
    }

    private ProviderIdentity readIdentity(ProviderOAuthConfig config, String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.userInfoEndpoint()))
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .header("User-Agent", "Aetheris-Syntra-Connector/1.0")
                .GET().build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return new ProviderIdentity(false, "", "", "Provider identity check returned HTTP " + response.statusCode());
        }
        try {
            JsonNode root = mapper.readTree(response.body());
            if (config.provider() == ConnectorProvider.GITHUB) {
                String id = root.path("id").asText("");
                String login = text(root, "login");
                String name = text(root, "name");
                return new ProviderIdentity(true, id, name.isBlank() ? login : name, "Read-only GitHub identity check passed");
            }
            String id = text(root, "sub");
            String email = text(root, "email");
            String name = text(root, "name");
            return new ProviderIdentity(true, id, name.isBlank() ? email : name, "Read-only Google identity check passed");
        } catch (IOException error) {
            return new ProviderIdentity(false, "", "", "Provider identity response was invalid JSON");
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Provider request failed", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Provider request was interrupted", error);
        }
    }

    private OAuthAuthorizationSessionEntity session(UUID id) {
        if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId is required");
        return sessions.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "OAuth authorization session was not found"));
    }

    private ConnectorConnectionEntity connection(UUID id) {
        if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "connectionId is required");
        return connections.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Connector connection was not found"));
    }

    private ProviderCredentialEntity credentialEntity(UUID connectionId) {
        return credentials.findByConnectionId(connectionId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider credential was not found"));
    }

    private static void requireCredentialProvider(ConnectorConnectionEntity connection,
                                                  ProviderCredentialEntity credential) {
        if (credential != null && credential.getProvider() != connection.getProvider()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Provider credential does not match connector provider");
        }
    }

    private ProviderCredentialView view(ProviderCredentialEntity entity) {
        return new ProviderCredentialView(entity.getConnectionId(), entity.getProvider(), entity.getStatus(),
                entity.getAccessTokenReference() != null && vault.contains(entity.getAccessTokenReference()),
                entity.getRefreshTokenReference() != null && vault.contains(entity.getRefreshTokenReference()),
                parseScopes(entity.getScopesCsv()), entity.getExpiresAt(), entity.getLastValidatedAt(), entity.getUpdatedAt());
    }

    private static String authorizationUrl(ProviderOAuthConfig config, String redirectUri,
                                           String state, String challenge) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("response_type", "code");
        query.put("client_id", config.clientId());
        query.put("redirect_uri", redirectUri);
        query.put("scope", String.join(" ", config.scopes()));
        query.put("state", state);
        query.put("code_challenge", challenge);
        query.put("code_challenge_method", "S256");
        if (config.provider() == ConnectorProvider.GMAIL || config.provider() == ConnectorProvider.CALENDAR) {
            query.put("access_type", "offline");
            query.put("prompt", "consent");
        }
        return config.authorizationEndpoint() + (config.authorizationEndpoint().contains("?") ? "&" : "?") + form(query);
    }

    private static String validRedirectUri(String value) {
        String result = required(value, "redirectUri");
        try {
            URI uri = URI.create(result);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
            return uri.toString();
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "redirectUri must be an absolute HTTP(S) URI");
        }
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return value.trim();
    }

    private static String randomUrlToken(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return base64Url(value);
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String normalizeScopeCsv(String scope) {
        return Arrays.stream(scope.trim().split("[ ,]+"))
                .filter(item -> !item.isBlank())
                .distinct().sorted().collect(Collectors.joining(","));
    }

    private static List<String> parseScopes(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }

    private record TokenExchange(String accessToken, String refreshToken, long expiresInSeconds, String scope) {}
    private record ProviderIdentity(boolean healthy, String accountId, String displayName, String message) {}
}
