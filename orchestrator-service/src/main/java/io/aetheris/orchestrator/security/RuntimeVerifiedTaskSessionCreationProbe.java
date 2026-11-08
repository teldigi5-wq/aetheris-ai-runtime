package io.aetheris.orchestrator.security;

import io.aetheris.orchestrator.policy.OperationMode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * Synthetic, NON-INJECTABLE transactional task-creation admission proof.
 *
 * Requires 3 separately verified signed artefacts, signed session/JTI bound
 * to a currently ACTIVE database row, and membership+fence locks in the SAME
 * SQL statement as the INSERT. NO Spring bean, HTTP route, task dispatcher or
 * production migration. Positive CI results do not authorize runtime use:
 * no real Identity session source, mTLS workload channel, B62 cross-primary
 * fencing, deployed authoritative registry or production database role.
 *
 * Caller owns the transaction and MUST roll back on failure. A return value
 * only identifies an UNCOMMITTED row until the caller commits. Since the
 * signing-key/epoch deny lists have absent-row races, a production design
 * must serialize revocation on an authoritative lock before granting writes.
 */
public final class RuntimeVerifiedTaskSessionCreationProbe {
    private static final String SELECTOR = "[a-z0-9][a-z0-9_-]{0,95}";

    private static final String INSERT = """
        WITH locked_fence AS MATERIALIZED (
          SELECT f.term
          FROM b62_local_resource_fence f
          WHERE f.resource_key='runtime'
            AND f.endpoint=? AND f.term=?
            AND f.state='ACTIVE' AND f.expires_at>clock_timestamp()
          FOR SHARE OF f
        ),
        authorized AS MATERIALIZED (
          SELECT tm.identity_uid, tenant.tenant_key, project.project_key
          FROM locked_fence lf
          CROSS JOIN registry_tenants tenant
          JOIN registry_projects project
            ON project.tenant_key=tenant.tenant_key
          JOIN registry_tenant_memberships tm
            ON tm.tenant_key=tenant.tenant_key
          JOIN registry_project_memberships pm
            ON pm.tenant_key=tenant.tenant_key
           AND pm.project_key=project.project_key
           AND pm.identity_uid=tm.identity_uid
          JOIN synthetic_session_registration active
            ON active.uid=tm.identity_uid
          WHERE tm.identity_uid=?
            AND tenant.tenant_key=? AND project.project_key=?
            AND tenant.status='ACTIVE' AND project.status='ACTIVE'
            AND tm.status='ACTIVE' AND tm.revoked_at IS NULL
            AND pm.status='ACTIVE' AND pm.revoked_at IS NULL
            AND tm.member_role IN ('OWNER','ADMIN','MEMBER')
            AND pm.member_role IN ('ADMIN','MEMBER')
            AND (tm.valid_until IS NULL OR tm.valid_until>clock_timestamp())
            AND (pm.valid_until IS NULL OR pm.valid_until>clock_timestamp())
            AND active.issuer=? AND active.kid=? AND active.jti=?
            AND active.uid=? AND active.iat_seconds=? AND active.state='ACTIVE'
            AND NOT EXISTS (
              SELECT 1 FROM blocked_signer_kids b
              WHERE b.issuer=? AND b.kid=?
            )
            AND NOT EXISTS (
              SELECT 1 FROM principal_epochs epoch
              WHERE epoch.principal_id=?
                AND ?<=epoch.reject_issued_at_or_before_epoch
            )
          FOR SHARE OF tenant, project, tm, pm, active
        )
        INSERT INTO aetheris_tasks (
          id,title,command_text,state,mode,created_at,updated_at,
          version,owner_identity_uid,owner_tenant_key,owner_project_key,
          owner_verified_at
        )
        SELECT ?, ?, ?, 'QUEUED', ?, clock_timestamp(), clock_timestamp(),
               0, a.identity_uid, a.tenant_key, a.project_key,
               clock_timestamp()
        FROM authorized a
        WHERE current_setting('transaction_isolation')='read committed'
          AND NOT pg_is_in_recovery()
        RETURNING id
        """;

    public UUID createSynthetic(Connection connection,
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal,
            String tenant, String project, String title, String command,
            OperationMode mode,
            RuntimePinnedLocalFenceLeaseVerifier.VerifiedLease lease,
            RuntimePinnedWorkloadIdentityVerifier.VerifiedWorkload workload)
            throws SQLException {
        Objects.requireNonNull(connection, "transaction connection");
        Objects.requireNonNull(principal, "verified Identity principal");
        if (connection.getAutoCommit() || connection.isReadOnly()
                || connection.getTransactionIsolation()!=Connection.TRANSACTION_READ_COMMITTED) {
            throw new IllegalStateException("Explicit writable READ COMMITTED transaction required");
        }
        if (!principal.hasScope("orchestrator:write") || lease==null
                || !lease.isBoundTo(workload)) {
            throw new SecurityException("Signed task creation authority denied");
        }
        var signed = principal.requireRevocationFacts();
        long issued = signed.signedIssuedAt().getEpochSecond();
        if (issued<=0 || signed.signedIdentityUid()<=0
                || !validSelector(tenant) || !validSelector(project)
                || !validText(title,160) || !validText(command,8000)
                || mode==null) {
            throw new IllegalArgumentException("Invalid scoped task request");
        }
        UUID id=UUID.randomUUID();
        try (PreparedStatement sql=connection.prepareStatement(INSERT)) {
            // The signed resource lease cannot be inferred from a user header.
            sql.setQueryTimeout(3);
            sql.setString(1,lease.endpoint());
            sql.setLong(2,lease.term());
            sql.setLong(3,signed.signedIdentityUid());
            sql.setString(4,tenant);
            sql.setString(5,project);
            sql.setString(6,signed.verifiedIssuer());
            sql.setString(7,signed.verifiedKeyId());
            sql.setString(8,signed.signedTokenId());
            sql.setLong(9,signed.signedIdentityUid());
            sql.setLong(10,issued);
            sql.setString(11,signed.verifiedIssuer());
            sql.setString(12,signed.verifiedKeyId());
            sql.setLong(13,signed.signedIdentityUid());
            sql.setLong(14,issued);
            sql.setObject(15,id);
            sql.setString(16,title);
            sql.setString(17,command);
            sql.setString(18,mode.name());
            try (ResultSet result=sql.executeQuery()) {
                if (!result.next()) throw new SecurityException(
                    "No active signed session, membership or resource fence");
                UUID persisted=result.getObject(1,UUID.class);
                if (!id.equals(persisted) || result.next()) {
                    throw new SQLException("Unexpected admission row count");
                }
            }
        }
        // Caller must abort the transaction if either signed claim expired
        // while SQL was executing. This cannot enforce post-commit fencing.
        if (!principal.isFresh() || !lease.isBoundTo(workload)) {
            throw new SecurityException("Signed creation authority expired");
        }
        return id;
    }

    private static boolean validSelector(String value) {
        return value!=null && value.matches(SELECTOR);
    }

    private static boolean validText(String value, int limit) {
        return value!=null && !value.isBlank() && value.length()<=limit
            && value.equals(value.strip())
            && value.chars().noneMatch(Character::isISOControl);
    }
}
