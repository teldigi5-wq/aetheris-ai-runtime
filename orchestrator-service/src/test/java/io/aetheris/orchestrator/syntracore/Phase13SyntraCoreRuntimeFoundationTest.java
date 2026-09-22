package io.aetheris.orchestrator.syntracore;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class Phase13SyntraCoreRuntimeFoundationTest {

    private final SyntraModelRouter router = new SyntraModelRouter();

    @Test
    void privateAndZeroCostModesFailClosedBeforeRoutingToRemoteProviders() {
        ModelRouteRequest request = new ModelRouteRequest(
                Set.of(ModelCapability.CODING), 4096,
                true, true, true, true, true);

        ModelRouteDecision decision = router.route(
                request,
                targetHardware(),
                List.of(remotePremium(), localFast()));

        assertTrue(decision.selected());
        ModelRouteSelection selected = decision.selection().orElseThrow();
        assertEquals("ollama", selected.providerId());
        assertEquals("qwen-local", selected.modelId());
        assertEquals(ExecutionTarget.GPU, selected.executionTarget());
        assertTrue(decision.rejectionReasons().contains(
                "cloud/premium:PRIVATE_MODE_REQUIRES_LOCAL"));
    }

    @Test
    void zeroCostModeBlocksPricedProvidersEvenWhenPrivateModeIsOff() {
        ModelRouteRequest request = new ModelRouteRequest(
                Set.of(ModelCapability.CODING), 4096,
                true, false, true, true, false);

        ModelRouteDecision decision = router.route(
                request,
                targetHardware(),
                List.of(remotePremium(), localFast()));

        assertEquals("qwen-local", decision.selection().orElseThrow().modelId());
        assertTrue(decision.rejectionReasons().contains(
                "cloud/premium:ZERO_COST_MODE_BLOCKED_COST"));
    }

    @Test
    void oversizedGpuModelUsesCpuOnlyWhenFallbackIsExplicitlyAllowed() {
        ModelRouteRequest allowed = new ModelRouteRequest(
                Set.of(ModelCapability.CODING), 4096,
                true, true, true, true, true);
        ModelRouteDecision cpuFallback = router.route(
                allowed, targetHardware(), List.of(localLarge()));

        assertEquals(ExecutionTarget.CPU,
                cpuFallback.selection().orElseThrow().executionTarget());
        assertEquals("LOCAL_CPU_FALLBACK",
                cpuFallback.selection().orElseThrow().reason());

        ModelRouteRequest denied = new ModelRouteRequest(
                Set.of(ModelCapability.CODING), 4096,
                true, true, true, false, true);
        ModelRouteDecision noFallback = router.route(
                denied, targetHardware(), List.of(localLarge()));

        assertFalse(noFallback.selected());
        assertTrue(noFallback.rejectionReasons().contains(
                "ollama/qwen-large:INSUFFICIENT_VRAM"));
    }

    @Test
    void capabilityContextAndStreamingRequirementsAreHardFilters() {
        ModelRuntimeCandidate limited = new ModelRuntimeCandidate(
                "ollama", "chat-only", Set.of(ModelCapability.CHAT),
                2048, true, true, false, false,
                0, 3000, true,
                0.75, 180, 50, 0.01, 0, 0);

        ModelRouteRequest coding = new ModelRouteRequest(
                Set.of(ModelCapability.CODING), 4096,
                true, true, true, true, true);
        ModelRouteDecision decision = router.route(
                coding, targetHardware(), List.of(limited));

        assertFalse(decision.selected());
        assertTrue(decision.rejectionReasons().contains(
                "ollama/chat-only:CAPABILITY_MISMATCH"));
    }

    @Test
    void routingIsDeterministicForTheSameEvidence() {
        ModelRouteRequest request = new ModelRouteRequest(
                Set.of(ModelCapability.CODING), 4096,
                false, false, true, true, true);
        List<ModelRuntimeCandidate> candidates = List.of(localFast(), remoteNearPeer());

        ModelRouteDecision first = router.route(request, targetHardware(), candidates);
        ModelRouteDecision second = router.route(request, targetHardware(), candidates);

        assertEquals(first, second);
        assertEquals("qwen-local", first.selection().orElseThrow().modelId(),
                "local preference should win when measured quality is otherwise close");
    }

    @Test
    void targetHardwareProfileCannotBeReportedAsPhysicallyVerified() {
        HardwareCapacity hardware = targetHardware();
        assertEquals(HardwareEvidence.TARGET_PROFILE, hardware.evidence());
        assertFalse(hardware.physicallyVerified());
    }

    @Test
    void runtimeContractSupportsStreamingAndCooperativeCancellation() {
        SyntraModelRuntime runtime = new SyntraModelRuntime() {
            @Override
            public String providerId() {
                return "fake-local";
            }

            @Override
            public List<ModelRuntimeCandidate> models() {
                return List.of(localFast());
            }

            @Override
            public void stream(
                    ModelInvocation invocation,
                    Consumer<ModelStreamChunk> sink,
                    BooleanSupplier cancellationRequested) {
                List<ModelStreamChunk> chunks = List.of(
                        new ModelStreamChunk(0, "first", false),
                        new ModelStreamChunk(1, "second", false),
                        new ModelStreamChunk(2, "", true));
                for (ModelStreamChunk chunk : chunks) {
                    if (cancellationRequested.getAsBoolean()) {
                        return;
                    }
                    sink.accept(chunk);
                }
            }
        };

        List<ModelStreamChunk> observed = new ArrayList<>();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        runtime.stream(
                new ModelInvocation("qwen-local", "hello", 128),
                chunk -> {
                    observed.add(chunk);
                    cancelled.set(true);
                },
                cancelled::get);

        assertEquals(1, observed.size());
        assertEquals("first", observed.getFirst().text());
    }

    private static HardwareCapacity targetHardware() {
        return new HardwareCapacity(
                16 * 1024L,
                6 * 1024L,
                true,
                HardwareEvidence.TARGET_PROFILE);
    }

    private static ModelRuntimeCandidate localFast() {
        return new ModelRuntimeCandidate(
                "ollama", "qwen-local",
                Set.of(ModelCapability.CHAT, ModelCapability.CODING, ModelCapability.REASONING),
                8192, true, true, true, true,
                5000, 8000, true,
                0.86, 280, 42, 0.01, 4700, 0);
    }

    private static ModelRuntimeCandidate localLarge() {
        return new ModelRuntimeCandidate(
                "ollama", "qwen-large",
                Set.of(ModelCapability.CODING, ModelCapability.REASONING),
                16384, true, true, true, true,
                8192, 12000, true,
                0.92, 500, 30, 0.02, 7800, 0);
    }

    private static ModelRuntimeCandidate remotePremium() {
        return new ModelRuntimeCandidate(
                "cloud", "premium",
                Set.of(ModelCapability.CHAT, ModelCapability.CODING, ModelCapability.REASONING),
                32768, false, true, true, false,
                0, 0, false,
                0.98, 120, 80, 0.01, 0, 0.04);
    }

    private static ModelRuntimeCandidate remoteNearPeer() {
        return new ModelRuntimeCandidate(
                "cloud", "near-peer",
                Set.of(ModelCapability.CHAT, ModelCapability.CODING, ModelCapability.REASONING),
                16384, false, true, true, false,
                0, 0, false,
                0.87, 260, 43, 0.01, 0, 0);
    }
}
