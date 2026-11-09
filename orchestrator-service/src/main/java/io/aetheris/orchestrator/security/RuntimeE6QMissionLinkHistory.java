package io.aetheris.orchestrator.security;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * S0-E6Q review-only Mission↔Task attachment and privacy-minimal event
 * headers. NOT a Spring bean, Task/Mission API, event-stream or migration.
 * Requires the independently pinned Runtime RS256 principal verifier and
 * separate function-only PostgreSQL writer/reader credentials.
 *
 * All object/session/tenant/project/workload authorization happens inside
 * the same PostgreSQL transaction as link insertion or event-header read.
 * Never trust MissionSessionEntity.taskIdsCsv as an access-control ledger.
 */
public final class RuntimeE6QMissionLinkHistory {
    private static final String LINK =
        "SELECT e6q_review.link_owned(?,?,?,?,?,?,?,?,?)";
    private static final String HEADERS =
        "SELECT task_id,event_at,event_state FROM " +
        "e6q_review.owned_event_headers(?,?,?,?,?,?,?,?,?)";
    private final DataSource restrictedWriter;
    private final DataSource restrictedReader;

    public record EventHeader(UUID taskId,Instant eventAt,String state) {}

    public RuntimeE6QMissionLinkHistory(
        DataSource restrictedWriter,DataSource restrictedReader) {
        this.restrictedWriter=Objects.requireNonNull(restrictedWriter);
        this.restrictedReader=Objects.requireNonNull(restrictedReader);
    }

    public boolean attach(
        RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal,
        UUID missionId,UUID taskId,String tenant,String project)
        throws SQLException {
        Objects.requireNonNull(principal,"verified signed principal");
        Objects.requireNonNull(missionId,"mission");
        Objects.requireNonNull(taskId,"task");
        if(!principal.isFresh() || !principal.hasScope("orchestrator:write")
           || !key(tenant) || !key(project)) return false;
        var signed=principal.requireRevocationFacts();
        try(Connection c=transaction(restrictedWriter)){
            try(PreparedStatement ps=c.prepareStatement(LINK)){
                ps.setQueryTimeout(3);
                signed(ps,signed);
                ps.setObject(6,missionId);
                ps.setObject(7,taskId);
                ps.setString(8,tenant);
                ps.setString(9,project);
                final boolean success;
                try(ResultSet rs=ps.executeQuery()){
                    if(!rs.next())throw new SQLException("Missing link verdict");
                    success=rs.getBoolean(1);
                    if(rs.wasNull() || rs.next())
                        throw new SQLException("Ambiguous link verdict");
                }
                if(!principal.isFresh()) {c.rollback();return false;}
                c.commit();
                return success;
            }catch(SQLException | RuntimeException ex){
                c.rollback();throw ex;
            }
        }
    }

    public List<EventHeader> headers(
        RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal,
        UUID missionId,String tenant,String project,int limit)
        throws SQLException {
        Objects.requireNonNull(principal,"verified signed principal");
        Objects.requireNonNull(missionId,"mission");
        if(!principal.isFresh() || !principal.hasScope("orchestrator:read")
           || !key(tenant) || !key(project) || limit<1 || limit>25)
            return List.of();
        var facts=principal.requireRevocationFacts();
        try(Connection c=transaction(restrictedReader)){
            try(PreparedStatement ps=c.prepareStatement(HEADERS)){
                ps.setQueryTimeout(3);
                signed(ps,facts);
                ps.setObject(6,missionId);
                ps.setString(7,tenant);
                ps.setString(8,project);
                ps.setInt(9,limit);
                var result=new ArrayList<EventHeader>();
                try(ResultSet rs=ps.executeQuery()){
                    while(rs.next()){
                        if(result.size()>=limit)
                            throw new SQLException("Unbounded event result");
                        UUID task=rs.getObject(1,UUID.class);
                        var when=rs.getTimestamp(2);
                        String state=rs.getString(3);
                        if(task==null || when==null || state==null)
                            throw new SQLException("Invalid event header");
                        result.add(new EventHeader(task,when.toInstant(),state));
                    }
                }
                if(!principal.isFresh()){c.rollback();return List.of();}
                c.commit();
                return List.copyOf(result);
            }catch(SQLException | RuntimeException ex){
                c.rollback();throw ex;
            }
        }
    }

    private static Connection transaction(DataSource source) throws SQLException {
        Connection c=source.getConnection();
        try{
            if(c.isReadOnly())throw new SQLException("Writable primary required");
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            if(c.isReadOnly() || c.getAutoCommit()
                    || c.getTransactionIsolation()!=Connection.TRANSACTION_READ_COMMITTED)
                throw new SQLException("READ COMMITTED primary required");
            return c;
        }catch(SQLException | RuntimeException ex){c.close();throw ex;}
    }
    private static void signed(PreparedStatement p,
         RuntimePinnedPrincipalVerifier.VerifiedRevocationFacts value)
         throws SQLException {
        p.setString(1,value.verifiedIssuer());
        p.setString(2,value.verifiedKeyId());
        p.setString(3,value.signedTokenId());
        p.setLong(4,value.signedIdentityUid());
        p.setLong(5,value.signedIssuedAt().getEpochSecond());
    }
    private static boolean key(String value) {
        return value!=null && value.matches("[A-Za-z0-9._-]{1,96}");
    }
}
