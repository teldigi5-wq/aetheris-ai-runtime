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
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase13ConcreteOllamaAdapterRuntimeTest {
    private static final String BASE_MODEL = "qwen2.5:7b";
    private static final String ADAPTER_ALIAS = "aetheris-owner-adapter:latest";

    private HttpServer server;
    private AtomicReference<List<String>> providerModels;
    private AtomicReference<String> generateRequestBody;
    private AtomicInteger generateCalls;

    @BeforeEach
    void startServer() throws IOException {
        providerModels = new AtomicReference<>(List.of(BASE_MODEL, ADAPTER_ALIAS));
        generateRequestBody = new AtomicReference<>("");
        generateCalls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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
    void preprovisionedAliasActivationDoesNotMintRoutingRegistration() {
        AdapterArtifactIdentity identity = identity();
        OllamaAdapterRuntimeBridge bridge = bridge(identity);

        AdapterArtifactObservation before = bridge.inspect(identity);
        assertTrue(before.exists());
        assertFalse(before.active());

        AdapterArtifactObservation activated = bridge.activate(identity);
        assertTrue(activated.exists());
        assertTrue(activated.active());
        assertTrue(bridge.adapterRegistrations().isEmpty());
        assertEquals(0, generateCalls.get());
    }

    @Test
    void missingProviderAliasFailsClosedWithoutInventingActivation() {
        AdapterArtifactIdentity identity = identity();
        OllamaAdapterRuntimeBridge bridge = bridge(identity);
        providerModels.set(List.of(BASE_MODEL));

        AdapterArtifactObservation activated = bridge.activate(identity);

        assertFalse(activated.exists());
        assertFalse(activated.active());
        assertTrue(bridge.adapterRegistrations().isEmpty());
        assertEquals(0, generateCalls.get());
    }

    @Test
    void verifiedLifecycleReconciliationRoutesLogicalAdapterThroughBoundProviderAlias() {
        AdapterArtifactIdentity identity = identity();
        OllamaAdapterRuntimeBridge bridge = bridge(identity);
        bridge.activate(identity);
        AdapterRuntimeRegistration registration = bridge.reconcileLifecycle(activated(identity)).orElseThrow();
        ContextPack context = contextPack();

        LocalInferenceResult result = new SyntraLocalInferenceOrchestrator(
                new SyntraModelRouter(),
                List.of(bridge))
                .infer(request(context), hardware(), () -> false);

        assertEquals(InferenceStatus.COMPLETED, result.status());
        assertEquals("adapter response", result.output());
        assertEquals(registration.candidate().modelId(), result.routeSelection().orElseThrow().modelId());
        assertEquals(context.citations(), result.evidenceAddresses());
        assertTrue(generateRequestBody.get().contains("\"model\":\"" + ADAPTER_ALIAS + "\""));
        assertFalse(generateRequestBody.get().contains(registration.candidate().modelId()));
        assertEquals(1, generateCalls.get());
    }

    @Test
    void providerAliasDisappearanceAfterReconciliationFailsClosedBeforeGeneration() {
        AdapterArtifactIdentity identity = identity();
        OllamaAdapterRuntimeBridge bridge = bridge(identity);
        bridge.activate(identity);
        bridge.reconcileLifecycle(activated(identity));
        providerModels.set(List.of(BASE_MODEL));

        LocalInferenceResult result = new SyntraLocalInferenceOrchestrator(
                new SyntraModelRouter(),
                List.of(bridge))
                .infer(request(contextPack()), hardware(), () -> false);

        assertEquals(InferenceStatus.UNAVAILABLE, result.status());
        assertEquals(0, generateCalls.get());
    }

    @Test
    void verifiedRollbackRemovesRegistrationAndPreventsLeaseAcquisition() {
        AdapterArtifactIdentity identity = identity();
        OllamaAdapterRuntimeBridge bridge = bridge(identity);
        bridge.activate(identity);
        AdapterRuntimeRegistration registration = bridge.reconcileLifecycle(activated(identity)).orElseThrow();
        assertTrue(bridge.acquireAdapterInvocationLease(registration).isPresent());

        AdapterArtifactObservation detached = bridge.detach(identity);
        assertTrue(detached.exists());
        assertFalse(detached.active());
        bridge.reconcileLifecycle(rolledBack(identity));

        assertTrue(bridge.adapterRegistrations().isEmpty());
        assertTrue(bridge.acquireAdapterInvocationLease(registration).isEmpty());
    }

    @Test
    void unverifiedLifecycleCannotPublishRoutingRegistration() {
        AdapterArtifactIdentity identity = identity();
        OllamaAdapterRuntimeBridge bridge = bridge(identity);
        bridge.activate(identity);

        AdapterLifecycleResult unverified = new AdapterLifecycleResult(
                AdapterLifecycleStatus.ACTIVATION_UNVERIFIED,
                identity,
                true,
                false,
                true,
                true,
                true,
                List.of("verification unavailable"));

        assertTrue(bridge.reconcileLifecycle(unverified).isEmpty());
        assertTrue(bridge.adapterRegistrations().isEmpty());
    }

    @Test
    void bindingRejectsBaseAliasAndReservedProviderAlias() {
        AdapterArtifactIdentity identity = identity();
        ModelRuntimeCandidate candidate = adapterCandidate(identity);

        assertThrows(
                IllegalArgumentException.class,
                () -> new OllamaAdapterBinding(identity, BASE_MODEL, candidate));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OllamaAdapterBinding(identity, candidate.modelId(), candidate));
    }

    private OllamaAdapterRuntimeBridge bridge(AdapterArtifactIdentity identity) {
        LocalRuntimeEndpoint endpoint = new LocalRuntimeEndpoint(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        LocalRuntimeBounds bounds = new LocalRuntimeBounds(4_000, 256, 32, 512, Duration.ofSeconds(5));
        return new OllamaAdapterRuntimeBridge(
                endpoint,
                bounds,
                List.of(baseCandidate()),
                List.of(new OllamaAdapterBinding(identity, ADAPTER_ALIAS, adapterCandidate(identity))));
    }

    private static ModelRuntimeCandidate baseCandidate() {
        return candidate(BASE_MODEL, Set.of(ModelCapability.CHAT), 0.50);
    }

    private static ModelRuntimeCandidate adapterCandidate(AdapterArtifactIdentity identity) {
        return candidate(
                AdapterRuntimeRegistration.modelIdFor(identity),
                Set.of(ModelCapability.CHAT, ModelCapability.CODING),
                0.95);
    }

    private static ModelRuntimeCandidate candidate(
            String modelId,
            Set<ModelCapability> capabilities,
            double quality) {
        return new ModelRuntimeCandidate(
                OllamaLocalRuntime.PROVIDER_ID,
                modelId,
                capabilities,
                8_192,
                true,
                true,
                true,
                false,
                0,
                1_024,
                true,
                quality,
                0,
                0,
                0,
                0,
                0);
    }

    private static AdapterArtifactIdentity identity() {
        String artifactSha = "c".repeat(64);
        return new AdapterArtifactIdentity(
                "exp-slice15-ollama",
                "aetheris-adapter://candidate/exp-slice15-ollama",
                "aetheris-adapter://promoted/exp-slice15-ollama",
                BASE_MODEL,
                "a".repeat(64),
                artifactSha,
                "aetheris-adapter-artifact://sha256/" + artifactSha,
                true,
                false);
    }

    private static AdapterLifecycleResult activated(AdapterArtifactIdentity identity) {
        return new AdapterLifecycleResult(
                AdapterLifecycleStatus.ACTIVATED_VERIFIED,
                identity,
                true,
                true,
                true,
                true,
                true,
                List.of());
    }

    private static AdapterLifecycleResult rolledBack(AdapterArtifactIdentity identity) {
        return new AdapterLifecycleResult(
                AdapterLifecycleStatus.ROLLED_BACK_VERIFIED,
                identity,
                true,
                true,
                false,
                true,
                true,
                List.of());
    }

    private static ContextAwareInferenceRequest request(ContextPack context) {
        return new ContextAwareInferenceRequest(
                InferenceTask.CODING,
                context,
                "Use the verified local adapter binding.",
                128,
                4_096,
                true);
    }

    private static HardwareCapacity hardware() {
        return new HardwareCapacity(16_384, 0, false, HardwareEvidence.TARGET_PROFILE);
    }

    private static ContextPack contextPack() {
        RetrievalScope scope = RetrievalScope.project("slice15-ollama-adapter-runtime");
        UUID nodeId = UUID.fromString("15151515-2626-3737-4848-595959595959");
        RetrievalEvidence source = new RetrievalEvidence(
                "aetheris-memory://project/slice15-ollama-adapter-runtime/" + nodeId,
                nodeId,
                scope,
                "ollama#adapter-runtime",
                "Concrete adapter routing must preserve verified lifecycle and local provider binding continuity.",
                0.99,
                "phase13-slice15-test",
                Instant.parse("2026-09-21T14:10:00Z"));
        ContextEvidence evidence = new ContextEvidence(source, source.excerpt());
        return new ContextPack(
                scope,
                "concrete ollama adapter runtime",
                List.of(evidence),
                List.of(evidence.citation()),
                evidence.estimatedTokens(),
                new ContextQualityAssessment(1, 1, 1, 0, 0, 0.99, 0.99, 0.99),
                Instant.parse("2026-09-21T14:11:00Z"));
    }

    private void handleTags(HttpExchange exchange) throws IOException {
        StringBuilder json = new StringBuilder("{\"models\":[");
        for (int i = 0; i < providerModels.get().size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"name\":\"").append(providerModels.get().get(i)).append("\"}");
        }
        json.append("]}");
        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void handleGenerate(HttpExchange exchange) throws IOException {
        generateCalls.incrementAndGet();
        generateRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] body = ("{\"response\":\"adapter \",\"done\":false}\n"
                + "{\"response\":\"response\",\"done\":false}\n"
                + "{\"response\":\"\",\"done\":true}\n").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
