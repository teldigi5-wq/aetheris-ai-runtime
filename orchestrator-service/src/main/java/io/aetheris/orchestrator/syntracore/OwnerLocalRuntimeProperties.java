package io.aetheris.orchestrator.syntracore;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Opt-in owner-machine configuration for the certified local Syntra runtime path.
 *
 * <p>No installed model, adapter artifact, hardware characteristic, or performance
 * value is inferred by this configuration contract. Every routable identity is
 * supplied explicitly by the owner/deployment.</p>
 */
@ConfigurationProperties(prefix = "aetheris.syntra.owner-runtime")
public record OwnerLocalRuntimeProperties(
        boolean enabled,
        URI endpoint,
        List<BaseModel> baseModels,
        List<AdapterBinding> adapters) {

    public OwnerLocalRuntimeProperties {
        endpoint = endpoint == null ? URI.create("http://127.0.0.1:11434") : endpoint;
        baseModels = baseModels == null ? List.of() : List.copyOf(baseModels);
        adapters = adapters == null ? List.of() : List.copyOf(adapters);
    }

    public void validateEnabledConfiguration() {
        if (!enabled) {
            return;
        }
        new LocalRuntimeEndpoint(endpoint);
        if (baseModels.isEmpty()) {
            throw new IllegalArgumentException("enabled owner runtime requires at least one explicit base model");
        }

        Set<String> baseIds = new LinkedHashSet<>();
        for (BaseModel base : baseModels) {
            Objects.requireNonNull(base, "baseModel");
            if (!baseIds.add(base.modelId())) {
                throw new IllegalArgumentException("duplicate owner-runtime base model: " + base.modelId());
            }
        }

        Set<String> artifactHashes = new LinkedHashSet<>();
        Set<String> providerAliases = new LinkedHashSet<>();
        for (AdapterBinding adapter : adapters) {
            Objects.requireNonNull(adapter, "adapterBinding");
            if (!baseIds.contains(adapter.baseModelId())) {
                throw new IllegalArgumentException(
                        "owner-runtime adapter base model must be present in the same configured runtime");
            }
            if (!artifactHashes.add(adapter.artifactSha256())) {
                throw new IllegalArgumentException("duplicate owner-runtime adapter artifact SHA-256");
            }
            if (!providerAliases.add(adapter.providerModelId())) {
                throw new IllegalArgumentException("duplicate owner-runtime Ollama adapter alias");
            }
        }
    }

    public LocalRuntimeEndpoint localEndpoint() {
        validateEnabledConfiguration();
        return new LocalRuntimeEndpoint(endpoint);
    }

    public List<ModelRuntimeCandidate> baseCandidates() {
        validateEnabledConfiguration();
        return baseModels.stream().map(BaseModel::candidate).toList();
    }

    public List<OllamaAdapterBinding> adapterBindings() {
        validateEnabledConfiguration();
        return adapters.stream().map(AdapterBinding::binding).toList();
    }

    public Set<String> expectedBaseModelIds() {
        return baseModels.stream()
                .map(BaseModel::modelId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Set<String> expectedAdapterAliases() {
        return adapters.stream()
                .map(AdapterBinding::providerModelId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public record BaseModel(
            String modelId,
            Set<ModelCapability> capabilities,
            int contextWindowTokens,
            long requiredRamMb,
            boolean cpuFallbackSupported) {

        public BaseModel {
            modelId = requireText(modelId, "baseModels.modelId");
            capabilities = requireCapabilities(capabilities, "baseModels.capabilities");
            requirePositive(contextWindowTokens, "baseModels.contextWindowTokens");
            requireNonNegative(requiredRamMb, "baseModels.requiredRamMb");
        }

        ModelRuntimeCandidate candidate() {
            return candidateFor(modelId, capabilities, contextWindowTokens, requiredRamMb, cpuFallbackSupported);
        }
    }

    public record AdapterBinding(
            String experimentId,
            String candidateAdapterAddress,
            String promotedAdapterAddress,
            String baseModelId,
            String datasetHash,
            String artifactSha256,
            String providerModelId,
            Set<ModelCapability> capabilities,
            int contextWindowTokens,
            long requiredRamMb,
            boolean cpuFallbackSupported) {

        public AdapterBinding {
            experimentId = requireText(experimentId, "adapters.experimentId");
            candidateAdapterAddress = requireText(candidateAdapterAddress, "adapters.candidateAdapterAddress");
            promotedAdapterAddress = requireText(promotedAdapterAddress, "adapters.promotedAdapterAddress");
            baseModelId = requireText(baseModelId, "adapters.baseModelId");
            datasetHash = requireSha256(datasetHash, "adapters.datasetHash");
            artifactSha256 = requireSha256(artifactSha256, "adapters.artifactSha256");
            providerModelId = requireText(providerModelId, "adapters.providerModelId");
            capabilities = requireCapabilities(capabilities, "adapters.capabilities");
            requirePositive(contextWindowTokens, "adapters.contextWindowTokens");
            requireNonNegative(requiredRamMb, "adapters.requiredRamMb");
        }

        AdapterArtifactIdentity identity() {
            return new AdapterArtifactIdentity(
                    experimentId,
                    candidateAdapterAddress,
                    promotedAdapterAddress,
                    baseModelId,
                    datasetHash,
                    artifactSha256,
                    "aetheris-adapter-artifact://sha256/" + artifactSha256,
                    true,
                    false);
        }

        OllamaAdapterBinding binding() {
            AdapterArtifactIdentity identity = identity();
            ModelRuntimeCandidate candidate = candidateFor(
                    AdapterRuntimeRegistration.modelIdFor(identity),
                    capabilities,
                    contextWindowTokens,
                    requiredRamMb,
                    cpuFallbackSupported);
            return new OllamaAdapterBinding(identity, providerModelId, candidate);
        }
    }

    private static ModelRuntimeCandidate candidateFor(
            String modelId,
            Set<ModelCapability> capabilities,
            int contextWindowTokens,
            long requiredRamMb,
            boolean cpuFallbackSupported) {
        return new ModelRuntimeCandidate(
                OllamaLocalRuntime.PROVIDER_ID,
                modelId,
                capabilities,
                contextWindowTokens,
                true,
                true,
                true,
                false,
                0,
                requiredRamMb,
                cpuFallbackSupported,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d);
    }

    private static Set<ModelCapability> requireCapabilities(Set<ModelCapability> values, String field) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return Set.copyOf(values);
    }

    private static String requireSha256(String value, String field) {
        String normalized = requireText(value, field);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256 digest");
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }
}
