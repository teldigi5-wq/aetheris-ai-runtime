package io.aetheris.orchestrator;

import io.aetheris.orchestrator.mcp.McpEndpointPolicy;
import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class McpEndpointPolicyTest {
    private final McpEndpointPolicy defaultPolicy =
        new McpEndpointPolicy("localhost,127.0.0.1,::1,host.docker.internal", "");

    @Test void acceptsLocalConfiguredRuntime() {
        assertEquals(URI.create("http://127.0.0.1:11434/mcp"),
                defaultPolicy.requireAllowed("http://127.0.0.1:11434/mcp", true));
        assertEquals("host.docker.internal",
                defaultPolicy.requireAllowed("http://host.docker.internal:8090/mcp", true).getHost());
    }

    @Test void refusesUnapprovedLocalAndRemoteTargets() {
        assertThrows(IllegalArgumentException.class, () -> defaultPolicy.requireAllowed("http://metadata.google.internal/", true));
        assertThrows(IllegalArgumentException.class, () -> defaultPolicy.requireAllowed("https://trusted.example.com/mcp", false));
        assertThrows(IllegalArgumentException.class, () -> defaultPolicy.requireAllowed("http://trusted.example.com/mcp", false));
    }

    @Test void remoteAllowlistIsExactAndRequiresHttps() {
        var policy = new McpEndpointPolicy("localhost", "trusted.example.com");
        assertEquals("trusted.example.com",
                policy.requireAllowed("https://trusted.example.com/mcp", false).getHost());
        for (String endpoint : new String[] {
                "https://trusted.example.com.evil.test/mcp",
                "https://api.trusted.example.com/mcp",
                "https://trusted.example.com@127.0.0.1/mcp",
                "https://trusted.example.com/mcp#fragment",
                "http://trusted.example.com/mcp",
                "file:///etc/passwd"
        }) {
            assertThrows(IllegalArgumentException.class,
                    () -> policy.requireAllowed(endpoint, false), endpoint);
        }
    }

    @Test void deniesLiteralAddressesEvenWhenConfigured() {
        var policy = new McpEndpointPolicy("localhost", "127.0.0.1,10.0.0.1,::1,metadata.google.internal");
        for (String target : new String[] {
                "https://127.0.0.1/mcp", "https://10.0.0.1/mcp",
                "https://[::1]/mcp", "https://metadata.google.internal/mcp"
        }) assertThrows(IllegalArgumentException.class,
                () -> policy.requireAllowed(target, false), target);
    }

    @Test void rejectsCredentialsAndInvalidPorts() {
        var policy = new McpEndpointPolicy("localhost", "trusted.example.com");
        for (String target : new String[] {
                "https://alice:secret@trusted.example.com/mcp",
                "http://localhost:0/mcp", "http://localhost:65536/mcp",
                "http://localhost/mcp#secret",
                "https://trusted.example.com:99999/mcp"
        }) assertThrows(IllegalArgumentException.class,
                () -> policy.requireAllowed(target, target.contains("localhost")), target);
    }
}
