package io.aetheris.orchestrator.syntracore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Concrete loopback-only bridge that connects pre-provisioned Ollama aliases to
 * the certified adapter activation, registration, lease, and bound-handle path.
 *
 * <p>Activation is process-local Aetheris eligibility. The bridge never installs,
 * downloads, trains, or mutates provider model artifacts.</p>
 */
public final class OllamaAdapterRuntimeBridge
        implements LocalAdapterActivationPort, AdapterInvocationHandleRuntime {

    private static final String VERIFIER_ID = "ollama-loopback-adapter-bridge-v1";

    private final OllamaLocalRuntime baseRuntime;
    private final Map<AdapterArtifactIdentity, OllamaAdapterBinding> bindings;
    private final Map<AdapterArtifactIdentity, OllamaLocalRuntime> adapterTransports;
    private final Set<AdapterArtifactIdentity> activeBindings = new LinkedHashSet<>();
    private final Map<AdapterArtifactIdentity, AdapterRuntimeRegistration> registrations = new LinkedHashMap<>();

    public OllamaAdapterRuntimeBridge(
            LocalRuntimeEndpoint endpoint,
            LocalRuntimeBounds bounds,
            List<ModelRuntimeCandidate> baseModels,
            List<OllamaAdapterBinding> adapterBindings) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(bounds, "bounds");
        baseModels = List.copyOf(Objects.requireNonNull(baseModels, "baseModels"));
        adapterBindings = List.copyOf(Objects.requireNonNull(adapterBindings, "adapterBindings"));
        this.baseRuntime = new OllamaLocalRuntime(endpoint, bounds, baseModels);

        Set<String> baseModelIds = new LinkedHashSet<>();
        for (ModelRuntimeCandidate base : baseModels) {
            Objects.requireNonNull(base, "baseModel");
            baseModelIds.add(base.modelId());
        }

        Map<AdapterArtifactIdentity, OllamaAdapterBinding> bindingMap = new LinkedHashMap<>();
        Map<AdapterArtifactIdentity, OllamaLocalRuntime> transportMap = new LinkedHashMap<>();
        Set<String> providerAliases = new LinkedHashSet<>();
        for (OllamaAdapterBinding binding : adapterBindings) {
            Objects.requireNonNull(binding, "adapterBinding");
            if (!baseModelIds.contains(binding.identity().baseModelId())) {
                throw new IllegalArgumentException(
                        "adapter binding base model must be configured on the same Ollama runtime");
            }
            if (bindingMap.putIfAbsent(binding.identity(), binding) != null) {
                throw new IllegalArgumentException("duplicate adapter artifact identity binding");
            }
            if (!providerAliases.add(binding.providerModelId())) {
                throw new IllegalArgumentException("duplicate Ollama provider adapter alias");
            }
            transportMap.put(
                    binding.identity(),
                    new OllamaLocalRuntime(endpoint, bounds, List.of(binding.transportCandidate())));
        }
        this.bindings = Map.copyOf(bindingMap);
        this.adapterTransports = Map.copyOf(transportMap);
    }

    @Override
    public String providerId() {
        return OllamaLocalRuntime.PROVIDER_ID;
    }

    @Override
    public List<ModelRuntimeCandidate> models() {
        return baseRuntime.models();
    }

    public LocalProviderDiscovery discoverModels() {
        return baseRuntime.discoverModels();
    }

    @Override
    public void stream(
            ModelInvocation invocation,
            Consumer<ModelStreamChunk> sink,
            BooleanSupplier cancellationRequested) {
        if (AdapterRuntimeRegistration.isAdapterModelId(invocation.modelId())) {
            throw new IllegalArgumentException("adapter selections must use the bound lease/handle path");
        }
        baseRuntime.stream(invocation, sink, cancellationRequested);
    }

    @Override
    public synchronized AdapterArtifactObservation inspect(AdapterArtifactIdentity expectedIdentity) {
        Objects.requireNonNull(expectedIdentity, "expectedIdentity");
        OllamaAdapterBinding binding = bindings.get(expectedIdentity);
        if (binding == null) {
            return observation(expectedIdentity, false, false, "unbound");
        }
        boolean exists = providerAliasExists(binding.providerModelId());
        boolean active = exists && activeBindings.contains(expectedIdentity);
        return observation(expectedIdentity, exists, active, active ? "active" : exists ? "present" : "missing");
    }

    @Override
    public synchronized AdapterArtifactObservation activate(AdapterArtifactIdentity expectedIdentity) {
        OllamaAdapterBinding binding = requireBinding(expectedIdentity);
        boolean exists = providerAliasExists(binding.providerModelId());
        if (exists) {
            activeBindings.add(expectedIdentity);
        } else {
            activeBindings.remove(expectedIdentity);
            registrations.remove(expectedIdentity);
        }
        return observation(expectedIdentity, exists, exists, exists ? "activated" : "activation-missing");
    }

    @Override
    public synchronized AdapterArtifactObservation detach(AdapterArtifactIdentity expectedIdentity) {
        OllamaAdapterBinding binding = requireBinding(expectedIdentity);
        activeBindings.remove(expectedIdentity);
        registrations.remove(expectedIdentity);
        boolean exists = providerAliasExists(binding.providerModelId());
        return observation(expectedIdentity, exists, false, exists ? "detached" : "detached-missing");
    }

    /**
     * Reconciles already-produced Slice 9 lifecycle truth into runtime routing.
     * The bridge cannot mint ACTIVATED_VERIFIED truth itself.
     */
    public synchronized Optional<AdapterRuntimeRegistration> reconcileLifecycle(AdapterLifecycleResult lifecycle) {
        Objects.requireNonNull(lifecycle, "lifecycle");
        AdapterArtifactIdentity identity = lifecycle.identity();
        OllamaAdapterBinding binding = requireBinding(identity);

        if (lifecycle.status() == AdapterLifecycleStatus.ACTIVATED_VERIFIED) {
            AdapterArtifactObservation observation = inspect(identity);
            if (!observation.exists() || !observation.active()) {
                registrations.remove(identity);
                throw new IllegalStateException(
                        "verified lifecycle activation is not currently active on the bound Ollama alias");
            }
            AdapterRuntimeRegistration registration = new AdapterRuntimeRegistration(
                    identity,
                    lifecycle,
                    binding.routingCandidate());
            registrations.put(identity, registration);
            return Optional.of(registration);
        }

        registrations.remove(identity);
        if (lifecycle.status() == AdapterLifecycleStatus.ROLLED_BACK_VERIFIED && inspect(identity).active()) {
            throw new IllegalStateException("verified rollback conflicts with current Ollama bridge activation state");
        }
        return Optional.empty();
    }

    @Override
    public synchronized List<AdapterRuntimeRegistration> adapterRegistrations() {
        return registrations.values().stream()
                .sorted(Comparator.comparing(registration -> registration.candidate().modelId()))
                .toList();
    }

    @Override
    public synchronized Optional<AdapterArtifactObservation> observeAdapter(AdapterArtifactIdentity identity) {
        return Optional.of(inspect(identity));
    }

    @Override
    public synchronized Optional<AdapterInvocationLease> acquireAdapterInvocationLease(
            AdapterRuntimeRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        AdapterRuntimeRegistration current = registrations.get(registration.identity());
        if (!registration.equals(current)) {
            return Optional.empty();
        }
        AdapterArtifactObservation observation = inspect(registration.identity());
        if (!observation.exists() || !observation.active()) {
            return Optional.empty();
        }
        return Optional.of(new AdapterInvocationLease(
                registration.identity(),
                providerId(),
                registration.candidate().modelId(),
                "aetheris-adapter-lease://ollama/" + registration.identity().artifactSha256()));
    }

    @Override
    public synchronized Optional<AdapterRuntimeInvocationHandle> acquireAdapterInvocationHandle(
            AdapterRuntimeRegistration registration) {
        return acquireAdapterInvocationLease(registration)
                .map(lease -> new AdapterRuntimeInvocationHandle(lease, this::streamWithAdapterLease));
    }

    @Override
    public synchronized void streamWithAdapterLease(
            AdapterInvocationLease lease,
            ModelInvocation invocation,
            Consumer<ModelStreamChunk> sink,
            BooleanSupplier cancellationRequested) {
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");

        AdapterRuntimeRegistration registration = registrations.get(lease.identity());
        if (registration == null
                || !lease.matches(registration)
                || !lease.modelId().equals(invocation.modelId())) {
            throw new IllegalStateException("adapter lease no longer matches the reconciled Ollama registration");
        }
        AdapterArtifactObservation observation = inspect(lease.identity());
        if (!observation.exists() || !observation.active()) {
            throw new IllegalStateException("adapter binding is no longer active at Ollama stream start");
        }

        OllamaAdapterBinding binding = requireBinding(lease.identity());
        ModelInvocation providerInvocation = new ModelInvocation(
                binding.providerModelId(),
                invocation.input(),
                invocation.maxOutputTokens(),
                invocation.evidenceAddresses());
        adapterTransports.get(lease.identity()).stream(providerInvocation, sink, cancellationRequested);
    }

    private boolean providerAliasExists(String providerModelId) {
        LocalProviderDiscovery discovery = baseRuntime.discoverModels();
        return discovery.reachable() && discovery.modelIds().contains(providerModelId);
    }

    private OllamaAdapterBinding requireBinding(AdapterArtifactIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        OllamaAdapterBinding binding = bindings.get(identity);
        if (binding == null) {
            throw new IllegalArgumentException("adapter artifact identity is not explicitly bound to this Ollama runtime");
        }
        return binding;
    }

    private static AdapterArtifactObservation observation(
            AdapterArtifactIdentity identity,
            boolean exists,
            boolean active,
            String state) {
        return new AdapterArtifactObservation(
                identity,
                exists,
                active,
                VERIFIER_ID,
                "aetheris-adapter-observation://ollama/"
                        + state
                        + "/"
                        + identity.artifactSha256());
    }
}
