package io.aetheris.orchestrator.syntracore;

import java.util.List;
import java.util.Optional;

/**
 * Optional extension for local runtimes that can truthfully expose adapter-derived
 * model candidates after Slice 9 has verified activation of the exact artifact.
 */
public interface AdapterAwareSyntraModelRuntime extends SyntraModelRuntime {
    List<AdapterRuntimeRegistration> adapterRegistrations();

    /**
     * Read-only current-state observation used to reconcile a previously verified
     * registration immediately before it is admitted to the routing catalog.
     *
     * <p>The default is deliberately fail-closed so existing implementations remain
     * source-compatible without making stale registrations routable.</p>
     */
    default Optional<AdapterArtifactObservation> observeAdapter(AdapterArtifactIdentity identity) {
        return Optional.empty();
    }
}
