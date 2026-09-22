package io.aetheris.orchestrator.syntracore;

import java.util.Objects;

public record AdapterArtifactObservation(
        AdapterArtifactIdentity identity,
        boolean exists,
        boolean active,
        String verifierId,
        String evidenceRef) {

    public AdapterArtifactObservation {
        identity = Objects.requireNonNull(identity, "identity");
        if (active && !exists) {
            throw new IllegalArgumentException("an active adapter artifact must exist");
        }
        if (verifierId == null || verifierId.isBlank()) {
            throw new IllegalArgumentException("verifierId must not be blank");
        }
        verifierId = verifierId.trim();
        if (evidenceRef == null || !evidenceRef.startsWith("aetheris-adapter-observation://")) {
            throw new IllegalArgumentException("evidenceRef must use aetheris-adapter-observation:// scheme");
        }
    }
}
