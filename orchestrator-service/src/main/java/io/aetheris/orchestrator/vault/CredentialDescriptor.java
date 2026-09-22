package io.aetheris.orchestrator.vault;

public record CredentialDescriptor(
        String alias,
        String provider,
        boolean available
) {
}
