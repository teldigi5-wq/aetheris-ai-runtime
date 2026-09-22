package io.aetheris.orchestrator.syntracore;

import io.aetheris.orchestrator.ingestion.IncrementalKnowledgeIngestionService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public final class AdaptationDatasetBuilder {
    public static final String MANIFEST_VERSION = "phase13-adaptation-v1";

    private final AdaptationSecretScrubber secretScrubber;

    public AdaptationDatasetBuilder() {
        this(new AdaptationSecretScrubber());
    }

    public AdaptationDatasetBuilder(AdaptationSecretScrubber secretScrubber) {
        this.secretScrubber = Objects.requireNonNull(secretScrubber, "secretScrubber");
    }

    public AdaptationDatasetManifest build(List<AdaptationSourceDocument> sources) {
        Objects.requireNonNull(sources, "sources");
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("adaptation sources must not be empty");
        }

        HashSet<String> sourceIds = new HashSet<>();
        List<AdaptationDatasetEntry> entries = new ArrayList<>();
        for (AdaptationSourceDocument source : sources) {
            Objects.requireNonNull(source, "source");
            if (!sourceIds.add(source.sourceId())) {
                throw new IllegalArgumentException("adaptation source IDs must be unique");
            }
            if (source.protectedData()) {
                throw new IllegalArgumentException("protected data is not eligible for adaptation datasets");
            }
            if (source.tags().contains(IncrementalKnowledgeIngestionService.TOMBSTONE_TAG)) {
                throw new IllegalArgumentException("tombstoned data is not eligible for adaptation datasets");
            }

            AdaptationSecretScrubber.ScrubResult scrubbed = secretScrubber.scrub(source.content());
            if (scrubbed.content().isBlank()) {
                throw new IllegalArgumentException("sanitized adaptation content must not be blank");
            }
            entries.add(new AdaptationDatasetEntry(
                    source.sourceId(),
                    sha256(source.content()),
                    scrubbed.content(),
                    sha256(scrubbed.content()),
                    scrubbed.redactionCount(),
                    source.rightsBasis(),
                    source.rightsReference(),
                    source.provenanceReference()));
        }

        entries.sort(Comparator.comparing(AdaptationDatasetEntry::sourceId));
        int redactions = entries.stream().mapToInt(AdaptationDatasetEntry::redactionCount).sum();
        return new AdaptationDatasetManifest(
                MANIFEST_VERSION,
                entries,
                datasetHash(entries),
                redactions);
    }

    private String datasetHash(List<AdaptationDatasetEntry> entries) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, MANIFEST_VERSION);
            for (AdaptationDatasetEntry entry : entries) {
                update(digest, entry.sourceId());
                update(digest, entry.sourceContentHash());
                update(digest, entry.sanitizedContentHash());
                update(digest, entry.rightsBasis().name());
                update(digest, entry.rightsReference());
                update(digest, entry.provenanceReference());
                update(digest, Integer.toString(entry.redactionCount()));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to hash adaptation dataset", failure);
        }
    }

    private void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to hash adaptation source", failure);
        }
    }
}
