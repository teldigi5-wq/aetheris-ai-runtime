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

class Phase13AdapterAwareRuntimeRegistrationTest {

    @Test
    void verifiedActiveAdapterCanEnterRoutingCatalogWithoutChangingRouterSemantics() {
        AdapterArtifactIdentity identity = identity();
        ModelRuntimeCandidate base = candidate(
                "local-adapter-test",
                identity.baseModelId(),
                Set.of(ModelCapability.CHAT),
                0.70);
        ModelRuntimeCandidate adapted = candidate(
                "local-adapter-test",
                AdapterRuntimeRegistration.modelIdFor(identity),
                Set.of(ModelCapability.CHAT, ModelCapability.CODING),
                0.95);
        AdapterRuntimeRegistration registration = new AdapterRuntimeRegistration(
                identity,
                activated(identity),
                adapted);
        FakeAdapterRuntime runtime = new FakeAdapterRuntime(base, List.of(registration));
        SyntraLocalInferenceOrchestrator orchestrator = new SyntraLocalInferenceOrchestrator(
                new SyntraModelRouter(),
                List.of(runtime));
        ContextPack context = contextPack();

        LocalInferenceResult result = orchestrator.infer(
                new ContextAwareInferenceRequest(
                        InferenceTask.CODING,
                        context,
                        "Use the verified adapter registration only.",
                        128,
                        4_096,
                        true),
                new HardwareCapacity(16_384, 0, false, HardwareEvidence.TARGET_PROFILE),
                () -> false);

        assertEquals(InferenceStatus.COMPLETED, result.status());
        assertEquals("adapter answer", result.output());
        assertEquals(AdapterRuntimeRegistration.modelIdFor(identity),
                result.routeSelection().orElseThrow().modelId());
        assertEquals(AdapterRuntimeRegistration.modelIdFor(identity), runtime.lastInvocation.modelId());
        assertEquals(context.citations(), runtime.lastInvocation.evidenceAddresses());
        assertEquals(1, runtime.streamCalls);
    }

    @Test
    void rolledBackOrUnverifiedLifecycleCannotCreateRoutingRegistration() {
        AdapterArtifactIdentity identity = identity();
        ModelRuntimeCandidate adapted = candidate(
                "local-adapter-test",
                AdapterRuntimeRegistration.modelIdFor(identity),
                Set.of(ModelCapability.CHAT, ModelCapability.CODING),
                0.95);

        AdapterLifecycleResult rolledBack = new AdapterLifecycleResult(
                AdapterLifecycleStatus.ROLLED_BACK_VERIFIED,
                identity,
                true,
                true,
                false,
                true,
                true,
                List.of());
        AdapterLifecycleResult unverified = new AdapterLifecycleResult(
                AdapterLifecycleStatus.ACTIVATION_UNVERIFIED,
                identity,
                true,
                false,
                true,
                true,
                true,
                List.of());

        assertThrows(IllegalArgumentException.class,
                () -> new AdapterRuntimeRegistration(identity, rolledBack, adapted));
        assertThrows(IllegalArgumentException.class,
                () -> new AdapterRuntimeRegistration(identity, unverified, adapted));
    }

    @Test
    void exactVerifiedIdentityAndArtifactBoundModelIdAreRequired() {
        AdapterArtifactIdentity identity = identity();
        AdapterArtifactIdentity otherIdentity = new AdapterArtifactIdentity(
                "exp-other",
                "aetheris-adapter://candidate/exp-other",
                "aetheris-adapter://promoted/exp-other",
                "base-local",
                "c".repeat(64),
                "d".repeat(64),
                "aetheris-adapter-artifact://sha256/" + "d".repeat(64),
                true,
                false);
        ModelRuntimeCandidate wrongModelId = candidate(
                "local-adapter-test",
                AdapterRuntimeRegistration.modelIdFor(otherIdentity),
                Set.of(ModelCapability.CHAT, ModelCapability.CODING),
                0.95);

        assertThrows(IllegalArgumentException.class,
                () -> new AdapterRuntimeRegistration(identity, activated(otherIdentity), wrongModelId));
        assertThrows(IllegalArgumentException.class,
                () -> new AdapterRuntimeRegistration(identity, activated(identity), wrongModelId));
    }

    @Test
    void plainRuntimeCannotInjectReservedAdapterNamespace() {
        AdapterArtifactIdentity identity = identity();
        ModelRuntimeCandidate forged = candidate(
                "plain-runtime",
                AdapterRuntimeRegistration.modelIdFor(identity),
                Set.of(ModelCapability.CHAT),
                0.9);
        SyntraModelRuntime plainRuntime = new SyntraModelRuntime() {
            @Override
            public String providerId() {
                return "plain-runtime";
            }

            @Override
            public List<ModelRuntimeCandidate> models() {
                return List.of(forged);
            }

            @Override
            public void stream(
                    ModelInvocation invocation,
                    Consumer<ModelStreamChunk> sink,
                    BooleanSupplier cancellationRequested) {
                throw new AssertionError("reserved adapter namespace must be rejected before invocation");
            }
        };
        SyntraLocalInferenceOrchestrator orchestrator = new SyntraLocalInferenceOrchestrator(
                new SyntraModelRouter(),
                List.of(plainRuntime));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> orchestrator.infer(
                        request(InferenceTask.CHAT),
                        new HardwareCapacity(8_192, 0, false, HardwareEvidence.TARGET_PROFILE),
                        () -> false));

