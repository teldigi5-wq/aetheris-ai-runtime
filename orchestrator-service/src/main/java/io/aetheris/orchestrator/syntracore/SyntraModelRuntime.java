package io.aetheris.orchestrator.syntracore;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface SyntraModelRuntime {
    String providerId();

    List<ModelRuntimeCandidate> models();

    void stream(
            ModelInvocation invocation,
            Consumer<ModelStreamChunk> sink,
            BooleanSupplier cancellationRequested);
}
