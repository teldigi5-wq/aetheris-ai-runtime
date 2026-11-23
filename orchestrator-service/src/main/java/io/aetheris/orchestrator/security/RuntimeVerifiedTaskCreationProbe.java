package io.aetheris.orchestrator.security;

import io.aetheris.orchestrator.policy.OperationMode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * Non-HTTP, non-Spring research boundary for transactional owner-bound writes.
 *
 * The signed principal can only be constructed by RuntimePinnedPrincipalVerifier,
 * but a signed identity is NOT a tenant grant or a live authenticated session.
 * The caller must separately prove session/JTI revocation, active workload
 * identity, B62 fencing, registry custody and approved schema before any route
 * or service activation. This class is intentionally NOT a Spring bean.
 *
 * The writable primary must use READ COMMITTED with an explicit transaction.
 * FOR SHARE holds registry/membership rows against concurrent revocation until
 * caller commits; caller MUST roll back on any error. No global task/mission
 * API or event stream is enabled by this proof.
 */
public final class RuntimeVerifiedTaskCreationProbe {
    private static final String SELECTOR = "[a-z0-9][a-z0-9_-]{0,95}";

    private static final String INSERT = """
        WITH authorized AS MATERIALIZED (
          SELECT tm.identity_uid, tenant.tenant_key, project.project_key
          FROM registry_tenants tenant
          JOIN registry_projects project
            ON project.tenant_key = tenant.tenant_key
          JOIN registry_tenant_memberships tm
            ON tm.tenant_key = tenant.tenant_key
          JOIN registry_project_memberships pm
            ON pm.tenant_key = tenant.tenant_key
           AND pm.project_key = project.project_key
           AND pm.identity_uid = tm.identity_uid
          WHERE tm.identity_uid = ?
            AND tenant.tenant_key = ?
            AND project.project_key = ?
            AND tenant.status = 'ACTIVE'
            AND project.status = 'ACTIVE'
            AND tm.status = 'ACTIVE' AND tm.revoked_at IS NULL
            AND pm.status = 'ACTIVE' AND pm.revoked_at IS NULL
            AND tm.member_role IN ('OWNER', 'ADMIN', 'MEMBER')
            AND pm.member_role IN ('ADMIN', 'MEMBER')
            AND (tm.valid_until IS NULL OR tm.valid_until > clock_timestamp())
            AND (pm.valid_until IS NULL OR pm.valid_until > clock_timestamp())
          FOR SHARE OF tenant, project, tm, pm
        )
        INSERT INTO aetheris_tasks (
          id, title, command_text, state, mode, created_at, updated_at,
          version, owner_identity_uid, owner_tenant_key, owner_project_key,
          owner_verified_at
        )
        SELECT ?, ?, ?, 'QUEUED', ?, clock_timestamp(), clock_timestamp(),
               0, authorized.identity_uid, authorized.tenant_key,
               authorized.project_key, clock_timestamp()
        FROM authorized
        WHERE current_setting('transaction_isolation') = 'read committed'
          AND NOT pg_is_in_recovery()
        RETURNING id
        """;

    /**
     * Writes one owner-bound task, but does NOT grant API access or start a
     * task's execution. No event publication until separately reviewed
     * post-commit outbox and scoped SSE are implemented.
     */
    public UUID create(Connection connection,
                       RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal,
                       String tenant, String project,
                       String title, String command,
                       OperationMode mode) throws SQLException {
        Objects.requireNonNull(connection, "database connection");
        Objects.requireNonNull(principal, "signed Identity principal");

        if (connection.getAutoCommit()
                || connection.isReadOnly()
                || connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
            throw new IllegalStateException("Explicit writable READ COMMITTED transaction required");
        }
        if (!principal.hasScope("orchestrator:write")) {
            throw new SecurityException("Signed task-write scope required");
        }
        // A signed JTI is mandatory but NOT a revocation check. No endpoints
        // may call this class until session-status enforcement is integrated.
        principal.requireRevocationFacts();
        if (!valid(tenant, 96) || !valid(project, 96)
                || !validText(title, 160) || !validText(command, 8000)
                || mode == null) {
            throw new IllegalArgumentException("Invalid owner-task inputs");
        }

        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setLong(1, principal.identityUid());
            statement.setString(2, tenant);
            statement.setString(3, project);
            statement.setObject(4, id);
            statement.setString(5, title);
            statement.setString(6, command);
            statement.setString(7, mode.name());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SecurityException("No active verified task membership");
                }
                UUID persisted = result.getObject(1, UUID.class);
                if (!id.equals(persisted) || result.next()) {
                    throw new SQLException("Unexpected task-write cardinality");
                }
            }
        }
        return id;
    }

    private static boolean valid(String value, int limit) {
        return value != null && value.length() <= limit
            && value.matches(SELECTOR);
    }

    private static boolean validText(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max
            && value.equals(value.strip())
            && value.chars().noneMatch(Character::isISOControl);
    }
}
