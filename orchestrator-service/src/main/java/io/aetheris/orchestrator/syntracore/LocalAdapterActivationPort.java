package io.aetheris.orchestrator.syntracore;

/**
 * Local-only point-of-effect contract for a future concrete adapter runtime integration.
 * Slice 9 supplies governance around this port; it does not provide an Ollama, filesystem,
 * process, or model-installation implementation.
 */
public interface LocalAdapterActivationPort {
    AdapterArtifactObservation inspect(AdapterArtifactIdentity expectedIdentity);

    AdapterArtifactObservation activate(AdapterArtifactIdentity expectedIdentity);

    AdapterArtifactObservation detach(AdapterArtifactIdentity expectedIdentity);
}
