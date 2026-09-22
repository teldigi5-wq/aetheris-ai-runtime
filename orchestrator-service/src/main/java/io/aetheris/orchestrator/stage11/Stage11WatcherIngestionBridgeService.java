package io.aetheris.orchestrator.stage11;

import io.aetheris.orchestrator.ingestion.IncrementalIngestionResult;
import io.aetheris.orchestrator.ingestion.IncrementalKnowledgeIngestionService;
import io.aetheris.orchestrator.ingestion.IngestKnowledgeRequest;
import io.aetheris.orchestrator.memory.MemoryScope;
import io.aetheris.orchestrator.stage10.Stage10WatcherService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class Stage11WatcherIngestionBridgeService {
    private static final int MAX_CONTENT_CHARS = 2_000_000;
    private static final String SOURCE_KIND = "OWNER_FILE";

    private final Stage10WatcherService watchers;
    private final IncrementalKnowledgeIngestionService ingestion;

    public Stage11WatcherIngestionBridgeService(Stage10WatcherService watchers,
                                                IncrementalKnowledgeIngestionService ingestion) {
        this.watchers = watchers;
        this.ingestion = ingestion;
    }

    public BridgeResult bridge(BridgeRequest request) {
        if (request == null || request.rootId() == null || request.event() == null) {
            throw new IllegalArgumentException("rootId and watcher event are required");
        }
        String kind = request.event().kind() == null ? "UPSERT" : request.event().kind().trim().toUpperCase(Locale.ROOT);
        String namespace = request.namespace() == null || request.namespace().isBlank()
                ? "owner-watch:" + request.rootId()
                : request.namespace().trim();

        if (!kind.equals("DELETE")) {
            String content = request.content() == null ? "" : request.content();
            if (content.isBlank()) throw new IllegalArgumentException("Watched file content is required for synchronization");
            if (content.length() > MAX_CONTENT_CHARS) throw new IllegalArgumentException("Watched content exceeds Stage 11 ingestion limit");
            String expected = request.event().sha256() == null ? "" : request.event().sha256().trim().toLowerCase(Locale.ROOT);
            String actual = sha256(content);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("Watcher SHA-256 evidence does not match supplied file content");
            }
        }

        Stage10WatcherService.WatchEventResult event = watchers.ingest(request.rootId(), request.event());
        if (event.status().equals("UNCHANGED")) {
            return new BridgeResult(event, null, "SKIPPED_UNCHANGED", "Hash is unchanged; no knowledge write performed");
        }

        if (event.status().equals("DELETED")) {
            try {
                IncrementalIngestionResult result = ingestion.tombstone(SOURCE_KIND, event.path(), namespace);
                return new BridgeResult(event, result, "TOMBSTONED", "Deleted owner file was tombstoned in Stage 9 knowledge state");
            } catch (NoSuchElementException ignored) {
                return new BridgeResult(event, null, "DELETE_NO_SOURCE", "Watcher deletion had no previously synchronized knowledge source");
            }
        }

        Set<String> tags = new LinkedHashSet<>();
        if (request.tags() != null) tags.addAll(request.tags());
        tags.add("stage11-watcher");
        tags.add("owner-root:" + request.rootId());
        MemoryScope scope = request.scope() == null ? MemoryScope.PROJECT : request.scope();
        String title = request.title() == null || request.title().isBlank() ? event.path() : request.title().trim();
        IncrementalIngestionResult result = ingestion.sync(new IngestKnowledgeRequest(
                SOURCE_KIND,
                event.path(),
                title,
                request.content(),
                scope,
                namespace,
                request.protectedData(),
                tags));
        return new BridgeResult(event, result, result.changed() ? "SYNCHRONIZED" : "SKIPPED_UNCHANGED",
                "Watcher evidence was verified before incremental knowledge synchronization");
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to calculate watcher content hash", e);
        }
    }

    public record BridgeRequest(UUID rootId, Stage10WatcherService.WatchEvent event, String title, String content,
                                MemoryScope scope, String namespace, boolean protectedData, Set<String> tags) {
        public BridgeRequest { tags = tags == null ? Set.of() : Set.copyOf(tags); }
    }

    public record BridgeResult(Stage10WatcherService.WatchEventResult watcherEvent,
                               IncrementalIngestionResult ingestion,
                               String status,
                               String detail) {}
}
