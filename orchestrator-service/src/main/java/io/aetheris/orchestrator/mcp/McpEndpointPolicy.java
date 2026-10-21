package io.aetheris.orchestrator.mcp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Exact-host, deny-by-default MCP outbound boundary. Network egress/DNS pinning remain separate controls. */
@Component
public final class McpEndpointPolicy {
    private final Set<String> localHosts;
    private final Set<String> remoteHosts;

    public McpEndpointPolicy(
            @Value("${aetheris.mcp.allowed-local-hosts:localhost,127.0.0.1,::1,host.docker.internal}") String local,
            @Value("${aetheris.mcp.allowed-remote-hosts:}") String remote) {
        this.localHosts = names(local);
        this.remoteHosts = names(remote);
    }

    public URI requireAllowed(String raw, boolean local) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("MCP endpoint is required");
        URI endpoint;
        try {
            endpoint = URI.create(raw);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid MCP endpoint URI");
        }
        String scheme = endpoint.getScheme() == null ? "" : endpoint.getScheme().toLowerCase(Locale.ROOT);
        String host = endpoint.getHost() == null ? "" : endpoint.getHost().toLowerCase(Locale.ROOT);
        if (host.isBlank() || endpoint.isOpaque() || endpoint.getRawUserInfo() != null
                || endpoint.getRawFragment() != null || endpoint.getPort() > 65535
                || endpoint.getPort() == 0) {
            throw new IllegalArgumentException("MCP endpoint has an unsafe authority or fragment");
        }
        if (local) {
            if (!scheme.equals("http") && !scheme.equals("https"))
                throw new IllegalArgumentException("Local MCP endpoint requires HTTP or HTTPS");
            if (!localHosts.contains(host))
                throw new IllegalArgumentException("Local MCP endpoint host is not allowlisted");
        } else {
            if (!scheme.equals("https"))
                throw new IllegalArgumentException("Remote MCP endpoint requires HTTPS");
            if (host.contains(":") || host.matches("[0-9.]+")
                    || host.equals("localhost") || host.endsWith(".localhost")
                    || host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".home.arpa"))
                throw new IllegalArgumentException("Remote MCP endpoint cannot target literal or local addresses");
            if (!remoteHosts.contains(host))
                throw new IllegalArgumentException("Remote MCP endpoint host is not allowlisted");
        }
        return endpoint;
    }

    private static Set<String> names(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}
