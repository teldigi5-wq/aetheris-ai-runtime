package io.aetheris.orchestrator.syntracore;

import java.util.Optional;

/**
 * Optional native adapter runtime contract that binds the exact Slice 13
 * invocation lease to a single-use runtime-owned stream handle.
 *
 * <p>Slice 13 lease-aware runtimes remain compatible: the orchestrator can
 * wrap a validated lease and {@code streamWithAdapterLease} executor in the
 * same single-use handle when this stronger native contract is not present.</p>
 */
public interface AdapterInvocationHandleRuntime extends AdapterInvocationLeasingRuntime {

    Optional<AdapterRuntimeInvocationHandle> acquireAdapterInvocationHandle(
            AdapterRuntimeRegistration registration);
}
