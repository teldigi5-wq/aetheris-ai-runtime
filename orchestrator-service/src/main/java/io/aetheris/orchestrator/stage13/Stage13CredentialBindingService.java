package io.aetheris.orchestrator.stage13;

import io.aetheris.orchestrator.stage12.Stage12ProviderRegistryService;
import io.aetheris.orchestrator.vault.CredentialDescriptor;
import io.aetheris.orchestrator.vault.CredentialVault;
import io.aetheris.orchestrator.vault.OperatingSystemCredentialVault;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Stage13CredentialBindingService {
    private final Stage12ProviderRegistryService providers;
    private final List<CredentialVault> vaults;

    public Stage13CredentialBindingService(Stage12ProviderRegistryService providers, List<CredentialVault> vaults) {
        this.providers = providers;
        this.vaults = List.copyOf(vaults);
    }

    public CredentialBinding assess(String providerId) {
        var provider = providers.find(require(providerId).toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new NoSuchElementException("Unknown Stage 12 provider"));
        String alias = provider.credentialAlias();
        if (alias == null || alias.isBlank()) {
            return new CredentialBinding("CREDENTIAL_ALIAS_REQUIRED", provider.providerId(), null,
                    List.of(), false, false, false,
                    "Provider has no credential alias; no secret lookup was attempted");
        }

        List<String> availableBackends = new ArrayList<>();
        boolean osBacked = false;
        for (CredentialVault vault : vaults) {
            try {
                CredentialDescriptor descriptor = vault.describe(alias);
                if (descriptor != null && descriptor.available()) {
                    availableBackends.add(descriptor.provider());
                    if (vault instanceof OperatingSystemCredentialVault) osBacked = true;
                }
            } catch (RuntimeException ignored) {
                // Descriptor failures are treated as unavailable; Stage 13 never falls back to reading/logging the secret value.
            }
        }
        boolean available = !availableBackends.isEmpty();
        String status = !available ? "CREDENTIAL_EVIDENCE_REQUIRED" : osBacked ? "OS_VAULT_ALIAS_AVAILABLE" : "CONTROLLED_TEST_ALIAS_AVAILABLE";
        return new CredentialBinding(status, provider.providerId(), alias, List.copyOf(availableBackends),
                available, osBacked, false,
                osBacked ? "Credential alias is available in an OS-backed vault without resolving the secret"
                        : available ? "Credential alias is available for controlled testing; OS-backed production validation remains pending"
                        : "No configured vault reports the alias as available");
    }

    private String require(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("providerId is required");
        return value.trim();
    }

    public record CredentialBinding(String status, String providerId, String credentialAlias,
                                    List<String> availableBackends, boolean available,
                                    boolean operatingSystemBacked, boolean secretValueExposed, String detail) {}
}
