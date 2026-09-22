package io.aetheris.orchestrator.syntracore;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase13LiveAdapterRegistrationReconciliationTest {

    @Test
    void activeObservationKeepsVerifiedRegistrationRoutableAndPreservesEvidence() {
        AdapterArtifactIdentity identity = identity("exp-slice11", "b");
        MutableObservedRuntime runtime = runtime(identity, active(identity));
        SyntraLocalInferenceOrchestrator orchestrator = orchestrator(runtime);
        ContextPack context = contextPack();

        LocalInferenceResult result = orchestrator.infer(
                codingRequest(context),
                hardware(),
                () -> false);

        assertEquals(InferenceStatus.COMPLETED, result.status());
        assertEquals(AdapterRuntimeRegistration.modelIdFor(identity),
                result.routeSelection().orElseThrow().modelId());
        assertEquals(context.citations(), result.evidenceAddresses());
        assertEquals(2, runtime.observationCalls);
        assertEquals(1, runtime.streamCalls);
    }

    @Test
    void registrationIsReconciledOnEveryInferenceAndDetachRemovesEligibility() {
        AdapterArtifactIdentity identity = identity("exp-reconcile", "c");
        MutableObservedRuntime runtime = runtime(identity, active(identity));
        SyntraLocalInferenceOrchestrator orchestrator = orchestrator(runtime);

        LocalInferenceResult first = orchestrator.infer(
                codingRequest(contextPack()),
                hardware(),
                () -> false);
        assertEquals(InferenceStatus.COMPLETED, first.status());

        runtime.observation.set(detached(identity));

        LocalInferenceResult second = orchestrator.infer(
                codingRequest(contextPack()),
                hardware(),
                () -> false);

        assertEquals(InferenceStatus.UNAVAILABLE, second.status());
        assertTrue(second.routeSelection().isEmpty());
        assertEquals(3, runtime.observationCalls);
        assertEquals(1, runtime.streamCalls);
    }

    @Test
    void missingArtifactAndIdentityDriftAreFilteredBeforeRouting() {
        AdapterArtifactIdentity identity = identity("exp-filter", "d");
        MutableObservedRuntime runtime = runtime(identity, missing(identity));
        SyntraLocalInferenceOrchestrator orchestrator = orchestrator(runtime);

        LocalInferenceResult missing = orchestrator.infer(
                codingRequest(contextPack()),
                hardware(),
                () -> false);
        assertEquals(InferenceStatus.UNAVAILABLE, missing.status());
        assertEquals(0, runtime.streamCalls);

        AdapterArtifactIdentity drifted = identity("exp-drifted", "e");
        runtime.observation.set(active(drifted));

        LocalInferenceResult drift = orchestrator.infer(
                codingRequest(contextPack()),
                hardware(),
                () -> false);
        assertEquals(InferenceStatus.UNAVAILABLE, drift.status());
        assertEquals(0, runtime.streamCalls);
        assertEquals(2, runtime.observationCalls);
    }

    @Test
    void defaultObservationContractFailsClosedWithoutBreakingSourceCompatibility() {
        AdapterArtifactIdentity identity = identity("exp-default-empty", "f");
        ModelRuntimeCandidate base = baseCandidate("legacy-adapter-runtime", identity.baseModelId());
        AdapterRuntimeRegistration registration = registration(
                identity,
                adapterCandidate("legacy-adapter-runtime", identity));

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
            public void stream(
                    ModelInvocation invocation,
                    Consumer<ModelStreamChunk> sink,
                    BooleanSupplier cancellationRequested) {
                throw new AssertionError("an unobserved adapter must never be invoked");
            }
        };

        Optional<AdapterArtifactObservation> defaultObservation = legacyRuntime.observeAdapter(identity);
        assertFalse(defaultObservation.isPresent());

        LocalInferenceResult result = orchestrator(legacyRuntime).infer(
                codingRequest(contextPack()),
                hardware(),
                () -> false);

        assertEquals(InferenceStatus.UNAVAILABLE, result.status());
        assertTrue(result.routeSelection().isEmpty());
    }

    private static SyntraLocalInferenceOrchestrator orchestrator(SyntraModelRuntime runtime) {
        return new SyntraLocalInferenceOrchestrator(new SyntraModelRouter(), List.of(runtime));
    }

    private static MutableObservedRuntime runtime(
            AdapterArtifactIdentity identity,
            AdapterArtifactObservation observation) {
        String provider = "reconciled-local-runtime";
        return new MutableObservedRuntime(
                baseCandidate(provider, identity.baseModelId()),
                registration(identity, adapterCandidate(provider, identity)),
                observation);
    }

    private static AdapterRuntimeRegistration registration(
            AdapterArtifactIdentity identity,
            ModelRuntimeCandidate candidate) {
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
                candidate);
    }

    private static ModelRuntimeCandidate baseCandidate(String provider, String modelId) {
        return candidate(provider, modelId, Set.of(ModelCapability.CHAT), 0.70);
    }

    private static ModelRuntimeCandidate adapterCandidate(
            String provider,
            AdapterArtifactIdentity identity) {
        return candidate(
                provider,
                AdapterRuntimeRegistration.modelIdFor(identity),
                Set.of(ModelCapability.CHAT, ModelCapability.CODING),
                0.95);
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

    private static AdapterArtifactObservation active(AdapterArtifactIdentity identity) {
        return new AdapterArtifactObservation(
                identity,
                true,
                true,
                "phase13-slice11-test-runtime",
                "aetheris-adapter-observation://slice11/active/" + identity.artifactSha256());
    }

    private static AdapterArtifactObservation detached(AdapterArtifactIdentity identity) {
        return new AdapterArtifactObservation(
                identity,
                true,
                false,
                "phase13-slice11-test-runtime",
                "aetheris-adapter-observation://slice11/detached/" + identity.artifactSha256());
    }

    private static AdapterArtifactObservation missing(AdapterArtifactIdentity identity) {
        return new AdapterArtifactObservation(
                identity,
                false,
                false,
                "phase13-slice11-test-runtime",
                "aetheris-adapter-observation://slice11/missing/" + identity.artifactSha256());
    }

    private static HardwareCapacity hardware() {
        return new HardwareCapacity(16_384, 0, false, HardwareEvidence.TARGET_PROFILE);
    }

    private static ContextAwareInferenceRequest codingRequest(ContextPack context) {
        return new ContextAwareInferenceRequest(
                InferenceTask.CODING,
                context,
                "Use only a currently observed adapter registration.",
                128,
                4_096,
                true);
    }

    private static ContextPack contextPack() {
        RetrievalScope scope = RetrievalScope.project("slice11-live-registration-reconciliation");
        UUID nodeId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        RetrievalEvidence source = new RetrievalEvidence(
                "aetheris-memory://project/slice11-live-registration-reconciliation/" + nodeId,
                nodeId,
                scope,
                "adapter#live-reconciliation",
                "A verified registration must still be observed active before routing.",
                0.97,
                "phase13-slice11-test",
                Instant.parse("2026-09-21T10:45:00Z"));
        ContextEvidence evidence = new ContextEvidence(source, source.excerpt());
        return new ContextPack(
                scope,
                "live adapter registration reconciliation",
                List.of(evidence),
                List.of(evidence.citation()),
                evidence.estimatedTokens(),
                new ContextQualityAssessment(1, 1, 1, 0, 0, 0.97, 0.97, 0.97),
                Instant.parse("2026-09-21T10:46:00Z"));
    }

    private static final class MutableObservedRuntime implements AdapterInvocationLeasingRuntime {
        private final ModelRuntimeCandidate base;
        private final AdapterRuntimeRegistration registration;
        private final AtomicReference<AdapterArtifactObservation> observation;
        private int observationCalls;
        private int streamCalls;

        private MutableObservedRuntime(
                ModelRuntimeCandidate base,
                AdapterRuntimeRegistration registration,
                AdapterArtifactObservation observation) {
            this.base = base;
            this.registration = registration;
            this.observation = new AtomicReference<>(observation);
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
            return Optional.ofNullable(observation.get());
        }

        @Override
        public Optional<AdapterInvocationLease> acquireAdapterInvocationLease(
                AdapterRuntimeRegistration requestedRegistration) {
            if (!registration.equals(requestedRegistration)) {
                return Optional.empty();
            }
            return Optional.of(new AdapterInvocationLease(
                    registration.identity(),
                    providerId(),
                    registration.candidate().modelId(),
                    "aetheris-adapter-lease://slice11/" + registration.identity().artifactSha256()));
        }

        @Override
        public void streamWithAdapterLease(
                AdapterInvocationLease lease,
                ModelInvocation invocation,
                Consumer<ModelStreamChunk> sink,
                BooleanSupplier cancellationRequested) {
            stream(invocation, sink, cancellationRequested);
        }

        @Override
        public void stream(
                ModelInvocation invocation,
                Consumer<ModelStreamChunk> sink,
                BooleanSupplier cancellationRequested) {
            streamCalls++;
            sink.accept(new ModelStreamChunk(0, "live ", false));
            sink.accept(new ModelStreamChunk(1, "adapter", true));
        }
    }
}
