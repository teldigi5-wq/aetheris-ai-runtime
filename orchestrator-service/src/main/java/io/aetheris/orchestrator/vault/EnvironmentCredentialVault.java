package io.aetheris.orchestrator.vault;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class EnvironmentCredentialVault implements CredentialVault {

    private final Environment environment;
    private final String prefix;

    public EnvironmentCredentialVault(
            Environment environment,
            @Value("${aetheris.vault.environment-prefix:AETHERIS_SECRET_}") String prefix) {
        this.environment = environment;
        this.prefix = prefix == null ? "AETHERIS_SECRET_" : prefix.trim();
    }

    @Override
    public Optional<char[]> resolve(String alias) {
        String value = environment.getProperty(environmentKey(alias));
        if (value == null || value.isBlank()) return Optional.empty();
        return Optional.of(value.toCharArray());
    }

    @Override
    public CredentialDescriptor describe(String alias) {
        String normalized = requireAlias(alias);
        String value = environment.getProperty(environmentKey(normalized));
        return new CredentialDescriptor(normalized, "environment", value != null && !value.isBlank());
    }

    private String environmentKey(String alias) {
        String normalized = requireAlias(alias)
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_");
        return prefix + normalized;
    }

    private String requireAlias(String alias) {
        if (alias == null || alias.isBlank()) throw new IllegalArgumentException("Credential alias is required");
        String normalized = alias.trim();
        if (normalized.length() > 120) throw new IllegalArgumentException("Credential alias is too long");
        return normalized;
    }
}
