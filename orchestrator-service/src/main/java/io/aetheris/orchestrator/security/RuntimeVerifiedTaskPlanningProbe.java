package io.aetheris.orchestrator.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * S0-D review-only, NON-INJECTABLE JDBC write proof.
 *
 * Not a Spring bean, task service, generic state-transition API, or authority
 * to run an agent. The only operation rehearsed is QUEUED -> PLANNING CAS.
 * Caller owns the transaction and MUST commit or roll back. All task HTTP
 * routes stay unconditionally locked down by OwnerlessTaskMissionHttpFilter.
 *
 * No raw caller UID is accepted: only the sealed, independently RS256-verified
 * VerifiedPrincipal. Tenant and project are selectors, never authority.
 */
public final class RuntimeVerifiedTaskPlanningProbe {
    private static final String SELECTOR = "[a-z0-9][a-z0-9_-]{0,95}";

    // PostgreSQL row-level FOR SHARE prevents concurrent membership revoke
    // (and tenant/project suspension) from committing while our transaction
    // holds authorization locks. No membership cache or stale precheck.
    private static final String LOCK_MEMBERSHIP = """
        SELECT 1
        FROM registry_tenants tenant
        JOIN registry_projects project
          ON project.tenant_key = tenant.tenant_key
        JOIN registry_tenant_memberships tm
          ON tm.tenant_key = tenant.tenant_key
        JOIN registry_project_memberships pm
          ON pm.tenant_key = project.tenant_key
          AND pm.project_key = project.project_key
          AND pm.identity_uid = tm.identity_uid
        WHERE tenant.tenant_key = ?
          AND project.project_key = ?
          AND tm.identity_uid = ?
          AND tenant.status = 'ACTIVE' AND project.status = 'ACTIVE'
          AND tm.status = 'ACTIVE' AND tm.revoked_at IS NULL
          AND pm.status = 'ACTIVE' AND pm.revoked_at IS NULL
          AND (tm.valid_until IS NULL OR tm.valid_until > clock_timestamp())
          AND (pm.valid_until IS NULL OR pm.valid_until > clock_timestamp())
          AND tm.member_role IN ('OWNER','ADMIN','MEMBER')
          AND pm.member_role IN ('ADMIN','MEMBER')
        FOR SHARE OF tenant, project, tm, pm
        """;

    private static final String CLAIM_QUEUED = """
        UPDATE aetheris_tasks
        SET state = 'PLANNING', version = version + 1, updated_at = clock_timestamp()
        WHERE id = ? AND owner_identity_uid = ?
          AND owner_tenant_key = ? AND owner_project_key = ?
          AND owner_verified_at IS NOT NULL
          AND state = 'QUEUED' AND version = ?
        """;

    /**
     * A false return is a generic denial or failed CAS, without revealing
     * whether another tenant owns the UUID. Throws for an invalid transaction
     * boundary or database failure. The successful update remains UNCOMMITTED
     * until the caller explicitly commits.
     */
    public boolean tryPlanQueuedTask(
            Connection connection,
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal,
            String requestedTenant, String requestedProject,
            UUID taskId, long expectedVersion) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(principal, "cryptographically verified principal");

        if (connection.getAutoCommit() ||
                connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
            throw new IllegalStateException("Explicit READ_COMMITTED transaction required");
        }
        if (connection.isReadOnly()) {
            throw new IllegalStateException("Read-only transaction cannot mutate tasks");
        }
        if (taskId == null || expectedVersion < 0
                || !principal.hasScope("orchestrator:write")
                || requestedTenant == null || !requestedTenant.matches(SELECTOR)
                || requestedProject == null || !requestedProject.matches(SELECTOR)) {
            return false;
        }

        // Keep authorization row locks until COMMIT/ROLLBACK, including on
        // denied task CAS. A concurrent revoke blocks and takes effect before
        // the next independently authorized transaction.
        try (PreparedStatement membership = connection.prepareStatement(LOCK_MEMBERSHIP)) {
            membership.setString(1, requestedTenant);
            membership.setString(2, requestedProject);
            membership.setLong(3, principal.identityUid());
            try (ResultSet rows = membership.executeQuery()) {
                if (!rows.next()) return false;
            }
        }
        try (PreparedStatement update = connection.prepareStatement(CLAIM_QUEUED)) {
            update.setObject(1, taskId);
            update.setLong(2, principal.identityUid());
            update.setString(3, requestedTenant);
            update.setString(4, requestedProject);
            update.setLong(5, expectedVersion);
            return update.executeUpdate() == 1;
        }
    }
}
