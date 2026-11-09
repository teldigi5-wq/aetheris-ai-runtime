package io.aetheris.orchestrator.security;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * PostgreSQL-backed HTTP session preflight against the EXISTING DISPOSABLE
 * Security #23 review schema. No production registry schema or writer has
 * been approved. This class is deliberately NOT a Spring component/bean
 * and MUST NOT be registered on product HTTP routes.
 *
 * The statement requires all three independent authoritative rows at once:
 * exact signed-session enrollment, active signer, and principal epoch.
 * FOR SHARE serializes concurrent UPDATE/DELETE of those rows until this
 * short-lived transaction commits. No token-derived identity header, local
 * cache or replica may act as the authority.
 *
 * IMPORTANT: a successful preflight does not hold locks through the HTTP
 * handler. Every real task/mission read/write must atomically reauthorize
 * session, signer, epoch, tenant/project membership, owner and workload
 * fence in its own SQL statement. Never treat this boolean as an object
 * permission, durable capability or a proof against split-brain.
 */
public final class RuntimePg16ReviewSessionAuthority
        implements RuntimeSignedIdentityHttpFilter.SessionAuthority {

    private static final String CHECK = """
        SELECT 1
          FROM synthetic_session_registration s
          JOIN signer_key_states k
            ON k.issuer=s.issuer AND k.kid=s.kid
          JOIN principal_epochs e ON e.principal_id=s.uid
         WHERE s.issuer=? AND s.kid=? AND s.jti=?
           AND s.uid=? AND s.iat_seconds=?
           AND s.state='ACTIVE'
           AND k.state='ACTIVE'
           AND ? > e.reject_issued_at_or_before_epoch
           AND NOT pg_is_in_recovery()
           AND current_setting('transaction_read_only')='off'
         FOR SHARE OF s,k,e
        """;

    private final DataSource source;

    public RuntimePg16ReviewSessionAuthority(DataSource source) {
        this.source=Objects.requireNonNull(source, "trusted primary DataSource");
    }

    @Override
    public boolean isActive(
            RuntimePinnedPrincipalVerifier.VerifiedRevocationFacts signed)
            throws SQLException {
        Objects.requireNonNull(signed, "cryptographically verified session facts");
        if(signed.signedIdentityUid()<=0 ||
                signed.signedIssuedAt().getEpochSecond()<=0) {
            return false;
        }
        // Never trust a local/replica-cached verdict. A new connection and
        // a fresh READ COMMITTED snapshot are required for every request.
        try(Connection connection=source.getConnection()) {
            if(connection.isReadOnly()) {
                throw new SQLException("Refusing read-only session authority");
            }
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            if(connection.getAutoCommit() || connection.isReadOnly()
                    || connection.getTransactionIsolation() !=
                        Connection.TRANSACTION_READ_COMMITTED) {
                throw new SQLException("Writable READ COMMITTED authority required");
            }
            try(PreparedStatement stmt=connection.prepareStatement(CHECK)) {
                stmt.setQueryTimeout(3);
                stmt.setString(1,signed.verifiedIssuer());
                stmt.setString(2,signed.verifiedKeyId());
                stmt.setString(3,signed.signedTokenId());
                stmt.setLong(4,signed.signedIdentityUid());
                stmt.setLong(5,signed.signedIssuedAt().getEpochSecond());
                stmt.setLong(6,signed.signedIssuedAt().getEpochSecond());
                final boolean active;
                try(ResultSet rows=stmt.executeQuery()) {
                    active=rows.next();
                    if(active && rows.next()) {
                        throw new SQLException("Ambiguous session authority");
                    }
                }
                connection.commit();
                return active;
            } catch(SQLException | RuntimeException rejected) {
                connection.rollback();
                throw rejected;
            }
        }
    }
}
