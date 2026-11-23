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
 * S0-E6P strictly opt-in actual-table-shape PostgreSQL16 persistence review.
 * Unregistered, no @Component/@Bean and NO customer Task/Mission controller.
 *
 * Creates only NEW owned Task/Mission rows with exact signed session/UID and
 * active tenant+project membership and an operator-bound workload fence in
 * ONE database transaction. Never updates a legacy row's owner binding.
 * The SQL function is trusted ONLY after independent RS256 verification by
 * RuntimePinnedPrincipalVerifier and a real function-only DB writer LOGIN.
 *
 * This candidate is NOT enabled in TaskService/MissionService. It requires
 * reviewed migrations for the actual deployment, DB client TLS custody,
 * stronger operation-specific workload attestation and ownerless policy.
 */
public final class RuntimeE6POwnedPersistenceWriter {
    public enum Kind { TASK, MISSION }
    private static final String CREATE = """
        SELECT e6p_review.create_owned(?,?,?,?,?,?,?,?,?,?,?,?)
        """;
    private static final String READ = """
        SELECT e6p_review.read_owned(?,?,?,?,?,?,?,?,?)
        """;
    private final DataSource scopedWriter;
    private final DataSource scopedReader;

    public RuntimeE6POwnedPersistenceWriter(DataSource scopedWriter,
                                            DataSource scopedReader) {
        this.scopedWriter=Objects.requireNonNull(scopedWriter,"function-only writer");
        this.scopedReader=Objects.requireNonNull(scopedReader,"function-only reader");
    }

    public boolean create(RuntimePinnedPrincipalVerifier.VerifiedPrincipal signed,
                          Kind kind,UUID newId,String tenant,String project,
                          UUID trustedFence,String title,String description)
            throws SQLException {
        Objects.requireNonNull(signed,"independently signed principal");
        Objects.requireNonNull(kind,"Task/Mission kind");
        Objects.requireNonNull(newId,"new object UUID");
        Objects.requireNonNull(trustedFence,"trusted reviewed workload fence");
        if(!signed.isFresh() || !signed.hasScope("orchestrator:write")
            || !key(tenant) || !key(project) || !text(title,240)
            || !text(description,8000)) return false;
        var jti=signed.requireRevocationFacts();
        return transact(scopedWriter,CREATE,p->{
            signature(p,jti);
            p.setObject(6,newId);p.setString(7,kind.name());
            p.setString(8,tenant);p.setString(9,project);
            p.setObject(10,trustedFence);p.setString(11,title);
            p.setString(12,description);
        },true).isPresent();
    }

    public Optional<String> read(
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal signed,
            Kind kind,UUID objectId,String tenant,String project)
            throws SQLException {
        Objects.requireNonNull(signed,"independently signed principal");
        Objects.requireNonNull(kind,"Task/Mission kind");
        Objects.requireNonNull(objectId,"object UUID");
        if(!signed.isFresh() || !signed.hasScope("orchestrator:read")
            || !key(tenant) || !key(project)) return Optional.empty();
        var jti=signed.requireRevocationFacts();
        return transact(scopedReader,READ,p->{
            signature(p,jti);
            p.setObject(6,objectId);p.setString(7,kind.name());
            p.setString(8,tenant);p.setString(9,project);
        },false);
    }

    private static void signature(PreparedStatement p,
            RuntimePinnedPrincipalVerifier.VerifiedRevocationFacts signed)
            throws SQLException {
        p.setString(1,signed.verifiedIssuer());
        p.setString(2,signed.verifiedKeyId());
        p.setString(3,signed.signedTokenId());
        p.setLong(4,signed.signedIdentityUid());
        p.setLong(5,signed.signedIssuedAt().getEpochSecond());
    }
    @FunctionalInterface private interface Bind {
        void bind(PreparedStatement p) throws SQLException;
    }
    private static Optional<String> transact(DataSource source,String sql,
            Bind binder,boolean write) throws SQLException {
        try(Connection c=source.getConnection()){
            if(c.isReadOnly()) throw new SQLException("Writable authority primary required");
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            if(c.isReadOnly() || c.getAutoCommit()
                || c.getTransactionIsolation()!=Connection.TRANSACTION_READ_COMMITTED)
                throw new SQLException("READ COMMITTED transaction required");
            try(PreparedStatement p=c.prepareStatement(sql)){
                p.setQueryTimeout(3);binder.bind(p);
                Optional<String> result;
                try(ResultSet rows=p.executeQuery()){
                    if(!rows.next())throw new SQLException("Missing function verdict");
                    if(write) {
                        boolean accepted=rows.getBoolean(1);
                        if(rows.wasNull())throw new SQLException("NULL write verdict");
                        result=accepted?Optional.of("CREATED"):Optional.empty();
                    }else {
                        result=Optional.ofNullable(rows.getString(1));
                    }
                    if(rows.next())throw new SQLException("Ambiguous function result");
                }
                c.commit();
                return result;
            }catch(SQLException | RuntimeException fail) {
                c.rollback();throw fail;
            }
        }
    }
    private static boolean key(String s) {
        return s!=null && s.matches("[A-Za-z0-9._-]{1,96}");
    }
    private static boolean text(String s,int max) {
        return s!=null && !s.isBlank() && s.length()<=max;
    }
}
