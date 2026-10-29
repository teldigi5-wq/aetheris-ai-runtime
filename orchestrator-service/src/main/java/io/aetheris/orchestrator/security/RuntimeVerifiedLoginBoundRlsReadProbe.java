package io.aetheris.orchestrator.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * S0-D3 NON-INJECTABLE disposal-only adapter demonstrating a signed Identity
 * UID AND an independently authenticated PostgreSQL session_user binding.
 *
 * PostgreSQL FORCE RLS additionally rejects direct SELECTs of other users'
 * task metadata, even when this Java query is bypassed. This does not create
 * a deployable per-person DB credential architecture or a live HTTP endpoint.
 */
public final class RuntimeVerifiedLoginBoundRlsReadProbe {
    private static final String SELECTOR = "[a-z0-9][a-z0-9_-]{0,95}";
    private static final String QUERY = """
        SELECT EXISTS (
          SELECT 1 FROM public.aetheris_tasks t
          WHERE t.id = ?
            AND t.owner_identity_uid = ?
            AND t.owner_tenant_key = ?
            AND t.owner_project_key = ?
            AND t.owner_verified_at IS NOT NULL
        )
        """;

    public boolean canRead(Connection connection,
                           RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal,
                           String tenant, String project, UUID taskId) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(principal, "sealed signed principal");
        if (!connection.isReadOnly() || connection.getAutoCommit()
            || connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
            throw new IllegalStateException("Explicit READ_COMMITTED read-only transaction required");
        }
        if (!principal.hasScope("orchestrator:read")
            || taskId == null || tenant == null || !tenant.matches(SELECTOR)
            || project == null || !project.matches(SELECTOR)) {
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement(QUERY)) {
            statement.setObject(1, taskId);
            statement.setLong(2, principal.identityUid());
            statement.setString(3, tenant);
            statement.setString(4, project);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("No task authorization decision");
                return result.getBoolean(1);
            }
        }
    }
}
