package io.aetheris.orchestrator.vault;

import io.aetheris.orchestrator.execution.InvocationAuditEntity;
import io.aetheris.orchestrator.execution.InvocationAuditService;
import io.aetheris.orchestrator.execution.InvocationKind;
import io.aetheris.orchestrator.execution.InvocationStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import static io.aetheris.orchestrator.vault.SecretAccessRequest.Caller.CLOUD_MODEL_PROVIDER;
import static io.aetheris.orchestrator.vault.SecretAccessRequest.Caller.GITHUB_ADAPTER;
import static io.aetheris.orchestrator.vault.SecretAccessRequest.Purpose.GITHUB_PUBLISH;
import static io.aetheris.orchestrator.vault.SecretAccessRequest.Purpose.GITHUB_READ;
import static io.aetheris.orchestrator.vault.SecretAccessRequest.Purpose.MODEL_INFERENCE;

/**
 * Production boundary for retrieving credential values.
 *
 * <p>The underlying {@link CredentialVault} remains responsible for secret storage,
 * while this service adds caller/purpose policy, audit evidence, bounded telemetry,
 * and fail-closed behavior before a secret value can leave the vault boundary.</p>
 */
@Service
public class ScopedCredentialAccessService {

    private static final String AUDIT_TARGET = "credential-vault";

    private final CredentialVault vault;
    private final InvocationAuditService audit;
    private final MeterRegistry metrics;
    private final String githubAlias;
    private final String cloudModelAlias;

    public ScopedCredentialAccessService(
            CredentialVault vault,
            InvocationAuditService audit,
            MeterRegistry metrics,
            @Value("${aetheris.execution.github.credential-alias:github-api-token}") String githubAlias,
            @Value("${aetheris.cloud-model.credential-alias:cloud-model-api-key}") String cloudModelAlias) {
        this.vault = vault;
        this.audit = audit;
        this.metrics = metrics;
        this.githubAlias = normalizeConfiguredAlias(githubAlias);
        this.cloudModelAlias = normalizeConfiguredAlias(cloudModelAlias);
    }

    /**
     * Resolve a credential value only after caller/purpose/alias authorization and
     * durable audit-start evidence. Audit completion must also succeed before the
     * secret is returned to the caller.
     */
    public Optional<char[]> resolve(SecretAccessRequest request) {
        ValidatedRequest validated = validate(request);
        InvocationAuditEntity entry = audit.start(
                null,
                validated.caller().name(),
                InvocationKind.TOOL,
                AUDIT_TARGET,
                metadata(validated, "ATTEMPT"));

        if (!authorized(validated)) {
            finish(entry, InvocationStatus.BLOCKED, "Scoped credential access denied", validated, "DENIED");
            count(validated, "denied");
            throw new SecurityException("Credential access denied by scoped secret policy");
        }

        final Optional<char[]> resolved;
        try {
            resolved = vault.resolve(validated.alias());
        } catch (RuntimeException exception) {
            finish(entry, InvocationStatus.FAILED, "Credential vault resolution failed", validated, "ERROR");
            count(validated, "error");
            throw new IllegalStateException("Credential vault resolution failed", exception);
        }

        if (resolved.isEmpty()) {
            finish(entry, InvocationStatus.FAILED, "Credential is unavailable", validated, "UNAVAILABLE");
            count(validated, "unavailable");
            return Optional.empty();
        }

        char[] secret = resolved.get();
        try {
            finish(entry, InvocationStatus.SUCCEEDED, "Scoped credential access granted", validated, "RESOLVED");
            count(validated, "resolved");
            return Optional.of(secret);
        } catch (RuntimeException auditFailure) {
            Arrays.fill(secret, '\0');
            count(validated, "audit_failure");
            throw new IllegalStateException("Credential access audit completion failed", auditFailure);
        }
    }

    /**
     * Non-secret readiness check. It applies the same scope policy but does not emit
     * a secret-access audit event because no credential value is retrieved.
     */
    public boolean available(SecretAccessRequest request) {
        ValidatedRequest validated;
        try {
            validated = validate(request);
        } catch (RuntimeException exception) {
            return false;
        }
        if (!authorized(validated)) return false;
        return vault.describe(validated.alias()).available();
    }

    private boolean authorized(ValidatedRequest request) {
        return switch (request.caller()) {
            case GITHUB_ADAPTER -> (request.purpose() == GITHUB_READ || request.purpose() == GITHUB_PUBLISH)
                    && !githubAlias.isBlank()
                    && githubAlias.equals(request.alias());
            case CLOUD_MODEL_PROVIDER -> request.purpose() == MODEL_INFERENCE
                    && !cloudModelAlias.isBlank()
                    && cloudModelAlias.equals(request.alias());
        };
    }

    private ValidatedRequest validate(SecretAccessRequest request) {
        if (request == null) throw new IllegalArgumentException("Secret access context is required");
        if (request.caller() == null) throw new IllegalArgumentException("Secret access caller is required");
        if (request.purpose() == null) throw new IllegalArgumentException("Secret access purpose is required");
        if (request.alias() == null || request.alias().isBlank()) throw new IllegalArgumentException("Credential alias is required");
        String alias = request.alias().trim();
        if (alias.length() > 120) throw new IllegalArgumentException("Credential alias is too long");
        return new ValidatedRequest(request.caller(), request.purpose(), alias, fingerprint(alias));
    }

    private void finish(
            InvocationAuditEntity entry,
            InvocationStatus status,
            String detail,
            ValidatedRequest request,
            String result) {
        audit.finish(entry.getId(), status, detail, metadata(request, result));
    }

    private Map<String, Object> metadata(ValidatedRequest request, String result) {
        return Map.of(
                "caller", request.caller().name(),
                "purpose", request.purpose().name(),
                "aliasFingerprint", request.aliasFingerprint(),
                "result", result,
                "secretValueRedacted", true);
    }

    private void count(ValidatedRequest request, String result) {
        metrics.counter(
                "aetheris_secret_access_total",
                "caller", request.caller().name(),
                "purpose", request.purpose().name(),
                "result", result).increment();
    }

    private String fingerprint(String alias) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(alias.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalizeConfiguredAlias(String alias) {
        if (alias == null) return "";
        String normalized = alias.trim();
        if (normalized.length() > 120) throw new IllegalStateException("Configured credential alias is too long");
        return normalized;
    }

    private record ValidatedRequest(
            SecretAccessRequest.Caller caller,
            SecretAccessRequest.Purpose purpose,
            String alias,
            String aliasFingerprint) {
    }
}
