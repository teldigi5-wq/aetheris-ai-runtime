package io.aetheris.orchestrator.syntracore;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Optional stronger adapter runtime contract that can issue a typed invocation
 * lease and start streaming through the lease-aware path.
 */
public interface AdapterInvocationLeasingRuntime extends AdapterAwareSyntraModelRuntime {

    Optional<AdapterInvocationLease> acquireAdapterInvocationLease(
            AdapterRuntimeRegistration registration);

    void streamWithAdapterLease(
            AdapterInvocationLease lease,
            ModelInvocation invocation,
            Consumer<ModelStreamChunk> sink,
            BooleanSupplier cancellationRequested);
}
