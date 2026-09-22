package io.aetheris.orchestrator.syntracore;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase13PointOfInvocationAdapterRevalidationTest {

    @Test
    void activeAdapterIsObservedAtCatalogAdmissionAndImmediatelyBeforeStreaming() {
        AdapterArtifactIdentity identity = identity("exp-slice12-active", "b");
        SequenceObservedRuntime runtime = runtime(
                identity,
                active(identity, "catalog"),
                active(identity, "invocation"));
        ContextPack context = contextPack();

        LocalInferenceResult result = orchestrator(runtime).infer(
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
    void detachAfterRoutingFailsClosedBeforeRuntimeInvocation() {
        AdapterArtifactIdentity identity = identity("exp-slice12-detach", "c");
        SequenceObservedRuntime runtime = runtime(
                identity,
                active(identity, "catalog"),
                detached(identity, "invocation"));
        ContextPack context = contextPack();

        LocalInferenceResult result = orchestrator(runtime).infer(
                codingRequest(context),
                hardware(),
                () -> false);

        assertEquals(InferenceStatus.UNAVAILABLE, result.status());
        assertTrue(result.routeSelection().isEmpty());
        assertEquals(context.citations(), result.evidenceAddresses());
        assertTrue(result.routeRejections().stream()
                .anyMatch(reason -> reason.contains("immediately before invocation")));
        assertEquals(2, runtime.observationCalls);
        assertEquals(0, runtime.streamCalls);
    }

    @Test
    void disappearanceAfterRoutingFailsClosedBeforeRuntimeInvocation() {
        AdapterArtifactIdentity identity = identity("exp-slice12-missing", "d");
        SequenceObservedRuntime runtime = runtime(
                identity,
                active(identity, "catalog"),
                missing(identity, "invocation"));

        LocalInferenceResult result = orchestrator(runtime).infer(
                codingRequest(contextPack()),
                hardware(),
                () -> false);

        assertEquals(InferenceStatus.UNAVAILABLE, result.status());
        assertEquals(2, runtime.observationCalls);
        assertEquals(0, runtime.streamCalls);
    }

    @Test
    void identityDriftAfterRoutingFailsClosedBeforeRuntimeInvocation() {
        AdapterArtifactIdentity identity = identity("exp-slice12-drift", "e");
        AdapterArtifactIdentity drifted = identity("exp-slice12-other", "f");
        SequenceObservedRuntime runtime = runtime(
                identity,
                active(identity, "catalog"),
                active(drifted, "invocation"));

        LocalInferenceResult result = orchestrator(runtime).infer(
                codingRequest(contextPack()),
                hardware(),
                () -> false);

        assertEquals(InferenceStatus.UNAVAILABLE, result.status());
        assertEquals(2, runtime.observationCalls);
        assertEquals(0, runtime.streamCalls);
    }

    @Test
    void cancellationBeforeInvocationRevalidationDoesNotPerformAnotherObservationOrStream() {
        AdapterArtifactIdentity identity = identity("exp-slice12-cancel", "1");
        SequenceObservedRuntime runtime = runtime(
                identity,
                active(identity, "catalog"));

        LocalInferenceResult result = orchestrator(runtime).infer(
                codingRequest(contextPack()),
                hardware(),
                () -> true);

        assertEquals(InferenceStatus.CANCELLED, result.status());
        assertEquals(1, runtime.observationCalls);
        assertEquals(0, runtime.streamCalls);
    }

    private static SyntraLocalInferenceOrchestrator orchestrator(SyntraModelRuntime runtime) {
        return new SyntraLocalInferenceOrchestrator(new SyntraModelRouter(), List.of(runtime));
    }

    private static SequenceObservedRuntime runtime(
            AdapterArtifactIdentity identity,
            AdapterArtifactObservation... observations) {
        String provider = "slice12-local-runtime";
        ModelRuntimeCandidate base = candidate(
                provider,
                identity.baseModelId(),
                Set.of(ModelCapability.CHAT),
                0.70);
        ModelRuntimeCandidate adapted = candidate(
                provider,
                AdapterRuntimeRegistration.modelIdFor(identity),
                Set.of(ModelCapability.CHAT, ModelCapability.CODING),
                0.96);
        AdapterRuntimeRegistration registration = new AdapterRuntimeRegistration(
                identity,
                activated(identity),
                adapted);
        return new SequenceObservedRuntime(base, registration, List.of(observations));
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
                "phase13-slice12-test-runtime",
                "aetheris-adapter-observation://slice12/" + phase + "/active/" + identity.artifactSha256());
    }

    private static AdapterArtifactObservation detached(
            AdapterArtifactIdentity identity,
            String phase) {
        return new AdapterArtifactObservation(
                identity,
                true,
                false,
                "phase13-slice12-test-runtime",
                "aetheris-adapter-observation://slice12/" + phase + "/detached/" + identity.artifactSha256());
    }

    private static AdapterArtifactObservation missing(
            AdapterArtifactIdentity identity,
            String phase) {
        return new AdapterArtifactObservation(
                identity,
                false,
                false,
                "phase13-slice12-test-runtime",
                "aetheris-adapter-observation://slice12/" + phase + "/missing/" + identity.artifactSha256());
    }

    private static HardwareCapacity hardware() {
        return new HardwareCapacity(16_384, 0, false, HardwareEvidence.TARGET_PROFILE);
    }

    private static ContextAwareInferenceRequest codingRequest(ContextPack context) {
        return new ContextAwareInferenceRequest(
                InferenceTask.CODING,
                context,
                "Invoke only an adapter that is still active at point of invocation.",
                128,
                4_096,
                true);
    }

    private static ContextPack contextPack() {
        RetrievalScope scope = RetrievalScope.project("slice12-point-of-invocation-revalidation");
        UUID nodeId = UUID.fromString("12121212-3434-5656-7878-909090909090");
        RetrievalEvidence source = new RetrievalEvidence(
                "aetheris-memory://project/slice12-point-of-invocation-revalidation/" + nodeId,
                nodeId,
                scope,
                "adapter#point-of-invocation",
                "Adapter state must remain verified active immediately before local streaming begins.",
                0.98,
                "phase13-slice12-test",
                Instant.parse("2026-09-21T12:30:00Z"));
        ContextEvidence evidence = new ContextEvidence(source, source.excerpt());
        return new ContextPack(
                scope,
                "point of invocation adapter revalidation",
                List.of(evidence),
                List.of(evidence.citation()),
                evidence.estimatedTokens(),
                new ContextQualityAssessment(1, 1, 1, 0, 0, 0.98, 0.98, 0.98),
                Instant.parse("2026-09-21T12:31:00Z"));
    }

    private static final class SequenceObservedRuntime implements AdapterInvocationLeasingRuntime {
        private final ModelRuntimeCandidate base;
        private final AdapterRuntimeRegistration registration;
        private final Deque<AdapterArtifactObservation> observations;
        private int observationCalls;
        private int streamCalls;

        private SequenceObservedRuntime(
                ModelRuntimeCandidate base,
                AdapterRuntimeRegistration registration,
                List<AdapterArtifactObservation> observations) {
            this.base = base;
            this.registration = registration;
            this.observations = new ArrayDeque<>(observations);
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
            return Optional.ofNullable(observations.pollFirst());
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
                    "aetheris-adapter-lease://slice12/" + registration.identity().artifactSha256()));
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
            sink.accept(new ModelStreamChunk(0, "revalidated ", false));
            sink.accept(new ModelStreamChunk(1, "adapter", true));
        }
    }
}
