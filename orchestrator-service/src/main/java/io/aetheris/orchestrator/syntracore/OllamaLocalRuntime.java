package io.aetheris.orchestrator.syntracore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class OllamaLocalRuntime implements SyntraModelRuntime {
    public static final String PROVIDER_ID = "ollama-local";

    private final LocalRuntimeEndpoint endpoint;
    private final LocalRuntimeBounds bounds;
    private final List<ModelRuntimeCandidate> configuredModels;
    private final Set<String> configuredModelIds;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OllamaLocalRuntime(
            LocalRuntimeEndpoint endpoint,
            LocalRuntimeBounds bounds,
            List<ModelRuntimeCandidate> configuredModels) {
        this(
                endpoint,
                bounds,
                configuredModels,
                HttpClient.newBuilder().connectTimeout(bounds.requestTimeout()).build(),
                new ObjectMapper(),
                Clock.systemUTC());
    }

    OllamaLocalRuntime(
            LocalRuntimeEndpoint endpoint,
            LocalRuntimeBounds bounds,
            List<ModelRuntimeCandidate> configuredModels,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Clock clock) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.configuredModels = List.copyOf(Objects.requireNonNull(configuredModels, "configuredModels"));

        Set<String> modelIds = new HashSet<>();
        for (ModelRuntimeCandidate candidate : this.configuredModels) {
            if (!PROVIDER_ID.equals(candidate.providerId())) {
                throw new IllegalArgumentException("all configured models must belong to " + PROVIDER_ID);
            }
            if (!candidate.local()) {
                throw new IllegalArgumentException("Ollama configured models must be local");
            }
            if (!modelIds.add(candidate.modelId())) {
                throw new IllegalArgumentException("duplicate configured modelId: " + candidate.modelId());
            }
        }
        this.configuredModelIds = Set.copyOf(modelIds);
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public List<ModelRuntimeCandidate> models() {
        return configuredModels;
    }

    public LocalProviderDiscovery discoverModels() {
        Instant observedAt = clock.instant();
        HttpRequest request = HttpRequest.newBuilder(endpoint.resolve("/api/tags"))
                .GET()
                .timeout(bounds.requestTimeout())
                .header("Accept", "application/json")
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return LocalProviderDiscovery.unreachable(
                        PROVIDER_ID,
                        observedAt,
                        "HTTP_" + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode models = root.path("models");
            if (!models.isArray()) {
                return LocalProviderDiscovery.unreachable(PROVIDER_ID, observedAt, "INVALID_DISCOVERY_PAYLOAD");
            }
            List<String> modelIds = new ArrayList<>();
            for (JsonNode model : models) {
                String name = model.path("name").asText("").trim();
                if (!name.isEmpty()) {
                    modelIds.add(name);
                }
            }
            modelIds = modelIds.stream().distinct().sorted(Comparator.naturalOrder()).toList();
            return LocalProviderDiscovery.reachable(PROVIDER_ID, modelIds, observedAt);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return LocalProviderDiscovery.unreachable(PROVIDER_ID, observedAt, "INTERRUPTED");
        } catch (IOException | RuntimeException exception) {
            return LocalProviderDiscovery.unreachable(PROVIDER_ID, observedAt, "DISCOVERY_FAILED");
        }
    }

    @Override
    public void stream(
            ModelInvocation invocation,
            Consumer<ModelStreamChunk> sink,
            BooleanSupplier cancellationRequested) {
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        bounds.validate(invocation);
        if (!configuredModelIds.contains(invocation.modelId())) {
            throw new IllegalArgumentException("modelId is not explicitly configured for local execution");
        }
        if (cancellationRequested.getAsBoolean()) {
            return;
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", invocation.modelId());
        payload.put("prompt", invocation.input());
        payload.put("stream", true);
        ObjectNode options = payload.putObject("options");
        options.put("num_predict", invocation.maxOutputTokens());

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(endpoint.resolve("/api/generate"))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .timeout(bounds.requestTimeout())
                    .header("Accept", "application/x-ndjson")
                    .header("Content-Type", "application/json")
                    .build();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to encode local runtime request", exception);
        }

        try {
            HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (Stream<String> ignored = response.body()) {
                    // Closing the response stream is sufficient; remote error text is intentionally not surfaced.
                }
                throw new IllegalStateException("local runtime returned HTTP " + response.statusCode());
            }

            boolean terminalSeen = false;
            long sequence = 0;
            int emittedChunks = 0;
            try (Stream<String> lines = response.body()) {
                var iterator = lines.iterator();
                while (iterator.hasNext()) {
                    if (cancellationRequested.getAsBoolean()) {
                        return;
                    }
                    String line = iterator.next();
                    if (line == null || line.isBlank()) {
                        continue;
                    }
                    JsonNode node = objectMapper.readTree(line);
                    if (node.hasNonNull("error")) {
                        throw new IllegalStateException("local runtime reported a protocol error");
                    }
                    String text = node.path("response").asText("");
                    if (text.length() > bounds.maxChunkCharacters()) {
                        throw new IllegalStateException("local runtime stream chunk exceeds configured bound");
                    }
                    boolean terminal = node.path("done").asBoolean(false);
                    if (!text.isEmpty() || terminal) {
                        emittedChunks++;
                        if (emittedChunks > bounds.maxStreamChunks()) {
                            throw new IllegalStateException("local runtime exceeded configured stream-chunk bound");
                        }
                        sink.accept(new ModelStreamChunk(sequence++, text, terminal));
                    }
                    if (terminal) {
                        terminalSeen = true;
                        break;
                    }
                }
            }
            if (!terminalSeen && !cancellationRequested.getAsBoolean()) {
                throw new IllegalStateException("local runtime stream ended without a terminal frame");
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("local runtime request interrupted", interruptedException);
        } catch (IOException exception) {
            throw new IllegalStateException("local runtime I/O or protocol failure", exception);
        }
    }
}
