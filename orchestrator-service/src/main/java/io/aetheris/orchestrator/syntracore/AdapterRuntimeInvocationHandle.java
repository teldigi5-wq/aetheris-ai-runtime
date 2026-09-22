package io.aetheris.orchestrator.syntracore;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Runtime-issued, single-use adapter invocation handle.
 *
 * <p>The handle binds the exact Slice 13 lease to the stream executor that owns
 * the adapter point-of-effect boundary. It is an in-process orchestration
 * contract, not a credential or cryptographic attestation.</p>
 */
public final class AdapterRuntimeInvocationHandle {

    @FunctionalInterface
    public interface StreamExecutor {
        void stream(
                AdapterInvocationLease lease,
                ModelInvocation invocation,
                Consumer<ModelStreamChunk> sink,
                BooleanSupplier cancellationRequested);
    }

    private final AdapterInvocationLease lease;
    private final StreamExecutor executor;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public AdapterRuntimeInvocationHandle(
            AdapterInvocationLease lease,
            StreamExecutor executor) {
        this.lease = Objects.requireNonNull(lease, "lease");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public AdapterInvocationLease lease() {
        return lease;
    }

    public boolean started() {
        return started.get();
    }

    public void stream(
            ModelInvocation invocation,
            Consumer<ModelStreamChunk> sink,
            BooleanSupplier cancellationRequested) {
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");

        if (!lease.modelId().equals(invocation.modelId())) {
            throw new IllegalArgumentException(
                    "adapter invocation handle modelId must match the bound lease");
        }
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("adapter invocation handle is single-use");
        }

        executor.stream(lease, invocation, sink, cancellationRequested);
    }
}
