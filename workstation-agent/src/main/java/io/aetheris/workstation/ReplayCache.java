package io.aetheris.workstation;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class ReplayCache {
    private static final int MAX_ACTIVE = 5000;
    private final Path file;
    private final Map<UUID, Instant> seen = new HashMap<>();

    public ReplayCache(Path file) throws Exception {
        this.file = file.toAbsolutePath().normalize();
        load();
    }

    public synchronized void claim(UUID commandId, Instant expiresAt, Instant now) {
        purge(now);
        if (seen.containsKey(commandId)) throw new SecurityException("Host command replay detected");
        if (seen.size() >= MAX_ACTIVE) throw new IllegalStateException("Replay cache capacity reached");
        seen.put(commandId, expiresAt);
        persist();
    }

    private void load() throws Exception {
        if (!Files.exists(file)) return;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String[] parts = line.trim().split("\\|", 2);
            if (parts.length != 2) continue;
            try { seen.put(UUID.fromString(parts[0]), Instant.parse(parts[1])); } catch (Exception ignored) {}
        }
        purge(Instant.now());
    }

    private void purge(Instant now) {
        seen.entrySet().removeIf(e -> !e.getValue().isAfter(now));
    }

    private void persist() {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            List<String> lines = seen.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "|" + e.getValue())
                    .toList();
            Files.write(temp, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            seen.clear();
            throw new IllegalStateException("Unable to persist replay cache; command execution is blocked", e);
        }
    }
}
