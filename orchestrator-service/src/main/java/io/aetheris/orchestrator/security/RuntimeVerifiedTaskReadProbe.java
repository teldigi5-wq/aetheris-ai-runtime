package io.aetheris.orchestrator.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * REVIEW-ONLY transactional read proof for Runtime Security #23.
 *
 * Not a Spring bean, controller, repository replacement, deployment migration,
 * or user-facing grant. The registries used by disposable PG16 CI are not
 * certified production authorities. The caller owns the READ COMMITTED
 * transaction and must commit/roll back after consuming results; in particular
 * it must roll back on ANY exception. Never stream data after releasing its
 * admission snapshot without an independently reviewed expiry/revoke policy.
 *
 * The signed UID comes only from the pinned RS256 verifier. The requested
 * tenant/project are merely narrowing selectors, NOT supplied authority.
 * No raw commands, event messages, or event metadata are returned.
 */
public final class RuntimeVerifiedTaskReadProbe {
    private static final String SELECTOR = "[a-z0-9][a-z0-9_-]{0,95}";

    public record TaskSummary(UUID id, String title, String state,
                              String mode, Instant updatedAt) {}
    public record EventHeader(UUID eventId, UUID taskId,
                              Instant eventAt, String state) {}

    // Lock every authority row within the SAME statement as the task/event
    // selector. A missing, revoked or unregistered authority yields no rows.
    // A fenced, writable primary is required even for this read proof:
    // READ COMMITTED + row locks cannot certify stale or split-brain replicas.
    private static final String AUTHORITY = """
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
          JOIN signer_key_states signer
            ON signer.issuer=active.issuer AND signer.kid=active.kid
          JOIN principal_epochs epoch
            ON epoch.principal_id=tm.identity_uid
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
            AND signer.state='ACTIVE'
            AND active.iat_seconds>epoch.reject_issued_at_or_before_epoch
          FOR SHARE OF tenant, project, tm, pm, active, signer, epoch
        )
        """;

    private static final String TASK_COLUMNS = """
        SELECT t.id,t.title,t.state,t.mode,t.updated_at
        FROM aetheris_tasks t
        JOIN authorized a
          ON t.owner_identity_uid=a.identity_uid
         AND t.owner_tenant_key=a.tenant_key
         AND t.owner_project_key=a.project_key
        WHERE t.owner_verified_at IS NOT NULL
          AND t.owner_verified_at<=clock_timestamp()
          AND current_setting('transaction_isolation')='read committed'
          AND NOT pg_is_in_recovery()
        """;

    private static final String ONE = AUTHORITY + TASK_COLUMNS + " AND t.id=?";
    private static final String RECENT = AUTHORITY + TASK_COLUMNS
        + " ORDER BY t.updated_at DESC,t.id DESC LIMIT ?";

    private static final String HISTORY = AUTHORITY + """
        SELECT e.id,e.task_id,e.event_at,e.state
        FROM aetheris_task_events e
        JOIN aetheris_tasks t ON t.id=e.task_id
        JOIN authorized a
          ON t.owner_identity_uid=a.identity_uid
         AND t.owner_tenant_key=a.tenant_key
         AND t.owner_project_key=a.project_key
        WHERE t.id=?
          AND t.owner_verified_at IS NOT NULL
          AND t.owner_verified_at<=clock_timestamp()
          AND current_setting('transaction_isolation')='read committed'
          AND NOT pg_is_in_recovery()
          AND (?=FALSE OR (e.event_at,e.id) <
                  (CAST(? AS timestamptz),CAST(? AS uuid)))
        ORDER BY e.event_at DESC,e.id DESC LIMIT ?
        """;

    public Optional<TaskSummary> findOne(Connection connection,
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal,
            String tenant, String project, UUID taskId,
            RuntimePinnedLocalFenceLeaseVerifier.VerifiedLease lease,
            RuntimePinnedWorkloadIdentityVerifier.VerifiedWorkload workload)
            throws SQLException {
        var signed=authorize(connection,principal,tenant,project,lease,workload);
        Objects.requireNonNull(taskId,"task ID");
        try(PreparedStatement q=connection.prepareStatement(ONE)) {
            q.setQueryTimeout(3);
            bindAuthority(q,signed,tenant,project,lease);
            q.setObject(11,taskId);
            try(ResultSet rows=q.executeQuery()) {
                Optional<TaskSummary> result=rows.next()
                    ? Optional.of(taskSummary(rows)) : Optional.empty();
                if(rows.next()) throw new SQLException("Unexpected duplicate task");
                assertStillFresh(principal,lease,workload);
                return result;
            }
        }
    }

