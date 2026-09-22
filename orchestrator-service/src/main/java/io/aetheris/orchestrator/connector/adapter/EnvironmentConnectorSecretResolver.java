package io.aetheris.orchestrator.connector.adapter;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@Component
public class EnvironmentConnectorSecretResolver {
    private static final String PREFIX = "AETHERIS_CONNECTOR_SECRET_";
    private final Environment environment;

    public EnvironmentConnectorSecretResolver(Environment environment) {
        this.environment = environment;
    }

    public byte[] resolve(String secretReference) {
        String value = environment.getProperty(PREFIX + secretReference);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Connector secret reference is not available in the runtime secret boundary");
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
