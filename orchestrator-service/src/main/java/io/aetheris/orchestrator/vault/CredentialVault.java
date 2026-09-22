package io.aetheris.orchestrator.vault;

import java.util.Optional;

/**
 * Resolves credentials by alias. Implementations must never persist or log secret values.
 */
public interface CredentialVault {
    Optional<char[]> resolve(String alias);
    CredentialDescriptor describe(String alias);
}
