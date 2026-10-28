package io.aetheris.orchestrator.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-only SQL authorization prototype, not an injectable service or route.
 * The UID comes from a VerifiedPrincipal whose constructor is PRIVATE to
 * RuntimePinnedPrincipalVerifier after RS256 signature validation. Tenant
 * and project parameters are untrusted *selectors*, not identity evidence.
 *
 * Membership lookup is executed on the SAME database transaction as the
 * task ownership predicate. Revocation must be checked on each read; no
 * membership or authorization decision is cached here.
 *
 * A future runtime service must separately prove which tenant registry is
 * authoritative, enforce real principal session/revocation and implement
 * row-scoped writes and mission/SSE isolation before removing HTTP lockdown.
 */
public final class RuntimeVerifiedTaskReadProbe {
    private static final String KEY = "[a-z0-9][a-z0-9_-]{0,95}";
    private static final String QUERY = """
        SELECT EXISTS (
          SELECT 1 FROM aetheris_tasks t
          JOIN tenant_memberships tm ON
            tm.tenant_key = t.owner_tenant_key
            AND tm.identity_uid = t.owner_identity_uid
          JOIN project_memberships pm ON
            pm.tenant_key = t.owner_tenant_key
            AND pm.project_key = t.owner_project_key
            AND pm.identity_uid = t.owner_identity_uid
          WHERE t.id = ?
            AND t.owner_identity_uid = ?
            AND t.owner_tenant_key = ?
            AND t.owner_project_key = ?
            AND t.owner_verified_at IS NOT NULL
            AND tm.status = 'ACTIVE' AND tm.revoked_at IS NULL
            AND (tm.valid_until IS NULL OR tm.valid_until > CURRENT_TIMESTAMP)
            AND pm.status = 'ACTIVE' AND pm.revoked_at IS NULL
            AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
        )
        """;

    public boolean canRead(Connection connection,
                           RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal,
                           String requestedTenant,
                           String requestedProject,
                           UUID taskId) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(principal, "cryptographically verified principal");
        // A valid signed token does not necessarily have read authority.
        if (taskId == null || !principal.hasScope("orchestrator:read")
                || !validSelector(requestedTenant)
                || !validSelector(requestedProject)) {
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement(QUERY)) {
            statement.setObject(1, taskId);
            statement.setLong(2, principal.identityUid());
            statement.setString(3, requestedTenant);
            statement.setString(4, requestedProject);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) throw new SQLException("No membership decision returned");
                return results.getBoolean(1);
            }
        }
    }

    private static boolean validSelector(String key) {
        return key != null && key.matches(KEY);
    }
}
