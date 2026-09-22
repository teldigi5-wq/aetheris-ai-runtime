package io.aetheris.orchestrator.syntracore;

import java.util.Objects;

public record HardwareCapacity(
        long ramMb,
        long vramMb,
        boolean gpuAvailable,
        HardwareEvidence evidence) {

    public HardwareCapacity {
        if (ramMb < 0) {
            throw new IllegalArgumentException("ramMb must be non-negative");
        }
        if (vramMb < 0) {
            throw new IllegalArgumentException("vramMb must be non-negative");
        }
        Objects.requireNonNull(evidence, "evidence");
    }

    public boolean physicallyVerified() {
        return evidence == HardwareEvidence.OBSERVED;
    }
}
