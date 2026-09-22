package io.aetheris.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aetheris.orchestrator.connector.ConnectorConnectionEntity;
import io.aetheris.orchestrator.connector.ConnectorConnectionRepository;
import io.aetheris.orchestrator.connector.ConnectorProvider;
import io.aetheris.orchestrator.connector.action.ConnectorActionBlockedException;
import io.aetheris.orchestrator.connector.action.ConnectorActionEntity;
import io.aetheris.orchestrator.connector.action.ConnectorActionKind;
import io.aetheris.orchestrator.connector.action.LiveConnectorWriteExecutor;
import io.aetheris.orchestrator.connector.action.ProviderAccountContinuityService;
import io.aetheris.orchestrator.connector.action.ProviderWriteEndpointRegistry;
import io.aetheris.orchestrator.connector.oauth.InMemoryConnectorCredentialVault;
import io.aetheris.orchestrator.connector.oauth.ProviderCredentialEntity;
import io.aetheris.orchestrator.connector.oauth.ProviderCredentialRepository;
import io.aetheris.orchestrator.connector.oauth.ProviderCredentialStatus;
import io.aetheris.orchestrator.connector.oauth.ProviderIdentityResolver;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class Phase12ProviderAccountContinuityTest {
    @Test
    void sameProviderAccountSurvivesAccessTokenRotation() {
        ConnectorConnectionRepository connections = mock(ConnectorConnectionRepository.class);
        ProviderIdentityResolver identities = mock(ProviderIdentityResolver.class);
        ConnectorConnectionEntity connection = mock(ConnectorConnectionEntity.class);
        ConnectorActionEntity action = mock(ConnectorActionEntity.class);
        UUID connectionId = UUID.randomUUID();
        when(action.getConnectionId()).thenReturn(connectionId);
        when(action.getProvider()).thenReturn(ConnectorProvider.GITHUB);
        when(connections.findById(connectionId)).thenReturn(Optional.of(connection));
        when(connection.getProvider()).thenReturn(ConnectorProvider.GITHUB);
        when(connection.getExternalAccountRef()).thenReturn("424242");
        when(identities.resolve(ConnectorProvider.GITHUB, "token-v1")).thenReturn("424242");
        when(identities.resolve(ConnectorProvider.GITHUB, "token-v2")).thenReturn("424242");
        ProviderAccountContinuityService continuity = new ProviderAccountContinuityService(connections, identities);
        assertThatCode(() -> continuity.assertCurrent(action, "token-v1")).doesNotThrowAnyException();
        assertThatCode(() -> continuity.assertCurrent(action, "token-v2")).doesNotThrowAnyException();
    }

    @Test
    void credentialRebindingToDifferentProviderAccountFailsClosedWithoutTokenLeakage() {
        ConnectorConnectionRepository connections = mock(ConnectorConnectionRepository.class);
        ProviderIdentityResolver identities = mock(ProviderIdentityResolver.class);
        ConnectorConnectionEntity connection = mock(ConnectorConnectionEntity.class);
        ConnectorActionEntity action = mock(ConnectorActionEntity.class);
        UUID connectionId = UUID.randomUUID();
        when(action.getConnectionId()).thenReturn(connectionId);
        when(action.getProvider()).thenReturn(ConnectorProvider.GITHUB);
        when(connections.findById(connectionId)).thenReturn(Optional.of(connection));
        when(connection.getProvider()).thenReturn(ConnectorProvider.GITHUB);
        when(connection.getExternalAccountRef()).thenReturn("424242");
        when(identities.resolve(ConnectorProvider.GITHUB, "rebound-token-secret")).thenReturn("999999");
        ProviderAccountContinuityService continuity = new ProviderAccountContinuityService(connections, identities);
        assertThatThrownBy(() -> continuity.assertCurrent(action, "rebound-token-secret"))
                .isInstanceOf(ConnectorActionBlockedException.class)
                .satisfies(error -> assertThat(error.getMessage())
                        .doesNotContain("rebound-token-secret")
                        .doesNotContain("424242")
                        .doesNotContain("999999"));
    }

    @Test
    void providerMismatchAndMissingStableAccountReferenceFailBeforeIdentityLookup() {
        ConnectorConnectionRepository connections = mock(ConnectorConnectionRepository.class);
        ProviderIdentityResolver identities = mock(ProviderIdentityResolver.class);
        ConnectorConnectionEntity connection = mock(ConnectorConnectionEntity.class);
        ConnectorActionEntity action = mock(ConnectorActionEntity.class);
        UUID connectionId = UUID.randomUUID();
        when(action.getConnectionId()).thenReturn(connectionId);
        when(action.getProvider()).thenReturn(ConnectorProvider.GITHUB);
        when(connections.findById(connectionId)).thenReturn(Optional.of(connection));
        when(connection.getProvider()).thenReturn(ConnectorProvider.GMAIL);
        ProviderAccountContinuityService continuity = new ProviderAccountContinuityService(connections, identities);
        assertThatThrownBy(() -> continuity.assertCurrent(action, "token"))
                .isInstanceOf(ConnectorActionBlockedException.class);
        verifyNoInteractions(identities);
        reset(identities);
        when(connection.getProvider()).thenReturn(ConnectorProvider.GITHUB);
        when(connection.getExternalAccountRef()).thenReturn("  ");
        assertThatThrownBy(() -> continuity.assertCurrent(action, "token"))
                .isInstanceOf(ConnectorActionBlockedException.class);
        verifyNoInteractions(identities);
    }

    @Test
    void executorChecksExactResolvedTokenBeforeAnyProviderEndpointIsUsed() {
        ProviderCredentialRepository credentials = mock(ProviderCredentialRepository.class);
        InMemoryConnectorCredentialVault vault = mock(InMemoryConnectorCredentialVault.class);
        ProviderAccountContinuityService continuity = mock(ProviderAccountContinuityService.class);
        ProviderWriteEndpointRegistry endpoints = mock(ProviderWriteEndpointRegistry.class);
        ProviderCredentialEntity credential = mock(ProviderCredentialEntity.class);
        ConnectorActionEntity action = mock(ConnectorActionEntity.class);
        UUID connectionId = UUID.randomUUID();
        when(action.getConnectionId()).thenReturn(connectionId);
        when(action.getProvider()).thenReturn(ConnectorProvider.GITHUB);
        when(action.getActionKind()).thenReturn(ConnectorActionKind.GITHUB_CREATE_ISSUE);
        when(credentials.findByConnectionId(connectionId)).thenReturn(Optional.of(credential));
        when(credential.getProvider()).thenReturn(ConnectorProvider.GITHUB);
        when(credential.getStatus()).thenReturn(ProviderCredentialStatus.ACTIVE);
        when(credential.getExpiresAt()).thenReturn(Instant.now().plusSeconds(300));
        when(credential.getScopesCsv()).thenReturn("public_repo");
        when(credential.getAccessTokenReference()).thenReturn("access-ref");
        when(vault.require("access-ref")).thenReturn("token-v2");
        doThrow(new ConnectorActionBlockedException("Provider account continuity check failed before live connector execution"))
                .when(continuity).assertCurrent(action, "token-v2");
        LiveConnectorWriteExecutor executor = new LiveConnectorWriteExecutor(
                credentials, vault, continuity, endpoints, new ObjectMapper());
        assertThatThrownBy(() -> executor.execute(action)).isInstanceOf(ConnectorActionBlockedException.class);
        verify(vault).require("access-ref");
        verify(continuity).assertCurrent(action, "token-v2");
        verifyNoInteractions(endpoints);
    }
}
