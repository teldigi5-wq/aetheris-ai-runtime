package io.aetheris.orchestrator.syntracore;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record LocalRuntimeEndpoint(URI baseUri) {
    private static final Set<String> LOOPBACK_HOSTS = Set.of(
            "localhost",
            "127.0.0.1",
            "::1",
            "[::1]");

    public LocalRuntimeEndpoint {
        baseUri = Objects.requireNonNull(baseUri, "baseUri").normalize();
        if (!"http".equalsIgnoreCase(baseUri.getScheme())) {
            throw new IllegalArgumentException("local runtime endpoint must use http");
        }
        String host = baseUri.getHost();
        if (host == null || !LOOPBACK_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("local runtime endpoint must use an explicit loopback host");
        }
        if (baseUri.getPort() <= 0) {
            throw new IllegalArgumentException("local runtime endpoint must declare a port");
        }
        if (baseUri.getUserInfo() != null || baseUri.getQuery() != null || baseUri.getFragment() != null) {
            throw new IllegalArgumentException("local runtime endpoint must not contain credentials, query, or fragment");
        }
        String path = baseUri.getPath();
        if (path != null && !path.isBlank() && !"/".equals(path)) {
            throw new IllegalArgumentException("local runtime endpoint must not contain a path");
        }
        baseUri = URI.create(baseUri.getScheme().toLowerCase(Locale.ROOT)
                + "://" + hostForUri(host) + ":" + baseUri.getPort() + "/");
    }

    public URI resolve(String absolutePath) {
        if (absolutePath == null || !absolutePath.startsWith("/") || absolutePath.contains("..")) {
            throw new IllegalArgumentException("runtime path must be absolute and traversal-free");
        }
        return baseUri.resolve(absolutePath.substring(1));
    }

    private static String hostForUri(String host) {
        if (host.contains(":") && !host.startsWith("[")) {
            return "[" + host + "]";
        }
        return host;
    }
}
