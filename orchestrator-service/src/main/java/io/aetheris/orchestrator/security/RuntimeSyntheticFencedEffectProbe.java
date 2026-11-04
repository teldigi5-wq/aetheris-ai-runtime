package io.aetheris.orchestrator.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * REVIEW-ONLY transactional resource-effect proof, never deployed admission.
 *
 * Uses disposable SQL fixture tables, an unprivileged-in-production UNKNOWN
 * writer role and synthetic signed Identity, workload and fence authorities.
 * Unlike a check-then-act Boolean, the authorization predicate, resource-fence
 * row lock, idempotent effect insertion and result live in ONE SQL statement.
 *
 * This is not an authenticated workload transport, cross-node resource fence,
 * real scheduler/task mutation, mTLS identity, or HTTP/SSE/mission route.
 * It cannot prove leadership on a disconnected writable old primary. The
 * caller MUST hold and finish its explicit JDBC transaction, with rollback
 * on any SQLException. A successful return means a pending transaction-local
 * row, not a durable committed side effect.
 */
public final class RuntimeSyntheticFencedEffectProbe {
    private static final String SELECTOR = "[a-z0-9][a-z0-9_-]{0,95}";
    private static final String ATOMIC_EFFECT = """
        WITH locked_fence AS MATERIALIZED (
            SELECT fence.term
            FROM b62_local_resource_fence fence
            WHERE fence.resource_key='runtime'
              AND fence.endpoint=? AND fence.term=?
              AND fence.state='ACTIVE'
              AND fence.expires_at>clock_timestamp()
              AND current_setting('transaction_isolation')='read committed'
              AND NOT pg_is_in_recovery()
            FOR SHARE OF fence
        ),
        scoped AS MATERIALIZED (
            SELECT t.id AS task_id, fence.term AS fence_term
            FROM locked_fence fence
            CROSS JOIN aetheris_tasks t
            JOIN tenant_memberships tm
              ON tm.tenant_key=t.owner_tenant_key
             AND tm.identity_uid=t.owner_identity_uid
            JOIN project_memberships pm
              ON pm.tenant_key=t.owner_tenant_key
             AND pm.project_key=t.owner_project_key
             AND pm.identity_uid=t.owner_identity_uid
            JOIN synthetic_session_registration active
              ON active.uid=t.owner_identity_uid
            WHERE t.id=? AND t.owner_identity_uid=?
              AND t.owner_tenant_key=? AND t.owner_project_key=?
              AND t.owner_verified_at IS NOT NULL
              AND tm.status='ACTIVE' AND tm.revoked_at IS NULL
              AND (tm.valid_until IS NULL OR tm.valid_until>clock_timestamp())
              AND pm.status='ACTIVE' AND pm.revoked_at IS NULL
              AND (pm.valid_until IS NULL OR pm.valid_until>clock_timestamp())
              AND active.issuer=? AND active.kid=? AND active.jti=?
              AND active.uid=? AND active.iat_seconds=?
              AND active.state='ACTIVE'
              AND NOT EXISTS (
                  SELECT 1 FROM blocked_signer_kids blocked
                  WHERE blocked.issuer=? AND blocked.kid=?)
              AND NOT EXISTS (
                  SELECT 1 FROM principal_epochs epoch
                  WHERE epoch.principal_id=?
                    AND ?<=epoch.reject_issued_at_or_before_epoch)
        ),
        inserted AS (
            INSERT INTO synthetic_guarded_task_effects
                (effect_id,task_id,fence_term,workload_subject)
            SELECT ?, scoped.task_id, scoped.fence_term, ?
            FROM scoped WHERE true
            ON CONFLICT (effect_id) DO NOTHING
            RETURNING effect_id
        )
        SELECT EXISTS (SELECT 1 FROM inserted)
        """;

    /**
     * Explicit, writable READ COMMITTED transaction is mandatory. An already
     * used effect ID is denied (not a second mutation). SQL exceptions require
     * the caller to roll back; they never grant an effect.
     */
    public boolean recordEffectSynthetic(Connection connection,
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal,
            String tenant, String project, UUID taskId, UUID effectId,
            RuntimePinnedLocalFenceLeaseVerifier.VerifiedLease lease,
            RuntimePinnedWorkloadIdentityVerifier.VerifiedWorkload workload)
            throws SQLException {
        if (connection == null || principal == null || taskId == null
                || effectId == null || lease == null || workload == null
                || !valid(tenant) || !valid(project)
                || !principal.hasScope("orchestrator:read")
                || !lease.isBoundTo(workload)) return false;
        if (connection.getAutoCommit() || connection.isReadOnly()) return false;
        final RuntimePinnedPrincipalVerifier.VerifiedRevocationFacts signed;
        try {
            signed = principal.requireRevocationFacts();
        } catch (RuntimeException invalid) {
            return false;
        }
        long iat = signed.signedIssuedAt().getEpochSecond();
        if (iat <= 0 || signed.signedIdentityUid() <= 0) return false;

        try (PreparedStatement stmt = connection.prepareStatement(ATOMIC_EFFECT)) {
            stmt.setQueryTimeout(2); // CI-only synchronous budget
            stmt.setString(1, lease.endpoint());
            stmt.setLong(2, lease.term());
            stmt.setObject(3, taskId);
            stmt.setLong(4, signed.signedIdentityUid());
            stmt.setString(5, tenant);
            stmt.setString(6, project);
            stmt.setString(7, signed.verifiedIssuer());
            stmt.setString(8, signed.verifiedKeyId());
            stmt.setString(9, signed.signedTokenId());
            stmt.setLong(10, signed.signedIdentityUid());
            stmt.setLong(11, iat);
            stmt.setString(12, signed.verifiedIssuer());
            stmt.setString(13, signed.verifiedKeyId());
            stmt.setLong(14, signed.signedIdentityUid());
            stmt.setLong(15, iat);
            stmt.setObject(16, effectId);
            stmt.setString(17, workload.subject());
            try (ResultSet result = stmt.executeQuery()) {
                // No separate authorization SELECT: the INSERT is conditional.
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private static boolean valid(String value) {
        return value != null && value.matches(SELECTOR);
    }
}
