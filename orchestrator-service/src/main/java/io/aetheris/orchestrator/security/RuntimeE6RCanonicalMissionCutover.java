package io.aetheris.orchestrator.security;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Review-only canonical Mission link cutover. Not a Spring bean, operator API,
 * production migration, authenticated user-controller or CSV compatibility
 * bridge. Operator calls require a DIFFERENT function-only SQL LOGIN that
 * can perform review decisions but cannot inspect raw Mission or link tables.
 *
 * A reviewed CANONICAL state is not an authorization decision by itself:
 * each read rechecks the original independently verified Platform RS256 JTI,
 * live tenant/project membership, Mission and Task owner and workload fence
 * inside the single data-returning PostgreSQL transaction.
 */
public final class RuntimeE6RCanonicalMissionCutover {
    private static final String DECIDE =
        "SELECT e6r_review.operator_decide(?,?,?)";
    private static final String IDS =
        "SELECT task_id FROM e6r_review.canonical_task_ids(?,?,?,?,?,?,?,?,?)";
    private final DataSource restrictedOperator;
    private final DataSource restrictedReader;

    public RuntimeE6RCanonicalMissionCutover(
            DataSource restrictedOperator,DataSource restrictedReader) {
        this.restrictedOperator=Objects.requireNonNull(restrictedOperator);
        this.restrictedReader=Objects.requireNonNull(restrictedReader);
    }

    /**
     * External, authenticated approval and immutable audit are NOT supplied
     * by this preview. Passing a UUID is not production operator authorization.
     * A distinct SQL certificate/role boundary must be deployed and reviewed.
     */
    public String review(UUID mission,UUID requestId,boolean wantCanonical)
            throws SQLException {
        Objects.requireNonNull(mission,"Mission");
        Objects.requireNonNull(requestId,"Synthetic review request");
        try(Connection c=transaction(restrictedOperator)) {
            try(PreparedStatement p=c.prepareStatement(DECIDE)) {
                p.setQueryTimeout(3);
                p.setObject(1,mission);
                p.setObject(2,requestId);
                p.setBoolean(3,wantCanonical);
                String decision;
                try(ResultSet rows=p.executeQuery()) {
                    if(!rows.next())throw new SQLException("Missing cutover verdict");
                    decision=rows.getString(1);
                    if(rows.next())throw new SQLException("Ambiguous cutover verdict");
                }
                if(decision!=null && !decision.equals("CANONICAL")
                    && !decision.equals("QUARANTINED")
                    && !decision.equals("FROZEN"))
                    throw new SQLException("Unknown cutover state");
                c.commit();
                return decision;
            }catch(SQLException | RuntimeException ex){
                c.rollback();throw ex;
            }
        }
    }

    public List<UUID> authorizedTaskIds(
           RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal,
           UUID mission,String tenant,String project,int max)
           throws SQLException {
        Objects.requireNonNull(principal,"Independently signed principal");
        Objects.requireNonNull(mission,"Mission");
        if(!principal.isFresh() || !principal.hasScope("orchestrator:read")
          || !key(tenant) || !key(project) || max<1 || max>25)
            return List.of();
        var verified=principal.requireRevocationFacts();
        try(Connection c=transaction(restrictedReader)) {
            try(PreparedStatement p=c.prepareStatement(IDS)) {
                p.setQueryTimeout(3);
                p.setString(1,verified.verifiedIssuer());
                p.setString(2,verified.verifiedKeyId());
                p.setString(3,verified.signedTokenId());
                p.setLong(4,verified.signedIdentityUid());
                p.setLong(5,verified.signedIssuedAt().getEpochSecond());
                p.setObject(6,mission);
                p.setString(7,tenant);
                p.setString(8,project);
                p.setInt(9,max);
                var out=new ArrayList<UUID>();
                try(ResultSet rows=p.executeQuery()) {
                    while(rows.next()) {
                        if(out.size()>=max)
                            throw new SQLException("Unbounded canonical read");
                        UUID task=rows.getObject(1,UUID.class);
                        if(task==null)throw new SQLException("Null Task link");
                        out.add(task);
                    }
                }
                if(!principal.isFresh()) {c.rollback();return List.of();}
                c.commit();
                return List.copyOf(out);
            }catch(SQLException | RuntimeException ex){
                c.rollback();throw ex;
            }
        }
    }
    private static Connection transaction(DataSource source) throws SQLException {
        Connection c=source.getConnection();
        try {
            if(c.isReadOnly())throw new SQLException("Writable authority primary required");
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            if(c.isReadOnly()||c.getAutoCommit()||
               c.getTransactionIsolation()!=Connection.TRANSACTION_READ_COMMITTED)
                throw new SQLException("READ COMMITTED authority primary required");
            return c;
        }catch(SQLException | RuntimeException ex) {c.close();throw ex;}
    }
    private static boolean key(String v) {
        return v!=null && v.matches("[A-Za-z0-9._-]{1,96}");
    }
}
