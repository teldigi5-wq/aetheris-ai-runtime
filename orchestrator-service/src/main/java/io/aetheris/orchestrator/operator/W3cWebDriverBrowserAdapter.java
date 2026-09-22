package io.aetheris.orchestrator.operator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static io.aetheris.orchestrator.operator.BrowserTypes.*;

@Service
public class W3cWebDriverBrowserAdapter {

    private static final String ELEMENT_KEY = "element-6066-11e4-a52e-4f735466cecf";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final BrowserDownloadEvidenceService downloadEvidence;

    public W3cWebDriverBrowserAdapter(
            ObjectMapper objectMapper,
            BrowserDownloadEvidenceService downloadEvidence) {
        this.objectMapper = objectMapper;
        this.downloadEvidence = downloadEvidence;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public AdapterExecution execute(
            String endpoint,
            String browserName,
            List<BrowserAction> actions,
            Map<String, String> valuesByRef,
            Map<String, String> filesByRef,
            Set<String> allowedDomains) {
        URI base = validatedLoopbackEndpoint(endpoint);
        String sessionId = null;
        List<BrowserActionResult> results = new ArrayList<>();
        String finalUrl = "";
        Path downloadDirectory = actions.stream().anyMatch(action -> action != null && action.type() == BrowserActionType.DOWNLOAD)
                ? downloadEvidence.createExecutionDirectory()
                : null;
        try {
            JsonNode session = request(base, "POST", "/session", sessionRequest(browserName, downloadDirectory));
            sessionId = text(session.path("value"), "sessionId");
            if (sessionId.isBlank()) sessionId = text(session, "sessionId");
            if (sessionId.isBlank()) throw new IllegalStateException("WebDriver did not return a session id");

            for (int i = 0; i < actions.size(); i++) {
                BrowserAction action = actions.get(i);
                String actionId = action.actionId().isBlank() ? "step-" + (i + 1) : action.actionId();
                try {
                    BrowserActionResult result = executeAction(
                            base,
                            sessionId,
                            actionId,
                            action,
                            valuesByRef,
                            filesByRef,
                            downloadDirectory);
                    finalUrl = currentUrl(base, sessionId);
                    validateCurrentUrl(finalUrl, allowedDomains);
                    results.add(result);
                } catch (RuntimeException exception) {
                    results.add(new BrowserActionResult(actionId, action.type(), false, safeMessage(exception), ""));
                    return new AdapterExecution(false, finalUrl, results, "Browser action failed: " + actionId);
                }
            }
            return new AdapterExecution(true, finalUrl, results, "Generic browser workflow completed through local W3C WebDriver");
        } finally {
            if (sessionId != null && !sessionId.isBlank()) {
                try {
                    request(base, "DELETE", "/session/" + sessionId, null);
                } catch (RuntimeException ignored) {
                    // Best-effort cleanup only. Execution evidence records the actual workflow result separately.
                }
            }
        }
    }

    private Map<String, Object> sessionRequest(String browserName, Path downloadDirectory) {
        String effectiveBrowserName = browserName == null || browserName.isBlank() ? "chrome" : browserName.trim();
        Map<String, Object> alwaysMatch = new LinkedHashMap<>();
        alwaysMatch.put("browserName", effectiveBrowserName);

        if (downloadDirectory != null) {
            Map<String, Object> prefs = new LinkedHashMap<>();
            prefs.put("download.default_directory", downloadDirectory.toAbsolutePath().normalize().toString());
            prefs.put("download.prompt_for_download", false);
            prefs.put("download.directory_upgrade", true);
            prefs.put("safebrowsing.enabled", true);

            String lower = effectiveBrowserName.toLowerCase(Locale.ROOT);
            String optionsKey = lower.contains("edge") ? "ms:edgeOptions" : "goog:chromeOptions";
            alwaysMatch.put(optionsKey, Map.of("prefs", prefs));
        }

        return Map.of("capabilities", Map.of("alwaysMatch", alwaysMatch));
    }

    private BrowserActionResult executeAction(
            URI base,
            String sessionId,
            String actionId,
            BrowserAction action,
            Map<String, String> valuesByRef,
            Map<String, String> filesByRef,
            Path downloadDirectory) {
        return switch (action.type()) {
            case NAVIGATE -> {
                request(base, "POST", "/session/" + sessionId + "/url", Map.of("url", action.url()));
                yield new BrowserActionResult(actionId, action.type(), true, "Navigation completed", "");
            }
            case CLICK -> {
                String elementId = findElement(base, sessionId, action.selector());
                request(base, "POST", "/session/" + sessionId + "/element/" + elementId + "/click", Map.of());
                yield new BrowserActionResult(actionId, action.type(), true, "Element click completed", "");
            }
            case TYPE -> {
                String value = requiredRef(valuesByRef, action.valueRef(), "valueRef");
                String elementId = findElement(base, sessionId, action.selector());
                request(base, "POST", "/session/" + sessionId + "/element/" + elementId + "/value", Map.of("text", value));
                yield new BrowserActionResult(actionId, action.type(), true,
                        "Text input completed from an ephemeral value reference; literal input was not added to audit metadata", "");
            }
            case UPLOAD -> {
                String file = requiredRef(filesByRef, action.fileRef(), "fileRef");
                String elementId = findElement(base, sessionId, action.selector());
                request(base, "POST", "/session/" + sessionId + "/element/" + elementId + "/value", Map.of("text", file));
                yield new BrowserActionResult(actionId, action.type(), true, "File input populated from a supplied file reference", "");
            }
            case DOWNLOAD -> {
                if (downloadDirectory == null) throw new IllegalStateException("Isolated download directory was not prepared");
                String expectedFilename = requiredRef(filesByRef, action.fileRef(), "fileRef");
                downloadEvidence.prepareExpectedTarget(downloadDirectory, expectedFilename);
                String elementId = findElement(base, sessionId, action.selector());
                request(base, "POST", "/session/" + sessionId + "/element/" + elementId + "/click", Map.of());
                int seconds = action.timeoutSeconds() == null ? 15 : Math.max(1, Math.min(action.timeoutSeconds(), 30));
                BrowserDownloadEvidenceService.VerifiedDownload verified = downloadEvidence.awaitVerifiedDownload(
                        downloadDirectory,
                        expectedFilename,
                        Duration.ofSeconds(seconds));
                BrowserArtifactEvidence artifact = new BrowserArtifactEvidence(
                        verified.relativePath(),
                        verified.sizeBytes(),
                        verified.sha256());
                yield new BrowserActionResult(
                        actionId,
                        action.type(),
                        true,
                        "Downloaded artifact verified inside the isolated browser download root",
                        "sha256:" + verified.sha256(),
                        artifact);
            }
            case SCREENSHOT -> {
                JsonNode response = request(base, "GET", "/session/" + sessionId + "/screenshot", null);
                String base64 = response.path("value").asText("");
                String evidence = base64.isBlank() ? "" : "sha256:" + sha256(base64);
                yield new BrowserActionResult(actionId, action.type(), !base64.isBlank(),
                        base64.isBlank() ? "WebDriver returned no screenshot evidence" : "Screenshot evidence hash captured", evidence);
            }
            case EXTRACT_TEXT -> {
                String elementId = findElement(base, sessionId, action.selector());
                JsonNode response = request(base, "GET", "/session/" + sessionId + "/element/" + elementId + "/text", null);
                String extracted = response.path("value").asText("");
                yield new BrowserActionResult(actionId, action.type(), true, truncate(extracted, 2000), "");
            }
            case WAIT -> {
                int seconds = action.timeoutSeconds() == null ? 1 : Math.max(0, Math.min(action.timeoutSeconds(), 30));
                try {
                    Thread.sleep(seconds * 1000L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Browser wait interrupted", exception);
                }
                yield new BrowserActionResult(actionId, action.type(), true, "Wait completed for " + seconds + " second(s)", "");
            }
        };
    }

    private String findElement(URI base, String sessionId, String selector) {
        if (selector == null || selector.isBlank()) throw new IllegalArgumentException("CSS selector is required for this browser action");
        JsonNode response = request(base, "POST", "/session/" + sessionId + "/element",
                Map.of("using", "css selector", "value", selector));
        String elementId = response.path("value").path(ELEMENT_KEY).asText("");
        if (elementId.isBlank()) throw new IllegalStateException("WebDriver did not return an element id for selector");
        return elementId;
    }

    private String currentUrl(URI base, String sessionId) {
        JsonNode response = request(base, "GET", "/session/" + sessionId + "/url", null);
        return response.path("value").asText("");
    }

    private void validateCurrentUrl(String rawUrl, Set<String> allowedDomains) {
        if (rawUrl == null || rawUrl.isBlank() || rawUrl.equals("about:blank")) return;
        URI uri = URI.create(rawUrl);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalStateException("Browser left http/https navigation: " + scheme);
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isBlank() || !domainAllowed(host, allowedDomains)) {
            throw new IllegalStateException("Browser redirected outside the workflow domain allowlist: " + host);
        }
    }

    private boolean domainAllowed(String host, Set<String> allowedDomains) {
        for (String domain : allowedDomains) {
            String normalized = domain == null ? "" : domain.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("*.")) normalized = normalized.substring(2);
            if (!normalized.isBlank() && (host.equals(normalized) || host.endsWith("." + normalized))) return true;
        }
        return false;
    }

