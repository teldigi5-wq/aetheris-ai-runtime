package io.aetheris.orchestrator.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * S0 review-only synthetic single-statement task + signed-session proof.
 *
 * Not a Spring bean, deployed Identity store, HTTP/SSE gate or release grant.
 * Revocation table names belong to disposable compatibility fixtures, NOT
 * approved production migrations. Real resource fencing, writer audit,
 * authenticated service identity and a least-privilege reader are missing.
 * pg_is_in_recovery() cannot detect two divergent writable primaries.
 */
public final class RuntimeVerifiedTaskSessionReadProbe {
    private static final String SELECTOR = "[a-z0-9][a-z0-9_-]{0,95}";
    private static final String SCOPED_SESSION_READ = """
        SELECT EXISTS (
          SELECT 1 FROM aetheris_tasks t
          JOIN tenant_memberships tm
            ON tm.tenant_key=t.owner_tenant_key
           AND tm.identity_uid=t.owner_identity_uid
          JOIN project_memberships pm
            ON pm.tenant_key=t.owner_tenant_key
           AND pm.project_key=t.owner_project_key
           AND pm.identity_uid=t.owner_identity_uid
          JOIN synthetic_session_registration active
            ON active.uid=t.owner_identity_uid
          WHERE current_setting('transaction_isolation')='read committed'
            AND NOT pg_is_in_recovery()
            AND t.id=? AND t.owner_identity_uid=?
            AND t.owner_tenant_key=? AND t.owner_project_key=?
            AND t.owner_verified_at IS NOT NULL
            AND tm.status='ACTIVE' AND tm.revoked_at IS NULL
            AND (tm.valid_until IS NULL OR tm.valid_until>clock_timestamp())
            AND pm.status='ACTIVE' AND pm.revoked_at IS NULL
            AND (pm.valid_until IS NULL OR pm.valid_until>clock_timestamp())
            AND active.issuer=? AND active.kid=? AND active.jti=?
            AND active.uid=? AND active.iat_seconds=? AND active.state='ACTIVE'
            AND NOT EXISTS (
              SELECT 1 FROM blocked_signer_kids blocked
              WHERE blocked.issuer=? AND blocked.kid=?)
            AND NOT EXISTS (
              SELECT 1 FROM principal_epochs epoch
              WHERE epoch.principal_id=?
                AND ?<=epoch.reject_issued_at_or_before_epoch)
        )
        """;

    /**
     * Always denies invalid signed facts, selectors, unavailable DB or SQL
     * error. Positive synthetic results MUST NOT admit real task/mission/SSE.
     */
    public boolean canReadSynthetic(Connection connection,
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal,
            String tenant, String project, UUID taskId) {
        try {
            if (connection == null || principal == null
                    || connection.getAutoCommit()
                    || !principal.hasScope("orchestrator:read")
                    || taskId == null || !valid(tenant) || !valid(project)) {
                return false;
            }
            var signed = principal.requireRevocationFacts();
            long iat = signed.signedIssuedAt().getEpochSecond();
            if (signed.signedIdentityUid() <= 0 || iat <= 0
                    || signed.verifiedIssuer().length() > 255
                    || signed.verifiedKeyId().length() > 64) return false;
            try (PreparedStatement read = connection.prepareStatement(
                    SCOPED_SESSION_READ)) {
                read.setQueryTimeout(2); // disposable test budget only
                read.setObject(1, taskId);
                read.setLong(2, signed.signedIdentityUid());
                read.setString(3, tenant);
                read.setString(4, project);
                read.setString(5, signed.verifiedIssuer());
                read.setString(6, signed.verifiedKeyId());
                read.setString(7, signed.signedTokenId());
                read.setLong(8, signed.signedIdentityUid());
                read.setLong(9, iat);
                read.setString(10, signed.verifiedIssuer());
                read.setString(11, signed.verifiedKeyId());
                read.setLong(12, signed.signedIdentityUid());
                read.setLong(13, iat);
                try (ResultSet decision = read.executeQuery()) {
                    return decision.next() && decision.getBoolean(1);
                }
            }
        } catch (SQLException | RuntimeException denied) {
            // Do not leak signed identity, SQL errors or authority details.
            return false;
        }
    }

    private static boolean valid(String selector) {
        return selector != null && selector.matches(SELECTOR);
    }
}
