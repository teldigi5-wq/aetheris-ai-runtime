package io.aetheris.orchestrator.syntracore;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase13LocalRuntimeAdapterTest {
    private HttpServer server;
    private AtomicReference<String> generateRequestBody;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        generateRequestBody = new AtomicReference<>("");
        server.createContext("/api/tags", this::handleTags);
        server.createContext("/api/generate", this::handleGenerate);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void endpointFailsClosedForNonLoopbackHosts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocalRuntimeEndpoint(URI.create("http://example.com:11434")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocalRuntimeEndpoint(URI.create("https://127.0.0.1:11434")));
    }

    @Test
    void providerDiscoveryUsesLoopbackAndReturnsDeterministicModelIds() {
        OllamaLocalRuntime runtime = runtime();

        LocalProviderDiscovery discovery = runtime.discoverModels();

        assertTrue(discovery.reachable());
        assertEquals(OllamaLocalRuntime.PROVIDER_ID, discovery.providerId());
        assertEquals(List.of("llama3.2:3b", "qwen2.5:7b"), discovery.modelIds());
        assertEquals(null, discovery.errorCode());
    }

    @Test
    void streamUsesNdjsonProtocolAndCarriesConfiguredOutputBound() {
        OllamaLocalRuntime runtime = runtime();
        List<ModelStreamChunk> chunks = new ArrayList<>();

        runtime.stream(
                new ModelInvocation("qwen2.5:7b", "Say hello", 64),
                chunks::add,
                () -> false);

        assertEquals(3, chunks.size());
        assertEquals(new ModelStreamChunk(0, "Hel", false), chunks.get(0));
        assertEquals(new ModelStreamChunk(1, "lo", false), chunks.get(1));
        assertEquals(new ModelStreamChunk(2, "", true), chunks.get(2));
        assertTrue(generateRequestBody.get().contains("\"num_predict\":64"));
        assertTrue(generateRequestBody.get().contains("\"stream\":true"));
    }

    @Test
    void cooperativeCancellationStopsConsumptionWithoutInventingTerminalFrame() {
        OllamaLocalRuntime runtime = runtime();
        List<ModelStreamChunk> chunks = new ArrayList<>();
        AtomicBoolean cancelled = new AtomicBoolean(false);

        runtime.stream(
                new ModelInvocation("qwen2.5:7b", "Say hello", 64),
                chunk -> {
                    chunks.add(chunk);
                    cancelled.set(true);
                },
                cancelled::get);

        assertEquals(1, chunks.size());
        assertFalse(chunks.getFirst().terminal());
    }

    @Test
    void runtimeRejectsUnconfiguredModelsAndOversizedRequestsBeforeExecution() {
        OllamaLocalRuntime runtime = runtime();

        assertThrows(
                IllegalArgumentException.class,
                () -> runtime.stream(
                        new ModelInvocation("unapproved:latest", "hello", 8),
                        ignored -> { },
                        () -> false));

        assertThrows(
                IllegalArgumentException.class,
                () -> runtime.stream(
                        new ModelInvocation("qwen2.5:7b", "x".repeat(1_001), 8),
                        ignored -> { },
                        () -> false));

        assertThrows(
                IllegalArgumentException.class,
                () -> runtime.stream(
                        new ModelInvocation("qwen2.5:7b", "hello", 129),
                        ignored -> { },
                        () -> false));
    }

    @Test
    void repositoryHardwareProbeReportsCpuAndRamWithoutClaimingGpuEvidence() {
        LocalHardwareDiscovery discovery = new JvmLocalHardwareProbe().discover();

        assertTrue(discovery.availableProcessors() > 0);
        assertFalse(discovery.osName().isBlank());
        assertFalse(discovery.osArch().isBlank());
        assertFalse(discovery.gpuEvidenceAvailable());
        discovery.totalPhysicalRamMb().ifPresent(value -> assertTrue(value > 0));
    }

    private OllamaLocalRuntime runtime() {
        return new OllamaLocalRuntime(
                new LocalRuntimeEndpoint(URI.create("http://127.0.0.1:" + server.getAddress().getPort())),
                new LocalRuntimeBounds(1_000, 128, 10, 256, Duration.ofSeconds(5)),
                List.of(candidate("qwen2.5:7b")));
    }

    private static ModelRuntimeCandidate candidate(String modelId) {
        return new ModelRuntimeCandidate(
                OllamaLocalRuntime.PROVIDER_ID,
                modelId,
                Set.of(ModelCapability.CHAT, ModelCapability.CODING),
                8_192,
                true,
                true,
                true,
                true,
                6_000,
                8_000,
                true,
                0.80,
                150.0,
                20.0,
                0.01,
                5_000.0,
                0.0);
    }

    private void handleTags(HttpExchange exchange) throws IOException {
        byte[] body = ("{\"models\":["
                + "{\"name\":\"qwen2.5:7b\"},"
                + "{\"name\":\"llama3.2:3b\"},"
                + "{\"name\":\"qwen2.5:7b\"}]}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void handleGenerate(HttpExchange exchange) throws IOException {
        generateRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] body = ("{\"response\":\"Hel\",\"done\":false}\n"
                + "{\"response\":\"lo\",\"done\":false}\n"
                + "{\"response\":\"\",\"done\":true}\n").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