    private JsonNode request(URI base, String method, String path, Object body) {
        try {
            URI uri = base.resolve(path.startsWith("/") ? path.substring(1) : path);
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30));
            if ("GET".equals(method)) {
                builder.GET();
            } else if ("DELETE".equals(method)) {
                builder.DELETE();
            } else {
                String json = objectMapper.writeValueAsString(body == null ? Map.of() : body);
                builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json));
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("WebDriver returned HTTP " + response.statusCode());
            }
            JsonNode json = response.body() == null || response.body().isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(response.body());
            if (json.path("value").has("error")) {
                throw new IllegalStateException("WebDriver error: " + json.path("value").path("error").asText("unknown"));
            }
            return json;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("WebDriver request failed", exception);
        }
    }

    private URI validatedLoopbackEndpoint(String endpoint) {
        URI uri = URI.create(endpoint == null || endpoint.isBlank() ? "http://127.0.0.1:9515/" : endpoint.trim());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) throw new IllegalArgumentException("WebDriver endpoint must use http or https");
        if (!(host.equals("127.0.0.1") || host.equals("localhost") || host.equals("::1"))) {
            throw new IllegalArgumentException("WebDriver endpoint must be loopback-local; remote browser control is not allowed by this adapter");
        }
        String normalized = uri.toString();
        return URI.create(normalized.endsWith("/") ? normalized : normalized + "/");
    }

    private String requiredRef(Map<String, String> values, String ref, String field) {
        if (ref == null || ref.isBlank()) throw new IllegalArgumentException(field + " is required");
        String value = values.get(ref);
        if (value == null) throw new IllegalArgumentException("No supplied ephemeral value exists for " + field);
        return value;
    }

    private String text(JsonNode node, String field) {
        return node == null ? "" : node.path(field).asText("");
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash browser evidence", exception);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    public record AdapterExecution(boolean success, String finalUrl, List<BrowserActionResult> actions, String detail) {
        public AdapterExecution {
            finalUrl = finalUrl == null ? "" : finalUrl;
            actions = actions == null ? List.of() : List.copyOf(actions);
            detail = detail == null ? "" : detail;
        }
    }
}
