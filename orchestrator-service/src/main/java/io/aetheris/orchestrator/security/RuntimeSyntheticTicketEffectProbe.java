package io.aetheris.orchestrator.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * REVIEW-ONLY candidate: a SQL function mediates all effect-table DML.
 * The separate worker login MUST have EXECUTE on that function only, never
 * INSERT/UPDATE/DELETE on the effect table or issuance tickets.
 *
 * An independently trusted issuer must first create an unpredictable,
 * single-use ticket bound to the exact verified principal, task, scopes,
 * resource endpoint, term, effect ID and worker DB session_user.
 * The database cross-checks the ticket and current authorization facts.
 * Caller parameters or SET LOCAL variables are never a source of authority.
 *
 * This is NOT deployable: ticket issuance/authentication, storage-fencing
 * and privileged database-administrator rollback are not certified.
 */
public final class RuntimeSyntheticTicketEffectProbe {
    private static final String SELECTOR = "[a-z0-9][a-z0-9_-]{0,95}";
    private static final String CALL =
            "SELECT b62_record_ticket_effect(?,?,?,?,?,?,?,?,?,?,?,?)";

    public boolean recordEffectSynthetic(Connection connection,
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal,
            String tenant, String project, UUID taskId, UUID effectId,
            RuntimePinnedLocalFenceLeaseVerifier.VerifiedLease lease,
            RuntimePinnedWorkloadIdentityVerifier.VerifiedWorkload workload)
            throws SQLException {
        if (connection == null || principal == null || taskId == null || effectId == null
                || lease == null || workload == null
                || !valid(tenant) || !valid(project)
                || !principal.hasScope("orchestrator:read")
                || !principal.hasScope("orchestrator:write")
                || !lease.isBoundTo(workload)) return false;
        if (connection.getAutoCommit() || connection.isReadOnly()) return false;
        final RuntimePinnedPrincipalVerifier.VerifiedRevocationFacts signed;
        try {
            signed = principal.requireRevocationFacts();
        } catch (RuntimeException invalid) {
            return false;
        }
        if (signed.signedIdentityUid() <= 0 ||
                signed.signedIssuedAt().getEpochSecond() <= 0) return false;
        try (PreparedStatement statement = connection.prepareStatement(CALL)) {
            statement.setQueryTimeout(2);
            statement.setString(1, lease.endpoint());
            statement.setLong(2, lease.term());
            statement.setObject(3, taskId);
            statement.setLong(4, signed.signedIdentityUid());
            statement.setString(5, tenant);
            statement.setString(6, project);
            statement.setString(7, signed.verifiedIssuer());
            statement.setString(8, signed.verifiedKeyId());
            statement.setString(9, signed.signedTokenId());
            statement.setLong(10, signed.signedIssuedAt().getEpochSecond());
            statement.setObject(11, effectId);
            statement.setString(12, workload.subject());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private static boolean valid(String value) {
        return value != null && value.matches(SELECTOR);
    }
}