    public List<TaskSummary> recent(Connection connection,
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal,
            String tenant, String project, int limit,
            RuntimePinnedLocalFenceLeaseVerifier.VerifiedLease lease,
            RuntimePinnedWorkloadIdentityVerifier.VerifiedWorkload workload)
            throws SQLException {
        var signed=authorize(connection,principal,tenant,project,lease,workload);
        if(limit<1 || limit>50) throw new IllegalArgumentException("Invalid task page size");
        try(PreparedStatement q=connection.prepareStatement(RECENT)) {
            q.setQueryTimeout(3);
            bindAuthority(q,signed,tenant,project,lease);
            q.setInt(11,limit);
            List<TaskSummary> tasks=new ArrayList<>();
            try(ResultSet rows=q.executeQuery()) {
                while(rows.next()) tasks.add(taskSummary(rows));
            }
            assertStillFresh(principal,lease,workload);
            return List.copyOf(tasks);
        }
    }

    /** Stable descending event_at/id keyset; no message or metadata payload. */
    public List<EventHeader> history(Connection connection,
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal,
            String tenant, String project, UUID taskId,
            Instant beforeAt, UUID beforeEventId, int limit,
            RuntimePinnedLocalFenceLeaseVerifier.VerifiedLease lease,
            RuntimePinnedWorkloadIdentityVerifier.VerifiedWorkload workload)
            throws SQLException {
        var signed=authorize(connection,principal,tenant,project,lease,workload);
        Objects.requireNonNull(taskId,"task ID");
        if((beforeAt==null)!=(beforeEventId==null) || limit<1 || limit>100)
            throw new IllegalArgumentException("Invalid history cursor or page size");
        try(PreparedStatement q=connection.prepareStatement(HISTORY)) {
            q.setQueryTimeout(3);
            bindAuthority(q,signed,tenant,project,lease);
            q.setObject(11,taskId);
            q.setBoolean(12,beforeAt!=null);
            if(beforeAt==null) q.setNull(13,Types.TIMESTAMP_WITH_TIMEZONE);
            else q.setTimestamp(13,Timestamp.from(beforeAt));
            if(beforeEventId==null) q.setNull(14,Types.OTHER);
            else q.setObject(14,beforeEventId);
            q.setInt(15,limit);
            List<EventHeader> events=new ArrayList<>();
            try(ResultSet rows=q.executeQuery()) {
                while(rows.next()) events.add(new EventHeader(
                    rows.getObject(1,UUID.class),rows.getObject(2,UUID.class),
                    rows.getTimestamp(3).toInstant(),rows.getString(4)));
            }
            assertStillFresh(principal,lease,workload);
            return List.copyOf(events);
        }
    }

    private static RuntimePinnedPrincipalVerifier.VerifiedRevocationFacts authorize(
            Connection connection,
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal,
            String tenant, String project,
            RuntimePinnedLocalFenceLeaseVerifier.VerifiedLease lease,
            RuntimePinnedWorkloadIdentityVerifier.VerifiedWorkload workload)
            throws SQLException {
        Objects.requireNonNull(connection,"transaction connection");
        Objects.requireNonNull(principal,"signed Identity principal");
        if(connection.getAutoCommit() || connection.isReadOnly()
                || connection.getTransactionIsolation()!=Connection.TRANSACTION_READ_COMMITTED)
            throw new IllegalStateException("Explicit writable READ COMMITTED transaction required");
        if(!principal.hasScope("orchestrator:read") || lease==null
                || !lease.isBoundTo(workload))
            throw new SecurityException("Signed task-read authority denied");
        if(tenant==null || !tenant.matches(SELECTOR)
                || project==null || !project.matches(SELECTOR))
            throw new IllegalArgumentException("Invalid scoped selectors");
        var signed=principal.requireRevocationFacts();
        if(signed.signedIdentityUid()<=0
                || signed.signedIssuedAt().getEpochSecond()<=0)
            throw new SecurityException("Invalid signed task-read provenance");
        return signed;
    }

    private static void bindAuthority(PreparedStatement q,
            RuntimePinnedPrincipalVerifier.VerifiedRevocationFacts signed,
            String tenant,String project,
            RuntimePinnedLocalFenceLeaseVerifier.VerifiedLease lease)
            throws SQLException {
        q.setString(1,lease.endpoint());q.setLong(2,lease.term());
        q.setLong(3,signed.signedIdentityUid());
        q.setString(4,tenant);q.setString(5,project);
        q.setString(6,signed.verifiedIssuer());
        q.setString(7,signed.verifiedKeyId());
        q.setString(8,signed.signedTokenId());
        q.setLong(9,signed.signedIdentityUid());
        q.setLong(10,signed.signedIssuedAt().getEpochSecond());
    }

    private static TaskSummary taskSummary(ResultSet row) throws SQLException {
        return new TaskSummary(row.getObject(1,UUID.class),row.getString(2),
            row.getString(3),row.getString(4),row.getTimestamp(5).toInstant());
    }

    private static void assertStillFresh(
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal,
            RuntimePinnedLocalFenceLeaseVerifier.VerifiedLease lease,
            RuntimePinnedWorkloadIdentityVerifier.VerifiedWorkload workload) {
        if(!principal.isFresh() || !lease.isBoundTo(workload))
            throw new SecurityException("Read authority expired during transaction");
    }
}