        assertTrue(failure.getMessage().contains("verified adapter registrations"));
    }

    @Test
    void adapterRegistrationRequiresItsBaseModelOnSameLocalProvider() {
        AdapterArtifactIdentity identity = identity();
        ModelRuntimeCandidate differentBase = candidate(
                "local-adapter-test",
                "different-base",
                Set.of(ModelCapability.CHAT),
                0.7);
        ModelRuntimeCandidate adapted = candidate(
                "local-adapter-test",
                AdapterRuntimeRegistration.modelIdFor(identity),
                Set.of(ModelCapability.CHAT),
                0.95);
        FakeAdapterRuntime runtime = new FakeAdapterRuntime(
                differentBase,
                List.of(new AdapterRuntimeRegistration(identity, activated(identity), adapted)));
        SyntraLocalInferenceOrchestrator orchestrator = new SyntraLocalInferenceOrchestrator(
                new SyntraModelRouter(),
                List.of(runtime));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> orchestrator.infer(
                        request(InferenceTask.CHAT),
                        new HardwareCapacity(8_192, 0, false, HardwareEvidence.TARGET_PROFILE),
                        () -> false));

        assertTrue(failure.getMessage().contains("base model is not present"));
    }

    @Test
    void adapterRegistrationRemainsLocalAndZeroCost() {
        AdapterArtifactIdentity identity = identity();
        ModelRuntimeCandidate remote = candidate(
                "local-adapter-test",
                AdapterRuntimeRegistration.modelIdFor(identity),
                Set.of(ModelCapability.CHAT),
                0.95,
                false,
                0d);
        ModelRuntimeCandidate paid = candidate(
                "local-adapter-test",
                AdapterRuntimeRegistration.modelIdFor(identity),
                Set.of(ModelCapability.CHAT),
                0.95,
                true,
                0.01d);

        assertThrows(IllegalArgumentException.class,
                () -> new AdapterRuntimeRegistration(identity, activated(identity), remote));
        assertThrows(IllegalArgumentException.class,
                () -> new AdapterRuntimeRegistration(identity, activated(identity), paid));
    }

    private static ContextAwareInferenceRequest request(InferenceTask task) {
        return new ContextAwareInferenceRequest(
                task,
                contextPack(),
                "Adapter registration boundary.",
                64,
                2_048,
                true);
    }

    private static AdapterArtifactIdentity identity() {
        return new AdapterArtifactIdentity(
                "exp-slice10",
                "aetheris-adapter://candidate/exp-slice10",
                "aetheris-adapter://promoted/exp-slice10",
                "base-local",
                "a".repeat(64),
                "b".repeat(64),
                "aetheris-adapter-artifact://sha256/" + "b".repeat(64),
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

    private static ModelRuntimeCandidate candidate(
            String providerId,
            String modelId,
            Set<ModelCapability> capabilities,
            double qualityScore) {
        return candidate(providerId, modelId, capabilities, qualityScore, true, 0d);
    }

    private static ModelRuntimeCandidate candidate(
            String providerId,
            String modelId,
            Set<ModelCapability> capabilities,
            double qualityScore,
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
                qualityScore,
                20,
                30,
                0.01,
                0,
                estimatedCostUsd);
    }

    private static ContextPack contextPack() {
        RetrievalScope scope = RetrievalScope.project("slice10-adapter-runtime-registration");
        UUID nodeId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        RetrievalEvidence source = new RetrievalEvidence(
                "aetheris-memory://project/slice10-adapter-runtime-registration/" + nodeId,
                nodeId,
                scope,
                "adapter#registration",
                "Only verified active local adapters may enter the adapter routing namespace.",
                0.95,
                "phase13-slice10-test",
                Instant.parse("2026-09-21T10:00:00Z"));
        ContextEvidence evidence = new ContextEvidence(source, source.excerpt());
        return new ContextPack(
                scope,
                "verified adapter routing registration",
                List.of(evidence),
                List.of(evidence.citation()),
                evidence.estimatedTokens(),
                new ContextQualityAssessment(1, 1, 1, 0, 0, 0.95, 0.95, 0.95),
                Instant.parse("2026-09-21T10:01:00Z"));
    }

    private static final class FakeAdapterRuntime implements AdapterInvocationLeasingRuntime {
        private final ModelRuntimeCandidate base;
        private final List<AdapterRuntimeRegistration> registrations;
        private ModelInvocation lastInvocation;
        private int streamCalls;

        private FakeAdapterRuntime(
                ModelRuntimeCandidate base,
                List<AdapterRuntimeRegistration> registrations) {
            this.base = base;
            this.registrations = List.copyOf(registrations);
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
            return registrations;
        }

        @Override
        public Optional<AdapterArtifactObservation> observeAdapter(AdapterArtifactIdentity identity) {
            return registrations.stream()
                    .filter(registration -> registration.identity().equals(identity))
                    .findFirst()
                    .map(registration -> new AdapterArtifactObservation(
                            identity,
                            true,
                            true,
                            "phase13-slice10-test-runtime",
                            "aetheris-adapter-observation://slice10/" + identity.artifactSha256()));
        }

        @Override
        public Optional<AdapterInvocationLease> acquireAdapterInvocationLease(
                AdapterRuntimeRegistration registration) {
            if (!registrations.contains(registration)) {
                return Optional.empty();
            }
            return Optional.of(new AdapterInvocationLease(
                    registration.identity(),
                    providerId(),
                    registration.candidate().modelId(),
                    "aetheris-adapter-lease://slice10/" + registration.identity().artifactSha256()));
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
            lastInvocation = invocation;
            sink.accept(new ModelStreamChunk(0, "adapter ", false));
            sink.accept(new ModelStreamChunk(1, "answer", true));
        }
    }
}
