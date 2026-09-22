package io.aetheris.orchestrator.trading;

import io.aetheris.orchestrator.vault.CredentialDescriptor;
import io.aetheris.orchestrator.vault.CredentialVault;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class Stage10TestnetReadinessService {
    private final CredentialVault vault;
    private final String apiKeyAlias;
    private final String apiSecretAlias;
    private final boolean adapterEnabled;

    public Stage10TestnetReadinessService(CredentialVault vault,
            @Value("${aetheris.stage10.testnet.api-key-alias:exchange-testnet-api-key}") String apiKeyAlias,
            @Value("${aetheris.stage10.testnet.api-secret-alias:exchange-testnet-api-secret}") String apiSecretAlias,
            @Value("${aetheris.stage10.testnet.adapter-enabled:false}") boolean adapterEnabled) {
        this.vault = vault;
        this.apiKeyAlias = normalize(apiKeyAlias, "exchange-testnet-api-key");
        this.apiSecretAlias = normalize(apiSecretAlias, "exchange-testnet-api-secret");
        this.adapterEnabled = adapterEnabled;
    }

    public TestnetReadiness readiness() {
        CredentialDescriptor key = vault.describe(apiKeyAlias);
        CredentialDescriptor secret = vault.describe(apiSecretAlias);
        boolean credentials = key.available() && secret.available();
        String status;
        if (!credentials) status = "CREDENTIALS_MISSING";
        else if (!adapterEnabled) status = "CREDENTIALS_PRESENT_ADAPTER_DISABLED";
        else status = "READY_FOR_TESTNET_CONNECTIVITY_VALIDATION";
        return new TestnetReadiness(status,
                List.of(new CredentialPresence(key.alias(), key.provider(), key.available()),
                        new CredentialPresence(secret.alias(), secret.provider(), secret.available())),
                adapterEnabled, false, false, false,
                "NEW_OWNER_CREDENTIALS_VIA_VAULT_ONLY",
                "Readiness checks never expose secret values. Even when enabled, this stage is testnet/paper only; live money, withdrawals and transfers remain disabled.");
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record CredentialPresence(String alias, String provider, boolean available) {}
    public record TestnetReadiness(String status, List<CredentialPresence> credentials, boolean adapterConfigured,
                                   boolean liveMoneyAllowed, boolean withdrawalsAllowed, boolean transfersAllowed,
                                   String credentialPolicy, String detail) {}
}
