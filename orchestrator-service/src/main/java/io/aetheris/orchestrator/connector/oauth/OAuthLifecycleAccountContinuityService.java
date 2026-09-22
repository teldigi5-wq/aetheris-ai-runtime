package io.aetheris.orchestrator.connector.oauth;

import io.aetheris.orchestrator.connector.ConnectorConnectionEntity;
import io.aetheris.orchestrator.connector.action.ConnectorActionBlockedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OAuthLifecycleAccountContinuityService {
    private static final String BLOCKED_MESSAGE =
            "Provider account continuity check failed during OAuth credential lifecycle";

    private final ProviderIdentityResolver identities;

    public OAuthLifecycleAccountContinuityService(ProviderIdentityResolver identities) {
        this.identities = identities;
    }

    public void assertCurrent(ConnectorConnectionEntity connection, String accessToken) {
        if (connection == null || connection.getProvider() == null
                || accessToken == null || accessToken.isBlank()) {
            throw blocked();
        }
        String expectedAccount = normalize(connection.getExternalAccountRef());
        if (expectedAccount.isBlank()) {
            throw blocked();
        }

        String currentAccount;
        try {
            currentAccount = normalize(identities.resolve(connection.getProvider(), accessToken));
        } catch (ConnectorActionBlockedException error) {
            throw blocked(error);
        } catch (RuntimeException error) {
            throw blocked(error);
        }
        if (!expectedAccount.equals(currentAccount)) {
            throw blocked();
        }
    }

    public boolean matchesResolvedIdentity(ConnectorConnectionEntity connection, String providerAccountRef) {
        if (connection == null || connection.getProvider() == null) {
            return false;
        }
        String expectedAccount = normalize(connection.getExternalAccountRef());
        String currentAccount = normalize(providerAccountRef);
        return !expectedAccount.isBlank() && expectedAccount.equals(currentAccount);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private ResponseStatusException blocked() {
        return new ResponseStatusException(HttpStatus.CONFLICT, BLOCKED_MESSAGE);
    }

    private ResponseStatusException blocked(Throwable cause) {
        return new ResponseStatusException(HttpStatus.CONFLICT, BLOCKED_MESSAGE, cause);
    }
}
