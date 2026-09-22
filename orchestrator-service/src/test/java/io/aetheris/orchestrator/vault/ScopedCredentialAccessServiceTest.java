package io.aetheris.orchestrator.vault;

import io.aetheris.orchestrator.execution.InvocationAuditEntity;
import io.aetheris.orchestrator.execution.InvocationAuditService;
import io.aetheris.orchestrator.execution.InvocationKind;
import io.aetheris.orchestrator.execution.InvocationStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.aetheris.orchestrator.vault.SecretAccessRequest.Caller.CLOUD_MODEL_PROVIDER;
import static io.aetheris.orchestrator.vault.SecretAccessRequest.Caller.GITHUB_ADAPTER;
import static io.aetheris.orchestrator.vault.SecretAccessRequest.Purpose.GITHUB_PUBLISH;
import static io.aetheris.orchestrator.vault.SecretAccessRequest.Purpose.GITHUB_READ;
import static io.aetheris.orchestrator.vault.SecretAccessRequest.Purpose.MODEL_INFERENCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScopedCredentialAccessServiceTest {

    private CredentialVault vault;
    private InvocationAuditService audit;
    private SimpleMeterRegistry metrics;
    private ScopedCredentialAccessService service;
    private InvocationAuditEntity auditEntry;

    @BeforeEach
    void setUp() {
        vault = mock(CredentialVault.class);
        audit = mock(InvocationAuditService.class);
        metrics = new SimpleMeterRegistry();
        service = new ScopedCredentialAccessService(
                vault,
                audit,
                metrics,
                "github-api-token",
                "cloud-model-api-key");
        auditEntry = new InvocationAuditEntity(
                UUID.randomUUID(), null, GITHUB_ADAPTER.name(), InvocationKind.TOOL, "credential-vault", "{}");
        when(audit.start(isNull(), anyString(), eq(InvocationKind.TOOL), eq("credential-vault"), anyMap()))
                .thenReturn(auditEntry);
    }

    @Test
    void authorizedResolutionReturnsSecretOnlyAfterRedactedAuditCompletes() {
        String literalSecret = "phase11-secret-value";
        when(vault.resolve("github-api-token")).thenReturn(Optional.of(literalSecret.toCharArray()));

        Optional<char[]> resolved = service.resolve(new SecretAccessRequest(
                GITHUB_ADAPTER, GITHUB_PUBLISH, "github-api-token"));

        assertThat(resolved).isPresent();
        assertThat(new String(resolved.orElseThrow())).isEqualTo(literalSecret);
        verify(vault).resolve("github-api-token");
        verify(audit).finish(eq(auditEntry.getId()), eq(InvocationStatus.SUCCEEDED),
                eq("Scoped credential access granted"), anyMap());

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> metadata = ArgumentCaptor.forClass(Map.class);
        verify(audit).start(isNull(), eq(GITHUB_ADAPTER.name()), eq(InvocationKind.TOOL),
                eq("credential-vault"), metadata.capture());
        String serialized = metadata.getValue().toString();
        assertThat(serialized)
                .contains("aliasFingerprint", "secretValueRedacted=true")
                .doesNotContain("github-api-token")
                .doesNotContain(literalSecret);
        assertThat(metrics.get("aetheris_secret_access_total")
                .tag("caller", GITHUB_ADAPTER.name())
                .tag("purpose", GITHUB_PUBLISH.name())
                .tag("result", "resolved")
                .counter().count()).isEqualTo(1.0d);
    }

    @Test
    void crossProviderAliasIsDeniedBeforeVaultResolution() {
        assertThatThrownBy(() -> service.resolve(new SecretAccessRequest(
                CLOUD_MODEL_PROVIDER, MODEL_INFERENCE, "github-api-token")))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Credential access denied by scoped secret policy");

        verify(vault, never()).resolve(anyString());
        verify(audit).finish(eq(auditEntry.getId()), eq(InvocationStatus.BLOCKED),
                eq("Scoped credential access denied"), anyMap());
        assertThat(metrics.get("aetheris_secret_access_total")
                .tag("caller", CLOUD_MODEL_PROVIDER.name())
                .tag("purpose", MODEL_INFERENCE.name())
                .tag("result", "denied")
                .counter().count()).isEqualTo(1.0d);
    }

    @Test
    void mismatchedPurposeIsDeniedBeforeVaultResolution() {
        assertThatThrownBy(() -> service.resolve(new SecretAccessRequest(
                GITHUB_ADAPTER, MODEL_INFERENCE, "github-api-token")))
                .isInstanceOf(SecurityException.class);

        verify(vault, never()).resolve(anyString());
    }

    @Test
    void missingCredentialIsAuditedWithoutLeakingAlias() {
        when(vault.resolve("cloud-model-api-key")).thenReturn(Optional.empty());

        Optional<char[]> resolved = service.resolve(new SecretAccessRequest(
                CLOUD_MODEL_PROVIDER, MODEL_INFERENCE, "cloud-model-api-key"));

        assertThat(resolved).isEmpty();
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> metadata = ArgumentCaptor.forClass(Map.class);
        verify(audit).finish(eq(auditEntry.getId()), eq(InvocationStatus.FAILED),
                eq("Credential is unavailable"), metadata.capture());
        assertThat(metadata.getValue().toString())
                .contains("result=UNAVAILABLE")
                .doesNotContain("cloud-model-api-key");
    }

    @Test
    void auditStartFailurePreventsVaultResolution() {
        reset(audit);
        when(audit.start(isNull(), anyString(), eq(InvocationKind.TOOL), eq("credential-vault"), anyMap()))
                .thenThrow(new IllegalStateException("audit unavailable"));

        assertThatThrownBy(() -> service.resolve(new SecretAccessRequest(
                GITHUB_ADAPTER, GITHUB_READ, "github-api-token")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit unavailable");

        verify(vault, never()).resolve(anyString());
    }

    @Test
    void auditCompletionFailureZeroesResolvedSecretAndFailsClosed() {
        char[] secret = "must-be-zeroed".toCharArray();
        when(vault.resolve("github-api-token")).thenReturn(Optional.of(secret));
        when(audit.finish(eq(auditEntry.getId()), eq(InvocationStatus.SUCCEEDED), anyString(), anyMap()))
                .thenThrow(new IllegalStateException("audit finish failed"));

        assertThatThrownBy(() -> service.resolve(new SecretAccessRequest(
                GITHUB_ADAPTER, GITHUB_READ, "github-api-token")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Credential access audit completion failed");

        assertThat(secret).containsOnly('\0');
    }

    @Test
    void availabilityUsesMetadataOnlyAndStillEnforcesScope() {
        when(vault.describe("cloud-model-api-key"))
                .thenReturn(new CredentialDescriptor("cloud-model-api-key", "environment", true));

        assertThat(service.available(new SecretAccessRequest(
                CLOUD_MODEL_PROVIDER, MODEL_INFERENCE, "cloud-model-api-key"))).isTrue();
        assertThat(service.available(new SecretAccessRequest(
                GITHUB_ADAPTER, GITHUB_READ, "cloud-model-api-key"))).isFalse();

        verify(vault, never()).resolve(anyString());
        verify(vault, times(1)).describe("cloud-model-api-key");
        verifyNoMoreInteractions(vault);
    }

    @Test
    void invalidContextNeverTouchesVaultOrAudit() {
        assertThat(service.available(new SecretAccessRequest(null, MODEL_INFERENCE, "cloud-model-api-key"))).isFalse();
        assertThatThrownBy(() -> service.resolve(new SecretAccessRequest(
                GITHUB_ADAPTER, GITHUB_READ, " ")))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(vault);
        verify(audit, never()).start(any(), anyString(), any(), anyString(), anyMap());
        assertThat(Arrays.asList(GITHUB_READ, GITHUB_PUBLISH, MODEL_INFERENCE)).hasSize(3);
    }
}
