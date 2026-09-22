package io.aetheris.orchestrator.syntracore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class SyntraLocalInferenceOrchestrator {
    private static final String ADAPTER_INVOCATION_REVALIDATION_REJECTION =
            "selected adapter lost verified-active state immediately before invocation";
    private static final String ADAPTER_LEASE_UNSUPPORTED_REJECTION =
            "selected adapter runtime does not support invocation leases";
    private static final String ADAPTER_LEASE_UNAVAILABLE_REJECTION =
            "selected adapter invocation lease unavailable";
    private static final String ADAPTER_LEASE_MISMATCH_REJECTION =
            "selected adapter invocation lease does not match the selected registration";
    private static final String ADAPTER_HANDLE_UNAVAILABLE_REJECTION =
            "selected adapter bound invocation handle unavailable";
    private static final String ADAPTER_HANDLE_MISMATCH_REJECTION =
            "selected adapter bound invocation handle does not match the selected registration";

    private final SyntraModelRouter modelRouter;
    private final List<SyntraModelRuntime> runtimes;
    private final List<LocalInferenceEvaluationHook> evaluationHooks;

    public SyntraLocalInferenceOrchestrator(
            SyntraModelRouter modelRouter,
            List<SyntraModelRuntime> runtimes) {
        this(modelRouter, runtimes, List.of());
    }

    public SyntraLocalInferenceOrchestrator(
            SyntraModelRouter modelRouter,
            List<SyntraModelRuntime> runtimes,
            List<LocalInferenceEvaluationHook> evaluationHooks) {
        this.modelRouter = Objects.requireNonNull(modelRouter, "modelRouter");
        this.runtimes = List.copyOf(Objects.requireNonNull(runtimes, "runtimes"));
        this.evaluationHooks = List.copyOf(Objects.requireNonNull(evaluationHooks, "evaluationHooks"));
        for (SyntraModelRuntime runtime : this.runtimes) {
            Objects.requireNonNull(runtime, "runtime");
        }
        for (LocalInferenceEvaluationHook hook : this.evaluationHooks) {
            Objects.requireNonNull(hook, "evaluationHook");
        }
    }

    public LocalInferenceResult infer(
            ContextAwareInferenceRequest request,
            HardwareCapacity hardware,
            BooleanSupplier cancellationRequested) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(hardware, "hardware");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");

        RuntimeCatalog catalog = buildCatalog();
        ModelRouteDecision decision = modelRouter.route(
                request.routeRequest(),
                hardware,
                catalog.candidates());

        if (!decision.selected()) {
            return evaluate(LocalInferenceResult.unavailable(
                    request.task(),
                    request.contextPack().citations(),
                    decision.rejectionReasons()));
        }

        ModelRouteSelection selection = decision.selection().orElseThrow();
        String selectedCandidateKey = selection.providerId() + "/" + selection.modelId();
        ModelRuntimeCandidate selectedCandidate = catalog.candidatesByKey().get(selectedCandidateKey);
        if (selectedCandidate == null) {
            throw new IllegalStateException("router selected a model outside the runtime catalog");
        }
        if (!selectedCandidate.local() || selection.executionTarget() == ExecutionTarget.REMOTE) {
            throw new IllegalStateException("local inference orchestration cannot select a remote runtime");
        }

        SyntraModelRuntime runtime = catalog.runtimesByProvider().get(selection.providerId());
        if (runtime == null) {
            throw new IllegalStateException("router selected an unknown runtime provider");
        }

        ModelInvocation invocation = request.contextPack().toModelInvocation(
                selection.modelId(),
                request.userInput(),
                request.maxOutputTokens());
        if (!request.contextPack().citations().equals(invocation.evidenceAddresses())) {
            throw new IllegalStateException("context citations changed before model invocation");
        }

        if (cancellationRequested.getAsBoolean()) {
            return evaluate(LocalInferenceResult.cancelled(
                    request.task(),
                    selection,
                    "",
                    invocation.evidenceAddresses(),
                    decision.rejectionReasons(),
                    0));
        }

        AdapterRuntimeRegistration selectedAdapterRegistration =
                catalog.adapterRegistrationsByCandidateKey().get(selectedCandidateKey);
        AdapterRuntimeInvocationHandle adapterInvocationHandle = null;
        AdapterInvocationLease adapterInvocationLease = null;
        if (selectedAdapterRegistration != null) {
            if (!(runtime instanceof AdapterInvocationLeasingRuntime leasingRuntime)) {
                return unavailableWithAdditionalRejection(
                        request,
                        invocation,
                        decision,
                        ADAPTER_LEASE_UNSUPPORTED_REJECTION);
            }

            Optional<AdapterArtifactObservation> invocationObservation = Objects.requireNonNull(
                    leasingRuntime.observeAdapter(selectedAdapterRegistration.identity()),
                    "adapter invocation observation");
            if (!isVerifiedCurrentObservation(selectedAdapterRegistration, invocationObservation)) {
                return unavailableWithAdditionalRejection(
                        request,
                        invocation,
                        decision,
                        ADAPTER_INVOCATION_REVALIDATION_REJECTION);
            }

            if (runtime instanceof AdapterInvocationHandleRuntime handleRuntime) {
                Optional<AdapterRuntimeInvocationHandle> acquiredHandle = Objects.requireNonNull(
                        handleRuntime.acquireAdapterInvocationHandle(selectedAdapterRegistration),
                        "adapter invocation handle");
                if (acquiredHandle.isEmpty()) {
                    return unavailableWithAdditionalRejection(
                            request,
                            invocation,
                            decision,
                            ADAPTER_HANDLE_UNAVAILABLE_REJECTION);
                }

                AdapterRuntimeInvocationHandle handle = acquiredHandle.orElseThrow();
                AdapterInvocationLease lease = handle.lease();
                if (!lease.matches(selectedAdapterRegistration)
                        || !lease.providerId().equals(selection.providerId())
                        || !lease.modelId().equals(selection.modelId())
                        || !lease.modelId().equals(invocation.modelId())
                        || handle.started()) {
                    return unavailableWithAdditionalRejection(
                            request,
                            invocation,
                            decision,
                            ADAPTER_HANDLE_MISMATCH_REJECTION);
                }
                adapterInvocationHandle = handle;
            } else {
                Optional<AdapterInvocationLease> acquiredLease = Objects.requireNonNull(
                        leasingRuntime.acquireAdapterInvocationLease(selectedAdapterRegistration),
                        "adapter invocation lease");
                if (acquiredLease.isEmpty()) {
                    return unavailableWithAdditionalRejection(
                            request,
                            invocation,
                            decision,
                            ADAPTER_LEASE_UNAVAILABLE_REJECTION);
                }

                AdapterInvocationLease lease = acquiredLease.orElseThrow();
                if (!lease.matches(selectedAdapterRegistration)
                        || !lease.providerId().equals(selection.providerId())
                        || !lease.modelId().equals(selection.modelId())
                        || !lease.modelId().equals(invocation.modelId())) {
                    return unavailableWithAdditionalRejection(
                            request,
                            invocation,
                            decision,
                            ADAPTER_LEASE_MISMATCH_REJECTION);
                }

                adapterInvocationLease = lease;
            }

            if (adapterInvocationLease != null) {
                adapterInvocationHandle = new AdapterRuntimeInvocationHandle(
                        adapterInvocationLease,
                        leasingRuntime::streamWithAdapterLease);
            }
        }

        StringBuilder output = new StringBuilder();
        long[] expectedSequence = {0L};
        int[] emittedChunks = {0};
        boolean[] terminalObserved = {false};

        Consumer<ModelStreamChunk> guardedSink = chunk -> {
            Objects.requireNonNull(chunk, "stream chunk");
            if (cancellationRequested.getAsBoolean()) {
                return;
            }
            if (terminalObserved[0]) {
                throw new IllegalStateException("runtime emitted a chunk after the terminal frame");
            }
            if (chunk.sequence() != expectedSequence[0]) {
                throw new IllegalStateException(
                        "runtime stream sequence discontinuity: expected "
                                + expectedSequence[0]
                                + " but received "
                                + chunk.sequence());
            }
            expectedSequence[0]++;
            emittedChunks[0]++;
            output.append(chunk.text());
            if (chunk.terminal()) {
                terminalObserved[0] = true;
            }
        };

        if (adapterInvocationHandle != null) {
            adapterInvocationHandle.stream(
                    invocation,
                    guardedSink,
                    cancellationRequested);
        } else {
            runtime.stream(invocation, guardedSink, cancellationRequested);
        }

        if (terminalObserved[0]) {
            return evaluate(LocalInferenceResult.completed(
                    request.task(),
                    selection,
                    output.toString(),
                    invocation.evidenceAddresses(),
                    decision.rejectionReasons(),
                    emittedChunks[0]));
        }
        if (cancellationRequested.getAsBoolean()) {
            return evaluate(LocalInferenceResult.cancelled(
                    request.task(),
                    selection,
                    output.toString(),
                    invocation.evidenceAddresses(),
                    decision.rejectionReasons(),
                    emittedChunks[0]));
        }
        throw new IllegalStateException("runtime stream ended without cancellation or a terminal frame");
    }

    private LocalInferenceResult unavailableWithAdditionalRejection(
            ContextAwareInferenceRequest request,
            ModelInvocation invocation,
            ModelRouteDecision decision,
            String rejection) {
        List<String> rejections = new ArrayList<>(decision.rejectionReasons());
        rejections.add(rejection);
        return evaluate(LocalInferenceResult.unavailable(
                request.task(),
                invocation.evidenceAddresses(),
                rejections));
    }

    private RuntimeCatalog buildCatalog() {
        Map<String, SyntraModelRuntime> runtimesByProvider = new LinkedHashMap<>();
        Map<String, ModelRuntimeCandidate> candidatesByKey = new LinkedHashMap<>();
        Map<String, AdapterRuntimeRegistration> adapterRegistrationsByCandidateKey = new LinkedHashMap<>();
        List<ModelRuntimeCandidate> candidates = new ArrayList<>();

        for (SyntraModelRuntime runtime : runtimes) {
            String providerId = runtime.providerId();
            if (providerId == null || providerId.isBlank()) {
                throw new IllegalStateException("runtime providerId must not be blank");
            }
            if (runtimesByProvider.putIfAbsent(providerId, runtime) != null) {
                throw new IllegalStateException("duplicate runtime providerId: " + providerId);
            }

            List<ModelRuntimeCandidate> runtimeModels = Objects.requireNonNull(
                    runtime.models(),
                    "runtime models");
            for (ModelRuntimeCandidate candidate : runtimeModels) {
                Objects.requireNonNull(candidate, "runtime candidate");
                if (!providerId.equals(candidate.providerId())) {
                    throw new IllegalStateException("runtime candidate providerId does not match its provider");
                }
                if (AdapterRuntimeRegistration.isAdapterModelId(candidate.modelId())) {
                    throw new IllegalStateException(
                            "adapter model IDs must enter the catalog through verified adapter registrations");
                }
                addCandidate(candidate, candidatesByKey, candidates);
            }

            if (runtime instanceof AdapterAwareSyntraModelRuntime adapterRuntime) {
                List<AdapterRuntimeRegistration> registrations = Objects.requireNonNull(
                        adapterRuntime.adapterRegistrations(),
                        "adapter registrations");
                for (AdapterRuntimeRegistration registration : registrations) {
                    Objects.requireNonNull(registration, "adapter registration");
                    ModelRuntimeCandidate candidate = registration.candidate();
                    if (!providerId.equals(candidate.providerId())) {
                        throw new IllegalStateException(
                                "adapter registration providerId does not match its runtime provider");
                    }
                    boolean baseModelPresent = runtimeModels.stream()
                            .filter(Objects::nonNull)
                            .anyMatch(model -> providerId.equals(model.providerId())
                                    && registration.identity().baseModelId().equals(model.modelId())
                                    && model.local());
                    if (!baseModelPresent) {
                        throw new IllegalStateException(
                                "adapter registration base model is not present on the same local runtime");
                    }

                    Optional<AdapterArtifactObservation> currentObservation = Objects.requireNonNull(
                            adapterRuntime.observeAdapter(registration.identity()),
                            "adapter observation");
                    if (!isVerifiedCurrentObservation(registration, currentObservation)) {
                        continue;
                    }

                    addCandidate(candidate, candidatesByKey, candidates);
                    adapterRegistrationsByCandidateKey.put(candidate.key(), registration);
                }
            }
        }

        return new RuntimeCatalog(
                Map.copyOf(runtimesByProvider),
                Map.copyOf(candidatesByKey),
                Map.copyOf(adapterRegistrationsByCandidateKey),
                List.copyOf(candidates));
    }

    private static boolean isVerifiedCurrentObservation(
            AdapterRuntimeRegistration registration,
            Optional<AdapterArtifactObservation> observation) {
        if (observation.isEmpty()) {
            return false;
        }
        AdapterArtifactObservation observed = observation.orElseThrow();
        return registration.identity().equals(observed.identity())
                && observed.exists()
                && observed.active();
    }

    private static void addCandidate(
            ModelRuntimeCandidate candidate,
            Map<String, ModelRuntimeCandidate> candidatesByKey,
            List<ModelRuntimeCandidate> candidates) {
        if (candidatesByKey.putIfAbsent(candidate.key(), candidate) != null) {
            throw new IllegalStateException("duplicate runtime candidate key: " + candidate.key());
        }
        candidates.add(candidate);
    }

    private LocalInferenceResult evaluate(LocalInferenceResult result) {
        for (LocalInferenceEvaluationHook hook : evaluationHooks) {
            hook.evaluate(result);
        }
        return result;
    }

    private record RuntimeCatalog(
            Map<String, SyntraModelRuntime> runtimesByProvider,
            Map<String, ModelRuntimeCandidate> candidatesByKey,
            Map<String, AdapterRuntimeRegistration> adapterRegistrationsByCandidateKey,
            List<ModelRuntimeCandidate> candidates) {
    }
}
