package io.aetheris.orchestrator.syntracore;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record AdaptationDatasetManifest(
        String manifestVersion,
        List<AdaptationDatasetEntry> entries,
        String datasetHash,
        int totalRedactions) {

    public AdaptationDatasetManifest {
        if (manifestVersion == null || manifestVersion.isBlank()) {
            throw new IllegalArgumentException("manifestVersion must not be blank");
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("entries must not be empty");
        }
        if (datasetHash == null || !datasetHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("datasetHash must be a lowercase SHA-256 hex digest");
        }
        if (totalRedactions < 0) {
            throw new IllegalArgumentException("totalRedactions must not be negative");
        }

        String previous = null;
        HashSet<String> ids = new HashSet<>();
        int countedRedactions = 0;
        for (AdaptationDatasetEntry entry : entries) {
            Objects.requireNonNull(entry, "entry");
            if (!ids.add(entry.sourceId())) {
                throw new IllegalArgumentException("dataset source IDs must be unique");
            }
            if (previous != null && previous.compareTo(entry.sourceId()) > 0) {
                throw new IllegalArgumentException("dataset entries must be ordered by sourceId");
            }
            previous = entry.sourceId();
            countedRedactions += entry.redactionCount();
        }
        if (countedRedactions != totalRedactions) {
            throw new IllegalArgumentException("totalRedactions must match entry evidence");
        }
    }
}
