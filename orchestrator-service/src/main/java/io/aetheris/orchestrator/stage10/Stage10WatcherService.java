package io.aetheris.orchestrator.stage10;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class Stage10WatcherService {
    private final Map<UUID, WatchRoot> roots = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> hashes = new ConcurrentHashMap<>();
    private final Deque<WatchEventResult> recent = new ArrayDeque<>();

    public WatchRoot register(RegisterWatchRootRequest request) {
        if (request == null || request.path() == null || request.path().isBlank()) throw new IllegalArgumentException("Watch root path is required");
        String path = normalize(request.path());
        if (isBroadRoot(path)) throw new IllegalArgumentException("Stage 10 watchers require a project/workspace subdirectory, not a drive/home root");
        Set<String> extensions = normalizeExtensions(request.extensions());
        WatchRoot root = new WatchRoot(UUID.randomUUID(), request.label() == null || request.label().isBlank() ? path : request.label().trim(), path,
                extensions, request.enabled(), Instant.now());
        roots.put(root.id(), root);
        hashes.put(root.id(), new ConcurrentHashMap<>());
        return root;
    }

    public WatchRoot setEnabled(UUID id, boolean enabled) {
        WatchRoot existing = required(id);
        WatchRoot updated = new WatchRoot(existing.id(), existing.label(), existing.path(), existing.extensions(), enabled, existing.createdAt());
        roots.put(id, updated);
        return updated;
    }

    public WatchEventResult ingest(UUID rootId, WatchEvent event) {
        WatchRoot root = required(rootId);
        if (!root.enabled()) throw new IllegalStateException("Watch root is disabled");
        if (event == null || event.path() == null || event.path().isBlank()) throw new IllegalArgumentException("Watch event path is required");
        String path = normalize(event.path());
        if (!underRoot(path, root.path())) throw new IllegalArgumentException("Watch event is outside the configured owner root");
        validateExtension(path, root.extensions());
        String kind = event.kind() == null ? "UPSERT" : event.kind().trim().toUpperCase(Locale.ROOT);
        Map<String, String> state = hashes.computeIfAbsent(rootId, ignored -> new ConcurrentHashMap<>());
        String previous = state.get(path);
        String status;
        String hash = event.sha256() == null ? "" : event.sha256().trim().toLowerCase(Locale.ROOT);
        if (kind.equals("DELETE")) {
            state.remove(path);
            status = previous == null ? "UNCHANGED" : "DELETED";
        } else {
            if (!hash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("UPSERT watch events require a SHA-256 content hash");
            if (Objects.equals(previous, hash)) status = "UNCHANGED";
            else if (previous == null) status = "ADDED";
            else status = "CHANGED";
            state.put(path, hash);
        }
        WatchEventResult result = new WatchEventResult(UUID.randomUUID(), rootId, path, status, hash, previous, Instant.now(),
                "Event evidence only; Stage 10 does not recursively crawl disks or read arbitrary file content");
        synchronized (recent) {
            recent.addFirst(result);
            while (recent.size() > 200) recent.removeLast();
        }
        return result;
    }

    public List<WatchRoot> roots() {
        return roots.values().stream().sorted(Comparator.comparing(WatchRoot::createdAt).reversed()).toList();
    }

    public List<WatchEventResult> recent() {
        synchronized (recent) { return List.copyOf(recent); }
    }

    private WatchRoot required(UUID id) {
        if (id == null) throw new IllegalArgumentException("root id is required");
        WatchRoot root = roots.get(id);
        if (root == null) throw new NoSuchElementException("Unknown Stage 10 watch root: " + id);
        return root;
    }

    private Set<String> normalizeExtensions(Set<String> input) {
        if (input == null || input.isEmpty()) return Set.of("java", "kt", "js", "ts", "tsx", "jsx", "py", "md", "json", "yaml", "yml", "xml", "properties", "txt", "html", "css", "dart", "ino");
        Set<String> out = new HashSet<>();
        for (String extension : input) {
            if (extension == null || extension.isBlank()) continue;
            String e = extension.trim().toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
            if (!e.matches("[a-z0-9]{1,12}")) throw new IllegalArgumentException("Invalid watched extension: " + extension);
            out.add(e);
        }
        if (out.isEmpty()) throw new IllegalArgumentException("At least one safe watched extension is required");
        return Set.copyOf(out);
    }

    private void validateExtension(String path, Set<String> extensions) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        if (dot <= slash || dot == path.length() - 1) throw new IllegalArgumentException("Stage 10 watcher ignores extensionless files by default");
        String extension = path.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!extensions.contains(extension)) throw new IllegalArgumentException("File extension is not allowed for this owner watch root: " + extension);
    }

    private String normalize(String raw) {
        String path = raw.trim().replace('\\', '/').replaceAll("/+", "/");
        if (path.contains("/../") || path.endsWith("/..") || path.startsWith("../")) throw new IllegalArgumentException("Parent traversal is not allowed");
        while (path.endsWith("/") && path.length() > 3) path = path.substring(0, path.length() - 1);
        return path.toLowerCase(Locale.ROOT);
    }

    private boolean underRoot(String path, String root) {
        return path.equals(root) || path.startsWith(root + "/");
    }

    private boolean isBroadRoot(String path) {
        if (path.equals("/") || path.matches("^[a-z]:/$")) return true;
        if (path.matches("^[a-z]:/users/[^/]+$")) return true;
        return path.equals("c:/windows") || path.equals("c:/programdata") || path.endsWith("/appdata");
    }

    public record RegisterWatchRootRequest(String label, String path, Set<String> extensions, boolean enabled) {}
    public record WatchRoot(UUID id, String label, String path, Set<String> extensions, boolean enabled, Instant createdAt) {}
    public record WatchEvent(String path, String sha256, String kind) {}
    public record WatchEventResult(UUID id, UUID rootId, String path, String status, String sha256, String previousSha256,
                                   Instant observedAt, String detail) {}
}
