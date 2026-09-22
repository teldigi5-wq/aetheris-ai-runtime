package io.aetheris.orchestrator.syntracore;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase13AdapterInvocationLeaseTest {

    @Test
    void exactLeaseBindsSelectedAdapterAndUsesOnlyLeaseAwareStreamPath() {
        AdapterArtifactIdentity identity = identity("exp-slice13-exact", "2");
        LeaseRuntime runtime = runtime(identity, LeaseBehavior.EXACT);
        ContextPack context = contextPack();

        LocalInferenceResult result = orchestrator(runtime).infer(
                request(InferenceTask.CODING, context),
                hardware(),
                () -> false);

        assertEquals(InferenceStatus.COMPLETED, result.status());
        assertEquals("leased adapter", result.output());
        assertEquals(AdapterRuntimeRegistration.modelIdFor(identity),
                result.routeSelection().orElseThrow().modelId());
        assertEquals(context.citations(), result.evidenceAddresses());
        assertEquals(2, runtime.observationCalls);
        assertEquals(1, runtime.leaseCalls);
        assertEquals(1, runtime.leaseStreamCalls);
        assertEquals(0, runtime.normalStreamCalls);
        assertTrue(runtime.lastLease.matches(runtime.registration));
    }

    @Test
    void adapterAwareRuntimeWithoutLeaseSupportFailsClosedBeforeInvocation() {
        AdapterArtifactIdentity identity = identity("exp-slice13-legacy", "3");
        AdapterRuntimeRegistration registration = registration(identity, "legacy-adapter-runtime");
        ModelRuntimeCandidate base = baseCandidate("legacy-adapter-runtime", identity.baseModelId());

        AdapterAwareSyntraModelRuntime legacyRuntime = new AdapterAwareSyntraModelRuntime() {
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
                return Optional.of(active(requestedIdentity, "legacy"));
            }

            @Override
            public void stream(
                    ModelInvocation invocation,
                    Consumer<ModelStreamChunk> sink,
                    BooleanSupplier cancellationRequested) {
                throw new AssertionError("adapter without lease support must never reach ordinary stream");
            }
        };

        LocalInferenceResult result = orchestrator(legacyRuntime).infer(
                request(InferenceTask.CODING, contextPack()),
                hardware(),
                () -> false);

        assertEquals(InferenceStatus.UNAVAILABLE, result.status());
        assertTrue(result.routeSelection().isEmpty());
        assertTrue(result.routeRejections().stream()
                .anyMatch(reason -> reason.contains("does not support invocation leases")));
    }

    @Test
    void missingRuntimeLeaseFailsClosedWithoutStreaming() {
        AdapterArtifactIdentity identity = identity("exp-slice13-empty", "4");
        LeaseRuntime runtime = runtime(identity, LeaseBehavior.EMPTY);

        LocalInferenceResult result = orchestrator(runtime).infer(
                request(InferenceTask.CODING, contextPack()),
                hardware(),
                () -> false);

        assertEquals(InferenceStatus.UNAVAILABLE, result.status());
        assertTrue(result.routeRejections().stream()
                .anyMatch(reason -> reason.contains("invocation lease unavailable")));
        assertEquals(2, runtime.observationCalls);
        assertEquals(1, runtime.leaseCalls);
        assertEquals(0, runtime.leaseStreamCalls);
        assertEquals(0, runtime.normalStreamCalls);
    }

    @Test
    void mismatchedRuntimeLeaseFailsClosedWithoutStreaming() {
        AdapterArtifactIdentity identity = identity("exp-slice13-mismatch", "5");
        LeaseRuntime runtime = runtime(identity, LeaseBehavior.WRONG_PROVIDER);

        LocalInferenceResult result = orchestrator(runtime).infer(
                request(InferenceTask.CODING, contextPack()),
                hardware(),
                () -> false);

        assertEquals(InferenceStatus.UNAVAILABLE, result.status());
        assertTrue(result.routeRejections().stream()
                .anyMatch(reason -> reason.contains("does not match the selected registration")));
        assertEquals(1, runtime.leaseCalls);
        assertEquals(0, runtime.leaseStreamCalls);
        assertEquals(0, runtime.normalStreamCalls);
    }

    @Test
    void cancellationBeforeLeaseAcquisitionPreservesExistingCancellationSemantics() {
        AdapterArtifactIdentity identity = identity("exp-slice13-cancel", "6");
        LeaseRuntime runtime = runtime(identity, LeaseBehavior.EXACT);

        LocalInferenceResult result = orchestrator(runtime).infer(
                request(InferenceTask.CODING, contextPack()),
                hardware(),
                () -> true);

        assertEquals(InferenceStatus.CANCELLED, result.status());
        assertEquals(1, runtime.observationCalls);
        assertEquals(0, runtime.leaseCalls);
        assertEquals(0, runtime.leaseStreamCalls);
        assertEquals(0, runtime.normalStreamCalls);
    }

    @Test
    void ordinaryBaseModelStillUsesExistingNormalStreamPath() {
        ModelRuntimeCandidate base = candidate(
                "plain-local-runtime",
                "plain-base",
                Set.of(ModelCapability.CHAT),
                0.8);
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
                sink.accept(new ModelStreamChunk(0, "plain base", true));
            }
        };

        LocalInferenceResult result = orchestrator(runtime).infer(
                request(InferenceTask.CHAT, contextPack()),
                hardware(),
                () -> false);

        assertEquals(InferenceStatus.COMPLETED, result.status());
        assertEquals("plain base", result.output());
        assertEquals(1, normalStreamCalls[0]);
    }

    @Test
    void leaseConstructorRejectsModelIdentityDriftAndInvalidEvidenceNamespace() {
        AdapterArtifactIdentity identity = identity("exp-slice13-invalid", "7");

        assertThrows(IllegalArgumentException.class, () -> new AdapterInvocationLease(
                identity,
                "slice13-local-runtime",
                "wrong-model",
                "aetheris-adapter-lease://invalid/model"));
        assertThrows(IllegalArgumentException.class, () -> new AdapterInvocationLease(
                identity,
                "slice13-local-runtime",
                AdapterRuntimeRegistration.modelIdFor(identity),
                "https://example.invalid/lease"));
    }

    private static SyntraLocalInferenceOrchestrator orchestrator(SyntraModelRuntime runtime) {
        return new SyntraLocalInferenceOrchestrator(new SyntraModelRouter(), List.of(runtime));
    }

    private static LeaseRuntime runtime(
            AdapterArtifactIdentity identity,
            LeaseBehavior behavior) {
        String provider = "slice13-local-runtime";
        return new LeaseRuntime(
                baseCandidate(provider, identity.baseModelId()),
                registration(identity, provider),
                behavior);
    }

    private static AdapterRuntimeRegistration registration(
            AdapterArtifactIdentity identity,
            String provider) {
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

    private static AdapterArtifactObservation active(
            AdapterArtifactIdentity identity,
            String phase) {
        return new AdapterArtifactObservation(
                identity,
                true,
                true,
                "phase13-slice13-test-runtime",
                "aetheris-adapter-observation://slice13/" + phase + "/" + identity.artifactSha256());
    }

    private static AdapterInvocationLease exactLease(
            AdapterRuntimeRegistration registration,
            String provider) {
        return new AdapterInvocationLease(
                registration.identity(),
                provider,
                registration.candidate().modelId(),
                "aetheris-adapter-lease://slice13/exact/" + registration.identity().artifactSha256());
    }

    private static ContextAwareInferenceRequest request(
            InferenceTask task,
            ContextPack context) {
        return new ContextAwareInferenceRequest(
                task,
                context,
                "Execute only through the correct typed adapter invocation lease.",
                128,
                4_096,
                true);
    }

    private static HardwareCapacity hardware() {
        return new HardwareCapacity(16_384, 0, false, HardwareEvidence.TARGET_PROFILE);
    }

    private static ContextPack contextPack() {
        RetrievalScope scope = RetrievalScope.project("slice13-adapter-invocation-lease");
        UUID nodeId = UUID.fromString("13131313-2424-3535-4646-575757575757");
        RetrievalEvidence source = new RetrievalEvidence(
                "aetheris-memory://project/slice13-adapter-invocation-lease/" + nodeId,
                nodeId,
                scope,
                "adapter#invocation-lease",
                "Adapter execution requires a runtime-issued lease bound to the exact verified artifact and route.",
                0.99,
                "phase13-slice13-test",
                Instant.parse("2026-09-21T13:10:00Z"));
        ContextEvidence evidence = new ContextEvidence(source, source.excerpt());
        return new ContextPack(
                scope,
                "typed adapter invocation lease",
                List.of(evidence),
                List.of(evidence.citation()),
                evidence.estimatedTokens(),
                new ContextQualityAssessment(1, 1, 1, 0, 0, 0.99, 0.99, 0.99),
                Instant.parse("2026-09-21T13:11:00Z"));
    }

    private enum LeaseBehavior {
        EXACT,
        EMPTY,
        WRONG_PROVIDER
    }

    private static final class LeaseRuntime implements AdapterInvocationLeasingRuntime {
        private final ModelRuntimeCandidate base;
        private final AdapterRuntimeRegistration registration;
        private final LeaseBehavior behavior;
        private int observationCalls;
        private int leaseCalls;
        private int leaseStreamCalls;
        private int normalStreamCalls;
        private AdapterInvocationLease lastLease;

        private LeaseRuntime(
                ModelRuntimeCandidate base,
                AdapterRuntimeRegistration registration,
                LeaseBehavior behavior) {
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
                        "aetheris-adapter-lease://slice13/wrong-provider/"
                                + requestedRegistration.identity().artifactSha256()));
            };
        }

        @Override
        public void streamWithAdapterLease(
                AdapterInvocationLease lease,
                ModelInvocation invocation,
                Consumer<ModelStreamChunk> sink,
                BooleanSupplier cancellationRequested) {
            leaseStreamCalls++;
            lastLease = lease;
            sink.accept(new ModelStreamChunk(0, "leased ", false));
            sink.accept(new ModelStreamChunk(1, "adapter", true));
        }

        @Override
        public void stream(
                ModelInvocation invocation,
                Consumer<ModelStreamChunk> sink,
                BooleanSupplier cancellationRequested) {
            normalStreamCalls++;
            throw new AssertionError("adapter selections must not use ordinary runtime.stream");
        }
    }
}
