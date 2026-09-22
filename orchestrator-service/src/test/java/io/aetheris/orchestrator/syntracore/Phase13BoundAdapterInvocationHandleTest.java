package io.aetheris.orchestrator.syntracore;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase13BoundAdapterInvocationHandleTest {

    @Test
    void exactNativeBoundHandleOwnsAdapterStreamStart() {
        AdapterArtifactIdentity identity = identity("exp-slice14-exact", "8");
        HandleRuntime runtime = runtime(identity, HandleBehavior.EXACT);
        ContextPack context = contextPack();

        LocalInferenceResult result = orchestrator(runtime).infer(
                request(InferenceTask.CODING, context), hardware(), () -> false);

        assertEquals(InferenceStatus.COMPLETED, result.status());
        assertEquals("bound handle", result.output());
        assertEquals(context.citations(), result.evidenceAddresses());
        assertEquals(2, runtime.observationCalls);
        assertEquals(1, runtime.handleCalls);
        assertEquals(1, runtime.leaseCalls);
        assertEquals(1, runtime.leaseStreamCalls);
        assertEquals(0, runtime.normalStreamCalls);
        assertTrue(runtime.lastHandle.started());
        assertTrue(runtime.lastHandle.lease().matches(runtime.registration));
    }

    @Test
    void leaseOnlyRuntimeIsCompatibilityBoundIntoSingleUseHandle() {
        AdapterArtifactIdentity identity = identity("exp-slice14-lease-only", "9");
        AdapterRuntimeRegistration registration = registration(identity, "slice14-lease-only-runtime");
        ModelRuntimeCandidate base = baseCandidate("slice14-lease-only-runtime", identity.baseModelId());
        int[] observationCalls = {0};
        int[] leaseCalls = {0};
        int[] leaseStreamCalls = {0};
        int[] normalStreamCalls = {0};

        AdapterInvocationLeasingRuntime runtime = new AdapterInvocationLeasingRuntime() {
            @Override
            public String providerId() {
                return base.providerId();
            }

            @Override
            public List<ModelRuntimeCandidate> models() {
                return List.of(base);
            }

            @Override
            public List<AdapterRuntimeRegistration> adapterRegistrations() {
                return List.of(registration);
            }

            @Override
            public Optional<AdapterArtifactObservation> observeAdapter(AdapterArtifactIdentity requestedIdentity) {
                observationCalls[0]++;
                return Optional.of(active(
                        requestedIdentity,
                        observationCalls[0] == 1 ? "catalog" : "invocation"));
            }

            @Override
            public Optional<AdapterInvocationLease> acquireAdapterInvocationLease(
                    AdapterRuntimeRegistration requestedRegistration) {
                leaseCalls[0]++;
                return Optional.of(exactLease(requestedRegistration, providerId()));
            }

            @Override
            public void streamWithAdapterLease(
                    AdapterInvocationLease lease,
                    ModelInvocation invocation,
                    Consumer<ModelStreamChunk> sink,
                    BooleanSupplier cancellationRequested) {
                leaseStreamCalls[0]++;
                sink.accept(new ModelStreamChunk(0, "compat", true));
            }

            @Override
            public void stream(
                    ModelInvocation invocation,
                    Consumer<ModelStreamChunk> sink,
                    BooleanSupplier cancellationRequested) {
                normalStreamCalls[0]++;
                throw new AssertionError("lease-only adapter compatibility must not use ordinary runtime.stream");
            }
        };

        LocalInferenceResult result = orchestrator(runtime).infer(
                request(InferenceTask.CODING, contextPack()), hardware(), () -> false);

        assertEquals(InferenceStatus.COMPLETED, result.status());
        assertEquals("compat", result.output());
        assertEquals(2, observationCalls[0]);
        assertEquals(1, leaseCalls[0]);
        assertEquals(1, leaseStreamCalls[0]);
        assertEquals(0, normalStreamCalls[0]);
    }

    @Test
    void missingNativeBoundHandleFailsClosedWithoutStreaming() {
        AdapterArtifactIdentity identity = identity("exp-slice14-empty", "b");
        HandleRuntime runtime = runtime(identity, HandleBehavior.EMPTY);

        LocalInferenceResult result = orchestrator(runtime).infer(
                request(InferenceTask.CODING, contextPack()), hardware(), () -> false);

        assertEquals(InferenceStatus.UNAVAILABLE, result.status());
        assertTrue(result.routeRejections().stream()
                .anyMatch(reason -> reason.contains("bound invocation handle unavailable")));
        assertEquals(2, runtime.observationCalls);
        assertEquals(1, runtime.handleCalls);
        assertEquals(0, runtime.leaseStreamCalls);
        assertEquals(0, runtime.normalStreamCalls);
    }

    @Test
    void providerMismatchedNativeHandleFailsClosedWithoutStreaming() {
        AdapterArtifactIdentity identity = identity("exp-slice14-mismatch", "c");
        HandleRuntime runtime = runtime(identity, HandleBehavior.WRONG_PROVIDER);

        LocalInferenceResult result = orchestrator(runtime).infer(
                request(InferenceTask.CODING, contextPack()), hardware(), () -> false);

        assertEquals(InferenceStatus.UNAVAILABLE, result.status());
        assertTrue(result.routeRejections().stream()
                .anyMatch(reason -> reason.contains("does not match the selected registration")));
        assertEquals(1, runtime.handleCalls);
        assertEquals(0, runtime.leaseStreamCalls);
        assertEquals(0, runtime.normalStreamCalls);
    }

    @Test
    void boundHandleIsSingleUseAndRejectsModelDrift() {
        AdapterArtifactIdentity identity = identity("exp-slice14-single-use", "d");
        AdapterRuntimeRegistration registration = registration(identity, "slice14-local-runtime");
        AdapterInvocationLease lease = exactLease(registration, registration.candidate().providerId());
        AtomicInteger streams = new AtomicInteger();
        AdapterRuntimeInvocationHandle handle = new AdapterRuntimeInvocationHandle(
                lease,
                (boundLease, invocation, sink, cancellationRequested) -> {
                    streams.incrementAndGet();
                    sink.accept(new ModelStreamChunk(0, "once", true));
                });

        ModelInvocation invocation = new ModelInvocation(
                lease.modelId(),
                "system\nuser",
                64,
                List.of("aetheris-memory://project/slice14/evidence"));

        assertFalse(handle.started());
        handle.stream(invocation, chunk -> { }, () -> false);
        assertTrue(handle.started());
        assertEquals(1, streams.get());
        assertThrows(IllegalStateException.class,
                () -> handle.stream(invocation, chunk -> { }, () -> false));

        ModelInvocation wrongModel = new ModelInvocation(
                identity.baseModelId(),
                "system\nuser",
                64,
                invocation.evidenceAddresses());
        AdapterRuntimeInvocationHandle fresh = new AdapterRuntimeInvocationHandle(
                lease,
                (boundLease, modelInvocation, sink, cancellationRequested) -> streams.incrementAndGet());
        assertThrows(IllegalArgumentException.class,
                () -> fresh.stream(wrongModel, chunk -> { }, () -> false));
        assertFalse(fresh.started());
        assertEquals(1, streams.get());
    }

    @Test
    void cancellationBeforeHandleAcquisitionPreservesCancellationSemantics() {
        AdapterArtifactIdentity identity = identity("exp-slice14-cancel", "e");
        HandleRuntime runtime = runtime(identity, HandleBehavior.EXACT);

        LocalInferenceResult result = orchestrator(runtime).infer(
                request(InferenceTask.CODING, contextPack()), hardware(), () -> true);

        assertEquals(InferenceStatus.CANCELLED, result.status());
        assertEquals(1, runtime.observationCalls);
        assertEquals(0, runtime.handleCalls);
        assertEquals(0, runtime.leaseCalls);
        assertEquals(0, runtime.leaseStreamCalls);
        assertEquals(0, runtime.normalStreamCalls);
    }

    @Test
    void ordinaryBaseModelStillUsesNormalRuntimeStream() {
        ModelRuntimeCandidate base = candidate(
                "slice14-plain-runtime", "slice14-plain-base", Set.of(ModelCapability.CHAT), 0.8);
        int[] normalStreamCalls = {0};
        SyntraModelRuntime runtime = new SyntraModelRuntime() {
            @Override
            public String providerId() {
                return base.providerId();
            }

            @Override
            public List<ModelRuntimeCandidate> models() {
                return List.of(base);
            }

            @Override
            public void stream(
                    ModelInvocation invocation,
                    Consumer<ModelStreamChunk> sink,
                    BooleanSupplier cancellationRequested) {
                normalStreamCalls[0]++;
                sink.accept(new ModelStreamChunk(0, "plain", true));
            }
        };

        LocalInferenceResult result = orchestrator(runtime).infer(
                request(InferenceTask.CHAT, contextPack()), hardware(), () -> false);

        assertEquals(InferenceStatus.COMPLETED, result.status());
        assertEquals("plain", result.output());
        assertEquals(1, normalStreamCalls[0]);
    }

    private static SyntraLocalInferenceOrchestrator orchestrator(SyntraModelRuntime runtime) {
        return new SyntraLocalInferenceOrchestrator(new SyntraModelRouter(), List.of(runtime));
    }

    private static HandleRuntime runtime(AdapterArtifactIdentity identity, HandleBehavior behavior) {
        String provider = "slice14-local-runtime";
        return new HandleRuntime(
                baseCandidate(provider, identity.baseModelId()),
                registration(identity, provider),
                behavior);
    }

    private static AdapterRuntimeRegistration registration(AdapterArtifactIdentity identity, String provider) {
        return new AdapterRuntimeRegistration(
                identity,
                new AdapterLifecycleResult(
                        AdapterLifecycleStatus.ACTIVATED_VERIFIED,
                        identity,
                        true,
                        true,
                        true,
                        true,
                        true,
                        List.of()),
                candidate(
                        provider,
                        AdapterRuntimeRegistration.modelIdFor(identity),
                        Set.of(ModelCapability.CHAT, ModelCapability.CODING),
                        0.99));
    }

    private static ModelRuntimeCandidate baseCandidate(String provider, String modelId) {
        return candidate(provider, modelId, Set.of(ModelCapability.CHAT), 0.70);
    }

    private static ModelRuntimeCandidate candidate(
            String provider,
            String modelId,
            Set<ModelCapability> capabilities,
            double quality) {
        return new ModelRuntimeCandidate(
                provider,
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
                20,
                30,
                0.01,
                0,
                0d);
    }

    private static AdapterArtifactIdentity identity(String experimentId, String digestCharacter) {
        String artifactSha = digestCharacter.repeat(64);
        return new AdapterArtifactIdentity(
                experimentId,
                "aetheris-adapter://candidate/" + experimentId,
                "aetheris-adapter://promoted/" + experimentId,
                "base-local",
                "a".repeat(64),
                artifactSha,
                "aetheris-adapter-artifact://sha256/" + artifactSha,
                true,
                false);
    }

    private static AdapterArtifactObservation active(AdapterArtifactIdentity identity, String phase) {
        return new AdapterArtifactObservation(
                identity,
                true,
                true,
                "phase13-slice14-test-runtime",
                "aetheris-adapter-observation://slice14/" + phase + "/" + identity.artifactSha256());
    }

    private static AdapterInvocationLease exactLease(AdapterRuntimeRegistration registration, String provider) {
        return new AdapterInvocationLease(
                registration.identity(),
                provider,
                registration.candidate().modelId(),
                "aetheris-adapter-lease://slice14/exact/" + registration.identity().artifactSha256());
    }

    private static ContextAwareInferenceRequest request(InferenceTask task, ContextPack context) {
        return new ContextAwareInferenceRequest(
                task,
                context,
                "Execute only through the runtime-bound adapter invocation handle.",
                128,
                4_096,
                true);
    }

    private static HardwareCapacity hardware() {
        return new HardwareCapacity(16_384, 0, false, HardwareEvidence.TARGET_PROFILE);
    }

    private static ContextPack contextPack() {
        RetrievalScope scope = RetrievalScope.project("slice14-bound-adapter-invocation-handle");
        UUID nodeId = UUID.fromString("14141414-2525-3636-4747-585858585858");
        RetrievalEvidence source = new RetrievalEvidence(
                "aetheris-memory://project/slice14-bound-adapter-invocation-handle/" + nodeId,
                nodeId,
                scope,
                "adapter#bound-invocation-handle",
                "Adapter streaming starts only through a single-use handle bound to the exact verified lease.",
                0.99,
                "phase13-slice14-test",
                Instant.parse("2026-09-21T14:00:00Z"));
        ContextEvidence evidence = new ContextEvidence(source, source.excerpt());
        return new ContextPack(
                scope,
                "bound adapter invocation handle",
                List.of(evidence),
                List.of(evidence.citation()),
                evidence.estimatedTokens(),
                new ContextQualityAssessment(1, 1, 1, 0, 0, 0.99, 0.99, 0.99),
                Instant.parse("2026-09-21T14:01:00Z"));
    }

    private enum HandleBehavior {
        EXACT,
        EMPTY,
        WRONG_PROVIDER
    }

    private static final class HandleRuntime implements AdapterInvocationHandleRuntime {
        private final ModelRuntimeCandidate base;
        private final AdapterRuntimeRegistration registration;
        private final HandleBehavior behavior;
        private int observationCalls;
        private int handleCalls;
        private int leaseCalls;
        private int leaseStreamCalls;
        private int normalStreamCalls;
        private AdapterRuntimeInvocationHandle lastHandle;

        private HandleRuntime(
                ModelRuntimeCandidate base,
                AdapterRuntimeRegistration registration,
                HandleBehavior behavior) {
            this.base = base;
            this.registration = registration;
            this.behavior = behavior;
        }

        @Override
        public String providerId() {
            return base.providerId();
        }

        @Override
        public List<ModelRuntimeCandidate> models() {
            return List.of(base);
        }

        @Override
        public List<AdapterRuntimeRegistration> adapterRegistrations() {
            return List.of(registration);
        }

        @Override
        public Optional<AdapterArtifactObservation> observeAdapter(AdapterArtifactIdentity identity) {
            observationCalls++;
            return Optional.of(active(identity, observationCalls == 1 ? "catalog" : "invocation"));
        }

        @Override
        public Optional<AdapterInvocationLease> acquireAdapterInvocationLease(
                AdapterRuntimeRegistration requestedRegistration) {
            leaseCalls++;
            return switch (behavior) {
                case EXACT -> Optional.of(exactLease(requestedRegistration, providerId()));
                case EMPTY -> Optional.empty();
                case WRONG_PROVIDER -> Optional.of(new AdapterInvocationLease(
                        requestedRegistration.identity(),
                        "wrong-provider",
                        requestedRegistration.candidate().modelId(),
                        "aetheris-adapter-lease://slice14/wrong-provider/"
                                + requestedRegistration.identity().artifactSha256()));
            };
        }

        @Override
        public Optional<AdapterRuntimeInvocationHandle> acquireAdapterInvocationHandle(
                AdapterRuntimeRegistration requestedRegistration) {
            handleCalls++;
            if (behavior == HandleBehavior.EMPTY) {
                return Optional.empty();
            }
            Optional<AdapterInvocationLease> lease = acquireAdapterInvocationLease(requestedRegistration);
            return lease.map(value -> {
                AdapterRuntimeInvocationHandle handle = new AdapterRuntimeInvocationHandle(
                        value,
                        this::streamWithAdapterLease);
                lastHandle = handle;
                return handle;
            });
        }

        @Override
        public void streamWithAdapterLease(
                AdapterInvocationLease lease,
                ModelInvocation invocation,
                Consumer<ModelStreamChunk> sink,
                BooleanSupplier cancellationRequested) {
            leaseStreamCalls++;
            sink.accept(new ModelStreamChunk(0, "bound ", false));
            sink.accept(new ModelStreamChunk(1, "handle", true));
        }

        @Override
        public void stream(
                ModelInvocation invocation,
                Consumer<ModelStreamChunk> sink,
                BooleanSupplier cancellationRequested) {
            normalStreamCalls++;
            throw new AssertionError("adapter selections must execute through the bound handle");
        }
    }
}
