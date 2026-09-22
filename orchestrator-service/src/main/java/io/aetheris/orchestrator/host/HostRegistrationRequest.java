package io.aetheris.orchestrator.host;

import jakarta.validation.constraints.NotBlank;
import java.util.Set;

public record HostRegistrationRequest(
        @NotBlank String hostKey,
        @NotBlank String displayName,
        @NotBlank String platform,
        Set<String> capabilities,
        @NotBlank String publicKeyFingerprint
) {
    public HostRegistrationRequest { capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities); }
}
