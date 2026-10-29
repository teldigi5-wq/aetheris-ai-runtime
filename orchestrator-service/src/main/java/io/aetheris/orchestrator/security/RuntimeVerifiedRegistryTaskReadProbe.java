package io.aetheris.orchestrator.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * S0-D restricted PostgreSQL read-only registry probe, not a Spring bean.
 * Only a sealed, cryptographically verified principal can supply the UID.
 * This does not verify session revocation, own the connection or expose HTTP.
 * The dedicated database role must separately DENY all mutation/DDL.
 */
public final class RuntimeVerifiedRegistryTaskReadProbe {
    private static final String SELECTOR = "[a-z0-9][a-z0-9_-]{0,95}";
    private static final String SCOPED_READ = """
        SELECT EXISTS (
          SELECT 1
          FROM aetheris_tasks t
          JOIN registry_tenants tenant
            ON tenant.tenant_key = t.owner_tenant_key
          JOIN registry_projects project
            ON project.tenant_key = t.owner_tenant_key
            AND project.project_key = t.owner_project_key
          JOIN registry_tenant_memberships tm
            ON tm.tenant_key = t.owner_tenant_key
            AND tm.identity_uid = t.owner_identity_uid
          JOIN registry_project_memberships pm
            ON pm.tenant_key = t.owner_tenant_key
            AND pm.project_key = t.owner_project_key
            AND pm.identity_uid = t.owner_identity_uid
          WHERE t.id = ?
            AND t.owner_identity_uid = ?
            AND t.owner_tenant_key = ?
            AND t.owner_project_key = ?
            AND t.owner_verified_at IS NOT NULL
            AND tenant.status = 'ACTIVE'
            AND project.status = 'ACTIVE'
            AND tm.status = 'ACTIVE' AND tm.revoked_at IS NULL
            AND (tm.valid_until IS NULL OR tm.valid_until > clock_timestamp())
            AND pm.status = 'ACTIVE' AND pm.revoked_at IS NULL
            AND (pm.valid_until IS NULL OR pm.valid_until > clock_timestamp())
        )
        """;

    public boolean canRead(Connection connection,
                           RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal,
                           String requestedTenant,
                           String requestedProject,
                           UUID taskId) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(principal, "sealed signed Identity principal");
        if (!connection.isReadOnly() || connection.getAutoCommit()) {
            throw new IllegalStateException("Explicit read-only transaction required");
        }
        if (!principal.hasScope("orchestrator:read")
                || taskId == null
                || requestedTenant == null || !requestedTenant.matches(SELECTOR)
                || requestedProject == null || !requestedProject.matches(SELECTOR)) {
            return false;
        }
        try (PreparedStatement read = connection.prepareStatement(SCOPED_READ)) {
            read.setObject(1, taskId);
            read.setLong(2, principal.identityUid());
            read.setString(3, requestedTenant);
            read.setString(4, requestedProject);
            try (ResultSet result = read.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("No scoped registry read decision");
                }
                return result.getBoolean(1);
            }
        }
    }
}
