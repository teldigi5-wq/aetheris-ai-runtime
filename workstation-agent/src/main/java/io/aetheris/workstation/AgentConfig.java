package io.aetheris.workstation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

public record AgentConfig(UUID hostId, int port, Path configFile, Path signingKeyFile,
                          List<Path> workspaceRoots, Map<String, Path> allowedApps) {
    public AgentConfig {
        hostId = Objects.requireNonNull(hostId, "hostId");
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("listen.port must be between 1024 and 65535");
        configFile = Objects.requireNonNull(configFile, "configFile").toAbsolutePath().normalize();
        signingKeyFile = Objects.requireNonNull(signingKeyFile, "signingKeyFile").toAbsolutePath().normalize();
        if (workspaceRoots == null || workspaceRoots.isEmpty()) throw new IllegalArgumentException("At least one workspace root is required");
        List<Path> safeRoots = new ArrayList<>();
        Path userHome = Path.of(System.getProperty("user.home", ".")).toAbsolutePath().normalize();
        for (Path value : workspaceRoots) {
            Path root = Objects.requireNonNull(value, "workspace root").toAbsolutePath().normalize();
            if (root.getParent() == null) throw new IllegalArgumentException("Filesystem/drive root cannot be used as a workspace root");
            if (root.equals(userHome)) throw new IllegalArgumentException("Entire user home cannot be used as a workspace root");
            safeRoots.add(root);
        }
        workspaceRoots = List.copyOf(safeRoots);

        Map<String, Path> safeApps = new TreeMap<>();
        if (allowedApps != null) {
            for (Map.Entry<String, Path> entry : allowedApps.entrySet()) {
                String alias = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
                if (!alias.matches("[a-z0-9._-]{1,64}")) throw new IllegalArgumentException("Invalid app alias: " + alias);
                safeApps.put(alias, Objects.requireNonNull(entry.getValue(), "app path").toAbsolutePath().normalize());
            }
        }
        allowedApps = Map.copyOf(safeApps);
    }

    public static AgentConfig load(Path configFile) throws IOException {
        Path absolute = configFile.toAbsolutePath().normalize();
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(absolute)) { properties.load(in); }
        UUID hostId = UUID.fromString(required(properties, "host.id"));
        int port = Integer.parseInt(properties.getProperty("listen.port", "17771").trim());
        Path base = absolute.getParent() == null ? Path.of(".").toAbsolutePath().normalize() : absolute.getParent();
        Path keyFile = resolve(base, required(properties, "signing-key-file"));

        List<Path> roots = new ArrayList<>();
        for (String value : properties.getProperty("workspace-roots", "").split(";")) {
            if (!value.isBlank()) roots.add(resolve(base, value.trim()));
        }

        Map<String, Path> apps = new TreeMap<>();
        for (String name : properties.stringPropertyNames()) {
            if (!name.startsWith("app.")) continue;
            String alias = name.substring(4).trim().toLowerCase(Locale.ROOT);
            apps.put(alias, resolve(base, properties.getProperty(name).trim()));
        }
        return new AgentConfig(hostId, port, absolute, keyFile, roots, apps);
    }

    public Path stateDirectory() {
        Path parent = configFile.getParent();
        return parent == null ? Path.of(".").toAbsolutePath().normalize() : parent;
    }

    private static Path resolve(Path base, String value) {
        Path path = Path.of(value);
        return (path.isAbsolute() ? path : base.resolve(path)).toAbsolutePath().normalize();
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing configuration: " + key);
        return value.trim();
    }
}
