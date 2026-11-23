package io.aetheris.orchestrator.security;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * S0-E6O resource-data+authorization atomicity proof, NOT a Spring component
 * or an enabled Task/Mission API. This uses the disposable E6O review schema;
 * it must not be connected to customer data without a real migration and
 * approved per-object owner/tenant/workload authority.
 *
 * The database function checks independently verified Platform JTI session,
 * active signer+epoch, resource owner, live tenant/project membership and
 * workload fence before returning the resource label. All row locks stay held
 * until this transaction completes. A separate preflight-only boolean check
 * CANNOT replace this guarded data read.
 *
 * DataSource MUST authenticate with the restricted E6O object-reader LOGIN
 * (which can EXECUTE only read_bound_resource, not directly SELECT data).
 * No claims are taken from caller-controlled headers; only the cryptographic
 * verifier can construct VerifiedPrincipal.
 */
public final class RuntimeE6OScopedResourceRead {
    private static final String SQL = """
        SELECT e6o_review.read_bound_resource(?,?,?,?,?,?,?,?,?)
        """;
    public enum Kind { TASK, MISSION }

    private final DataSource restrictedPrimary;
    public RuntimeE6OScopedResourceRead(DataSource restrictedPrimary) {
        this.restrictedPrimary = Objects.requireNonNull(
            restrictedPrimary, "independent restricted primary DataSource");
    }

    public Optional<String> read(
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal verified,
            UUID resourceId, Kind kind, String tenant, String project)
            throws SQLException {
        Objects.requireNonNull(verified, "independently signed principal");
        Objects.requireNonNull(kind, "approved resource kind");
        Objects.requireNonNull(resourceId, "resource id");
        if(!verified.isFresh() || !verified.hasScope("orchestrator:read"))
            return Optional.empty();
        // Identifiers are not trusted for authority. Still enforce canonical
        // bounds before passing them to parameterized SQL + live membership.
        if(!key(tenant) || !key(project)) return Optional.empty();
        var signed=verified.requireRevocationFacts();
        try(Connection c=restrictedPrimary.getConnection()) {
            if(c.isReadOnly()) throw new SQLException("Primary required");
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            if(c.isReadOnly() || c.getAutoCommit()
                || c.getTransactionIsolation()!=Connection.TRANSACTION_READ_COMMITTED)
                throw new SQLException("Writable READ COMMITTED required");
            try(PreparedStatement p=c.prepareStatement(SQL)) {
                p.setQueryTimeout(3);
                p.setString(1,signed.verifiedIssuer());
                p.setString(2,signed.verifiedKeyId());
                p.setString(3,signed.signedTokenId());
                p.setLong(4,signed.signedIdentityUid());
                p.setLong(5,signed.signedIssuedAt().getEpochSecond());
                p.setObject(6,resourceId);
                p.setString(7,kind.name());
                p.setString(8,tenant);
                p.setString(9,project);
                final Optional<String> object;
                try(ResultSet rows=p.executeQuery()) {
                    if(!rows.next()) throw new SQLException("Missing read outcome");
                    String label=rows.getString(1);
                    if(rows.next()) throw new SQLException("Ambiguous read outcome");
                    object=Optional.ofNullable(label);
                }
                // Both authentication and resource SELECT happened under the
                // same transaction; no second unguarded resource read follows.
                if(!verified.isFresh()) {
                    c.rollback();
                    return Optional.empty();
                }
                c.commit();
                return object;
            } catch(SQLException | RuntimeException ex) {
                c.rollback();
                throw ex;
            }
        }
    }
    private static boolean key(String k) {
        return k!=null && k.length()>=1 && k.length()<=96
            && k.equals(k.strip())
            && k.chars().allMatch(ch->Character.isLetterOrDigit(ch)
                  || ch=='.'||ch=='_'||ch=='-');
    }
}
