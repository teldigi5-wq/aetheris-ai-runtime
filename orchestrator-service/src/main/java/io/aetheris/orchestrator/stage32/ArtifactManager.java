package io.aetheris.orchestrator.stage32;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArtifactManager {
    private final Map<String, ArtifactRecord> records = new LinkedHashMap<>();
    public synchronized ArtifactRecord register(ArtifactRecord record) {
        if (records.containsKey(record.artifactId())) throw new IllegalStateException("Artifact id already registered");
        records.put(record.artifactId(), record);
        return record;
    }
    public synchronized List<ArtifactRecord> retainedAt(Instant moment) {
        return records.values().stream().filter(record -> !record.retainUntil().isBefore(moment)).toList();
    }
}
