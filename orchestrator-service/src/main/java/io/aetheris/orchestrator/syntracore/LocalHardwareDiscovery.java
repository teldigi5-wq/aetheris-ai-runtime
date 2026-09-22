package io.aetheris.orchestrator.syntracore;

import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;

public record LocalHardwareDiscovery(
        String osName,
        String osArch,
        int availableProcessors,
        OptionalLong totalPhysicalRamMb,
        Instant observedAt) {

    public LocalHardwareDiscovery {
        if (osName == null || osName.isBlank()) {
            throw new IllegalArgumentException("osName must not be blank");
        }
        if (osArch == null || osArch.isBlank()) {
            throw new IllegalArgumentException("osArch must not be blank");
        }
        if (availableProcessors <= 0) {
            throw new IllegalArgumentException("availableProcessors must be positive");
        }
        totalPhysicalRamMb = Objects.requireNonNull(totalPhysicalRamMb, "totalPhysicalRamMb");
        if (totalPhysicalRamMb.isPresent() && totalPhysicalRamMb.getAsLong() <= 0) {
            throw new IllegalArgumentException("observed physical RAM must be positive");
        }
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
    }

    public boolean gpuEvidenceAvailable() {
        return false;
    }
}
