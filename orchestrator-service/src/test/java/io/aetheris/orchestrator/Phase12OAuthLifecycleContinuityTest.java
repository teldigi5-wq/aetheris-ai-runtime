package io.aetheris.orchestrator;

import io.aetheris.orchestrator.connector.ConnectorConnectionEntity;
import io.aetheris.orchestrator.connector.ConnectorProvider;
import io.aetheris.orchestrator.connector.action.ConnectorActionBlockedException;
import io.aetheris.orchestrator.connector.oauth.OAuthLifecycleAccountContinuityService;
import io.aetheris.orchestrator.connector.oauth.ProviderIdentityResolver;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class Phase12OAuthLifecycleContinuityTest {
    @Test
    void matchingStableAccountAllowsOAuthLifecycleContinuation() {
        ProviderIdentityResolver identities = mock(ProviderIdentityResolver.class);
        ConnectorConnectionEntity connection = mock(ConnectorConnectionEntity.class);
        when(connection.getProvider()).thenReturn(ConnectorProvider.GITHUB);
        when(connection.getExternalAccountRef()).thenReturn("424242");
        when(identities.resolve(ConnectorProvider.GITHUB, "new-access-token")).thenReturn("424242");

        OAuthLifecycleAccountContinuityService continuity =
                new OAuthLifecycleAccountContinuityService(identities);

        assertThatCode(() -> continuity.assertCurrent(connection, "new-access-token"))
                .doesNotThrowAnyException();
        verify(identities).resolve(ConnectorProvider.GITHUB, "new-access-token");
    }

    @Test
    void reboundAccountFailsClosedWithoutLeakingComparedIdentityOrToken() {
        ProviderIdentityResolver identities = mock(ProviderIdentityResolver.class);
        ConnectorConnectionEntity connection = mock(ConnectorConnectionEntity.class);
        when(connection.getProvider()).thenReturn(ConnectorProvider.GITHUB);
        when(connection.getExternalAccountRef()).thenReturn("424242");
        when(identities.resolve(ConnectorProvider.GITHUB, "rebound-secret-token")).thenReturn("999999");

        OAuthLifecycleAccountContinuityService continuity =
                new OAuthLifecycleAccountContinuityService(identities);

        assertThatThrownBy(() -> continuity.assertCurrent(connection, "rebound-secret-token"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    String message = error.getMessage();
                    assertThat(message)
                            .contains("Provider account continuity check failed during OAuth credential lifecycle")
                            .doesNotContain("rebound-secret-token")
                            .doesNotContain("424242")
                            .doesNotContain("999999");
                });
    }

    @Test
    void missingDurableAccountFailsBeforeProviderIdentityLookup() {
        ProviderIdentityResolver identities = mock(ProviderIdentityResolver.class);
        ConnectorConnectionEntity connection = mock(ConnectorConnectionEntity.class);
        when(connection.getProvider()).thenReturn(ConnectorProvider.GMAIL);
        when(connection.getExternalAccountRef()).thenReturn("  ");

        OAuthLifecycleAccountContinuityService continuity =
                new OAuthLifecycleAccountContinuityService(identities);

        assertThatThrownBy(() -> continuity.assertCurrent(connection, "token"))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(identities);
    }

    @Test
    void providerIdentityResolutionFailureIsRewrappedWithoutTokenLeakage() {
        ProviderIdentityResolver identities = mock(ProviderIdentityResolver.class);
        ConnectorConnectionEntity connection = mock(ConnectorConnectionEntity.class);
        when(connection.getProvider()).thenReturn(ConnectorProvider.CALENDAR);
        when(connection.getExternalAccountRef()).thenReturn("google-sub-123");
        when(identities.resolve(ConnectorProvider.CALENDAR, "secret-access-token"))
                .thenThrow(new ConnectorActionBlockedException(
                        "Current provider account identity could not be verified before live connector execution"));

        OAuthLifecycleAccountContinuityService continuity =
                new OAuthLifecycleAccountContinuityService(identities);

        assertThatThrownBy(() -> continuity.assertCurrent(connection, "secret-access-token"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(error.getMessage())
                        .doesNotContain("secret-access-token")
                        .doesNotContain("google-sub-123"));
    }

    @Test
    void healthIdentityComparisonUsesExactStableAccountMatch() {
        ProviderIdentityResolver identities = mock(ProviderIdentityResolver.class);
        ConnectorConnectionEntity connection = mock(ConnectorConnectionEntity.class);
        when(connection.getProvider()).thenReturn(ConnectorProvider.GITHUB);
        when(connection.getExternalAccountRef()).thenReturn("424242");

        OAuthLifecycleAccountContinuityService continuity =
                new OAuthLifecycleAccountContinuityService(identities);

        assertThat(continuity.matchesResolvedIdentity(connection, "424242")).isTrue();
        assertThat(continuity.matchesResolvedIdentity(connection, " 424242 ")).isTrue();
        assertThat(continuity.matchesResolvedIdentity(connection, "424243")).isFalse();
        assertThat(continuity.matchesResolvedIdentity(connection, "")).isFalse();
    }
}
