package io.aetheris.orchestrator.connector.oauth;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryConnectorCredentialVault {
    private final Map<String, String> values = new ConcurrentHashMap<>();

    public String put(String purpose, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Credential material is required");
        String reference = "vault:" + purpose + ":" + UUID.randomUUID();
        values.put(reference, value);
        return reference;
    }

    public String require(String reference) {
        if (reference == null || reference.isBlank()) throw new IllegalStateException("Credential reference is unavailable");
        String value = values.get(reference);
        if (value == null) throw new IllegalStateException("Credential material is unavailable in the active runtime vault");
        return value;
    }

    public void delete(String reference) {
        if (reference != null && !reference.isBlank()) values.remove(reference);
    }

    public boolean contains(String reference) {
        return reference != null && values.containsKey(reference);
    }
}
