package io.aetheris.orchestrator.syntracore;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase13ContextAwareLocalInferenceTest {

    @Test
    void routesCodingTaskStreamsAndPreservesContextEvidence() {
        ContextPack context = contextPack();
        AtomicReference<LocalInferenceResult> evaluated = new AtomicReference<>();
        FakeRuntime runtime = new FakeRuntime(
                candidate("local-test", "coder-local", Set.of(ModelCapability.CHAT, ModelCapability.CODING), true, 0d),
                (sink, cancellationRequested) -> {
                    sink.accept(new ModelStreamChunk(0, "bounded ", false));
                    sink.accept(new ModelStreamChunk(1, "answer", true));
                });
        SyntraLocalInferenceOrchestrator orchestrator = new SyntraLocalInferenceOrchestrator(
                new SyntraModelRouter(),
                List.of(runtime),
                List.of(evaluated::set));

        LocalInferenceResult result = orchestrator.infer(
                new ContextAwareInferenceRequest(
                        InferenceTask.CODING,
                        context,
                        "Explain the local routing boundary.",
                        256,
                        4_096,
                        true),
                new HardwareCapacity(16_384, 0, false, HardwareEvidence.TARGET_PROFILE),
                () -> false);

        assertEquals(InferenceStatus.COMPLETED, result.status());
        assertEquals("bounded answer", result.output());
        assertEquals(2, result.emittedChunks());
        assertTrue(result.terminalObserved());
        assertEquals(context.citations(), result.evidenceAddresses());
        assertEquals(ExecutionTarget.CPU, result.routeSelection().orElseThrow().executionTarget());
        assertEquals(result, evaluated.get());
        assertEquals(1, runtime.streamCalls());
        assertEquals(context.citations(), runtime.lastInvocation().evidenceAddresses());
        assertTrue(runtime.lastInvocation().input().contains(context.citations().getFirst()));
        assertTrue(runtime.lastInvocation().input().contains("untrusted reference data"));
    }

    @Test
    void cancellationReturnsPartialOutputWithoutInventingTerminalCompletion() {
        ContextPack context = contextPack();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        FakeRuntime runtime = new FakeRuntime(
                candidate("local-test", "chat-local", Set.of(ModelCapability.CHAT), true, 0d),
                (sink, cancellationRequested) -> {
                    sink.accept(new ModelStreamChunk(0, "partial", false));
                    cancelled.set(true);
                    assertTrue(cancellationRequested.getAsBoolean());
                });
        SyntraLocalInferenceOrchestrator orchestrator = new SyntraLocalInferenceOrchestrator(
                new SyntraModelRouter(),
                List.of(runtime));

        LocalInferenceResult result = orchestrator.infer(
                new ContextAwareInferenceRequest(
                        InferenceTask.CHAT,
                        context,
                        "Stop when requested.",
                        128,
                        2_048,
                        true),
                new HardwareCapacity(8_192, 0, false, HardwareEvidence.TARGET_PROFILE),
                cancelled::get);

        assertEquals(InferenceStatus.CANCELLED, result.status());
        assertEquals("partial", result.output());
        assertEquals(1, result.emittedChunks());
        assertFalse(result.terminalObserved());
        assertEquals(context.citations(), result.evidenceAddresses());
    }

    @Test
    void rejectsDiscontinuousProviderStreams() {
        FakeRuntime runtime = new FakeRuntime(
                candidate("local-test", "chat-local", Set.of(ModelCapability.CHAT), true, 0d),
                (sink, cancellationRequested) -> sink.accept(new ModelStreamChunk(1, "out-of-order", true)));
        SyntraLocalInferenceOrchestrator orchestrator = new SyntraLocalInferenceOrchestrator(
                new SyntraModelRouter(),
                List.of(runtime));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> orchestrator.infer(
                        new ContextAwareInferenceRequest(
                                InferenceTask.CHAT,
                                contextPack(),
                                "Sequence check.",
                                64,
                                2_048,
                                true),
                        new HardwareCapacity(8_192, 0, false, HardwareEvidence.TARGET_PROFILE),
                        () -> false));

        assertTrue(failure.getMessage().contains("sequence discontinuity"));
    }

    @Test
    void localOrchestratorFailsClosedWhenOnlyRemoteOrPaidCandidatesExist() {
        ContextPack context = contextPack();
        FakeRuntime remote = new FakeRuntime(
                candidate("remote-test", "remote-chat", Set.of(ModelCapability.CHAT), false, 0d),
                Phase13ContextAwareLocalInferenceTest::unexpectedStream);
        FakeRuntime paidLocal = new FakeRuntime(
                candidate("paid-local", "paid-chat", Set.of(ModelCapability.CHAT), true, 0.01d),
                Phase13ContextAwareLocalInferenceTest::unexpectedStream);
        AtomicReference<LocalInferenceResult> evaluated = new AtomicReference<>();
        SyntraLocalInferenceOrchestrator orchestrator = new SyntraLocalInferenceOrchestrator(
                new SyntraModelRouter(),
                List.of(remote, paidLocal),
                List.of(evaluated::set));

        LocalInferenceResult result = orchestrator.infer(
                new ContextAwareInferenceRequest(
                        InferenceTask.CHAT,
                        context,
                        "Stay local and zero cost.",
                        64,
                        2_048,
                        false),
                new HardwareCapacity(8_192, 0, false, HardwareEvidence.TARGET_PROFILE),
                () -> false);

        assertEquals(InferenceStatus.UNAVAILABLE, result.status());
        assertTrue(result.routeSelection().isEmpty());
        assertTrue(result.routeRejections().contains("remote-test/remote-chat:PRIVATE_MODE_REQUIRES_LOCAL"));
        assertTrue(result.routeRejections().contains("paid-local/paid-chat:ZERO_COST_MODE_BLOCKED_COST"));
        assertEquals(context.citations(), result.evidenceAddresses());
        assertEquals(0, remote.streamCalls());
        assertEquals(0, paidLocal.streamCalls());
        assertEquals(result, evaluated.get());
    }

    @Test
    void toolPlanningCapabilityDoesNotCreateAnExecutionAuthoritySurface() {
        ContextAwareInferenceRequest request = new ContextAwareInferenceRequest(
                InferenceTask.TOOL_PLANNING,
                contextPack(),
                "Draft a plan only.",
                64,
                2_048,
                false);

        ModelRouteRequest routeRequest = request.routeRequest();

        assertTrue(routeRequest.requiredCapabilities().contains(ModelCapability.TOOL_CALLING));
        assertTrue(routeRequest.privateMode());
        assertTrue(routeRequest.zeroCostMode());
        assertTrue(routeRequest.requireStreaming());
        assertTrue(routeRequest.preferLocal());
    }

    private static ContextPack contextPack() {
        RetrievalScope scope = RetrievalScope.project("slice5-local-inference");
        UUID nodeId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        RetrievalEvidence source = new RetrievalEvidence(
                "aetheris-memory://project/slice5-local-inference/" + nodeId,
                nodeId,
                scope,
                "routing#local",
                "Local inference stays scoped and bounded.",
                0.91,
                "phase13-slice5-test",
                Instant.parse("2026-09-20T18:00:00Z"));
        ContextEvidence evidence = new ContextEvidence(source, source.excerpt());
        return new ContextPack(
                scope,
                "local inference boundary",
                List.of(evidence),
                List.of(evidence.citation()),
                evidence.estimatedTokens(),
                new ContextQualityAssessment(1, 1, 1, 0, 0, 0.91, 0.91, 0.91),
                Instant.parse("2026-09-20T18:01:00Z"));
    }

    private static ModelRuntimeCandidate candidate(
            String providerId,
            String modelId,
            Set<ModelCapability> capabilities,
            boolean local,
            double estimatedCostUsd) {
        return new ModelRuntimeCandidate(
                providerId,
                modelId,
                capabilities,
                8_192,
                local,
                true,
                true,
                false,
                0,
                1_024,
                true,
                0.9,
                20,
                30,
                0.01,
                0,
                estimatedCostUsd);
    }

    private static void unexpectedStream(
            Consumer<ModelStreamChunk> sink,
            BooleanSupplier cancellationRequested) {
        throw new AssertionError("unavailable candidates must not be invoked");
    }

    @FunctionalInterface
    private interface StreamScript {
        void run(Consumer<ModelStreamChunk> sink, BooleanSupplier cancellationRequested);
    }

    private static final class FakeRuntime implements SyntraModelRuntime {
        private final ModelRuntimeCandidate candidate;
        private final StreamScript streamScript;
        private ModelInvocation lastInvocation;
        private int streamCalls;

        private FakeRuntime(ModelRuntimeCandidate candidate, StreamScript streamScript) {
            this.candidate = candidate;
            this.streamScript = streamScript;
        }

        @Override
        public String providerId() {
            return candidate.providerId();
        }

        @Override
        public List<ModelRuntimeCandidate> models() {
            return List.of(candidate);
        }

        @Override
        public void stream(
                ModelInvocation invocation,
                Consumer<ModelStreamChunk> sink,
                BooleanSupplier cancellationRequested) {
            streamCalls++;
            lastInvocation = invocation;
            streamScript.run(sink, cancellationRequested);
        }

        private ModelInvocation lastInvocation() {
            return lastInvocation;
        }

        private int streamCalls() {
            return streamCalls;
        }
    }
}
