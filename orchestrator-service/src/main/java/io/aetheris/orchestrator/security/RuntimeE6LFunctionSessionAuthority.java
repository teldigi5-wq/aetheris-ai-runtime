package io.aetheris.orchestrator.security;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * S0-E6L review candidate, based on the EXACT pinned Platform S0-E6J/E6K
 * contract (Platform PR #415). No Spring annotation, bean, servlet route
 * or production credentials are registered.
 *
 * Unlike the earlier RuntimePg16ReviewSessionAuthority's disposable direct
 * table join, this adapter needs ONLY EXECUTE on the separate Platform
 * e6j_runtime_auth.check_signed_session SECURITY DEFINER function.
 *
 * The DataSource MUST authenticate as a distinct least-privilege reader
 * principal; this class never SET ROLE, changes tables, accepts unsigned
 * request claims, caches an ALLOW, or silently falls back to prior fixtures.
 * Every request obtains a fresh primary PostgreSQL READ COMMITTED snapshot.
 *
 * IMPORTANT: this is preflight ONLY. After this short transaction commits,
 * session/signer/epoch changes can occur. Resource authorization must
 * atomically recheck the session and tenant/owner/workload fence in its
 * own SQL resource operation. Do not register any production endpoint here.
 */
public final class RuntimeE6LFunctionSessionAuthority
        implements RuntimeSignedIdentityHttpFilter.SessionAuthority {
    private static final String CHECK = """
        SELECT e6j_runtime_auth.check_signed_session(?,?,?,?,?)
        """;
    private final DataSource primaryReader;

    public RuntimeE6LFunctionSessionAuthority(DataSource primaryReader) {
        this.primaryReader=Objects.requireNonNull(primaryReader,
            "independent function-only PostgreSQL reader DataSource");
    }

    @Override
    public boolean isActive(
        RuntimePinnedPrincipalVerifier.VerifiedRevocationFacts signed)
        throws SQLException {
        Objects.requireNonNull(signed, "cryptographically verified token facts");
        if(signed.signedIdentityUid()<=0
                || signed.signedIssuedAt().getEpochSecond()<=0) return false;
        try(Connection connection=primaryReader.getConnection()) {
            if(connection.isReadOnly()) {
                throw new SQLException("Refuse read-only transaction for row lock");
            }
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            if(connection.getAutoCommit() || connection.isReadOnly()
                    || connection.getTransactionIsolation()
                        != Connection.TRANSACTION_READ_COMMITTED) {
                throw new SQLException("Writable READ COMMITTED required");
            }
            try(PreparedStatement stmt=connection.prepareStatement(CHECK)) {
                stmt.setQueryTimeout(3);
                stmt.setString(1,signed.verifiedIssuer());
                stmt.setString(2,signed.verifiedKeyId());
                stmt.setString(3,signed.signedTokenId());
                stmt.setLong(4,signed.signedIdentityUid());
                stmt.setLong(5,signed.signedIssuedAt().getEpochSecond());
                final boolean active;
                try(ResultSet rows=stmt.executeQuery()) {
                    if(!rows.next()) throw new SQLException("Missing authority verdict");
                    active=rows.getBoolean(1);
                    if(rows.wasNull() || rows.next())
                        throw new SQLException("Ambiguous authority verdict");
                }
                connection.commit();
                return active;
            }catch(SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }
}
