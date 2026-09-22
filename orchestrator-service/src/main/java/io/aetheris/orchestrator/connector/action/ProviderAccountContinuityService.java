package io.aetheris.orchestrator.connector.action;

import io.aetheris.orchestrator.connector.ConnectorConnectionEntity;
import io.aetheris.orchestrator.connector.ConnectorConnectionRepository;
import io.aetheris.orchestrator.connector.oauth.ProviderIdentityResolver;
import org.springframework.stereotype.Service;

@Service
public class ProviderAccountContinuityService {
    private final ConnectorConnectionRepository connections;
    private final ProviderIdentityResolver identities;

    public ProviderAccountContinuityService(
            ConnectorConnectionRepository connections,
            ProviderIdentityResolver identities) {
        this.connections = connections;
        this.identities = identities;
    }

    public void assertCurrent(ConnectorActionEntity action, String accessToken) {
        if (action == null) throw blocked();

        ConnectorConnectionEntity connection = connections.findById(action.getConnectionId())
                .orElseThrow(this::blocked);
        if (connection.getProvider() != action.getProvider()) throw blocked();

        String expectedAccount = normalize(connection.getExternalAccountRef());
        if (expectedAccount.isBlank()) throw blocked();

        String currentAccount = normalize(identities.resolve(action.getProvider(), accessToken));
        if (!expectedAccount.equals(currentAccount)) throw blocked();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private ConnectorActionBlockedException blocked() {
        return new ConnectorActionBlockedException(
                "Provider account continuity check failed before live connector execution");
    }
}
