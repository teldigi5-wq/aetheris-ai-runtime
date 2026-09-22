package io.aetheris.orchestrator.syntracore;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase13OwnerRuntimeReadinessWiringTest {
    private static final String BASE_MODEL = "qwen2.5:7b";
    private static final String ADAPTER_ALIAS = "aetheris-owner-adapter:latest";
    private static final String DATASET_HASH = "a".repeat(64);
    private static final String ARTIFACT_HASH = "b".repeat(64);

    private HttpServer server;
    private AtomicReference<List<String>> providerModels;
    private AtomicInteger tagsCalls;
    private AtomicInteger generateCalls;

    @BeforeEach
    void startServer() throws IOException {
        providerModels = new AtomicReference<>(List.of(BASE_MODEL, ADAPTER_ALIAS));
        tagsCalls = new AtomicInteger();
        generateCalls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", this::handleTags);
        server.createContext("/api/generate", exchange -> {
            generateCalls.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void ownerRuntimeIsDisabledByDefaultAndPerformsNoProviderDiscovery() {
        runner().run(context -> {
            assertFalse(context.containsBean("ownerLocalRuntimeBridge"));
            assertFalse(context.containsBean("ownerLocalRuntimeReadinessService"));
            assertEquals(0, tagsCalls.get());
            assertEquals(0, generateCalls.get());
        });
    }

    @Test
    void enabledWiringIsStartupSideEffectFreeAndReadinessUsesReadOnlyDiscovery() {
        runner().withPropertyValues(enabledProperties()).run(context -> {
            assertNotNull(context.getBean(OllamaAdapterRuntimeBridge.class));
            assertNotNull(context.getBean(OwnerLocalRuntimeReadinessService.class));
            assertEquals(0, tagsCalls.get(), "startup must not probe the owner runtime");
            assertEquals(0, generateCalls.get());

            HealthIndicator indicator = context.getBean("ownerLocalRuntimeHealthIndicator", HealthIndicator.class);
            assertEquals(Status.UP, indicator.health().getStatus());
            assertEquals(1, tagsCalls.get());
            assertEquals(0, generateCalls.get(), "readiness must not invoke generation");
        });
    }

    @Test
    void missingConfiguredAdapterAliasFailsReadinessClosedWithoutGeneration() {
        providerModels.set(List.of(BASE_MODEL));

        runner().withPropertyValues(enabledProperties()).run(context -> {
            OwnerLocalRuntimeReadiness report = context
                    .getBean(OwnerLocalRuntimeReadinessService.class)
                    .check();

            assertFalse(report.ready());
            assertTrue(report.providerReachable());
            assertEquals(List.of(ADAPTER_ALIAS), report.missingAdapterAliases());
            assertEquals(0, generateCalls.get());
        });
    }

    @Test
    void nonLoopbackEndpointIsRejectedWhenOwnerRuntimeIsEnabled() {
        String[] properties = enabledProperties();
        properties[1] = "aetheris.syntra.owner-runtime.endpoint=http://example.com:11434";

        runner().withPropertyValues(properties).run(context -> {
            assertNotNull(context.getStartupFailure());
            assertEquals(0, tagsCalls.get());
            assertEquals(0, generateCalls.get());
        });
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(OwnerLocalRuntimeConfiguration.class);
    }

    private String[] enabledProperties() {
        return new String[] {
                "aetheris.syntra.owner-runtime.enabled=true",
                "aetheris.syntra.owner-runtime.endpoint=http://127.0.0.1:" + server.getAddress().getPort(),
                "aetheris.syntra.owner-runtime.base-models[0].model-id=" + BASE_MODEL,
                "aetheris.syntra.owner-runtime.base-models[0].capabilities[0]=CHAT",
                "aetheris.syntra.owner-runtime.base-models[0].context-window-tokens=8192",
                "aetheris.syntra.owner-runtime.base-models[0].required-ram-mb=1024",
                "aetheris.syntra.owner-runtime.base-models[0].cpu-fallback-supported=true",
                "aetheris.syntra.owner-runtime.adapters[0].experiment-id=exp-slice16-readiness",
                "aetheris.syntra.owner-runtime.adapters[0].candidate-adapter-address=aetheris-adapter://candidate/exp-slice16-readiness",
                "aetheris.syntra.owner-runtime.adapters[0].promoted-adapter-address=aetheris-adapter://promoted/exp-slice16-readiness",
                "aetheris.syntra.owner-runtime.adapters[0].base-model-id=" + BASE_MODEL,
                "aetheris.syntra.owner-runtime.adapters[0].dataset-hash=" + DATASET_HASH,
                "aetheris.syntra.owner-runtime.adapters[0].artifact-sha256=" + ARTIFACT_HASH,
                "aetheris.syntra.owner-runtime.adapters[0].provider-model-id=" + ADAPTER_ALIAS,
                "aetheris.syntra.owner-runtime.adapters[0].capabilities[0]=CHAT",
                "aetheris.syntra.owner-runtime.adapters[0].capabilities[1]=CODING",
                "aetheris.syntra.owner-runtime.adapters[0].context-window-tokens=8192",
                "aetheris.syntra.owner-runtime.adapters[0].required-ram-mb=1024",
                "aetheris.syntra.owner-runtime.adapters[0].cpu-fallback-supported=true"
        };
    }

    private void handleTags(HttpExchange exchange) throws IOException {
        tagsCalls.incrementAndGet();
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
}
