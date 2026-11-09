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
 * S0-E6S: an opt-in, application-facing Mission link/history contract.
 *
 * NOT a Spring component, repository replacement, HTTP route, migration,
 * SSE broadcaster or authorization for the existing ownerless MissionService.
 * No operator credential is held here: an independently approved operator
 * must have promoted a Mission via the separate E6R review boundary.
 *
 * These three operations use only canonical functions. The older E6Q
 * direct link/history function grants are revoked from the scoped logins.
 * Signed JTI, cutover mode, owner, tenant/project and workload fence are
 * revalidated by SQL in the SAME transaction returning data / adding links.
 * No fallback to MissionSessionEntity.taskIdsCsv is ever permitted.
 */
public final class RuntimeE6SCanonicalMissionApplication {
    private static final String ATTACH =
        "SELECT e6s_review.canonical_attach(?,?,?,?,?,?,?,?,?)";
    private static final String TASK_IDS =
        "SELECT task_id FROM e6r_review.canonical_task_ids(?,?,?,?,?,?,?,?,?)";
    private static final String HEADERS =
        "SELECT task_id,event_at,event_state FROM " +
        "e6s_review.canonical_event_headers(?,?,?,?,?,?,?,?,?)";

    public record EventHeader(UUID taskId,Instant eventAt,String state) {}
    private final DataSource writer;
    private final DataSource reader;

    public RuntimeE6SCanonicalMissionApplication(
            DataSource restrictedWriter,DataSource restrictedReader) {
        this.writer=Objects.requireNonNull(restrictedWriter,"restricted SQL writer");
        this.reader=Objects.requireNonNull(restrictedReader,"restricted SQL reader");
    }

    public boolean attach(RuntimePinnedPrincipalVerifier.VerifiedPrincipal verified,
                          UUID mission,UUID task,String tenant,String project)
            throws SQLException {
        Objects.requireNonNull(verified,"independently signed principal");
        Objects.requireNonNull(mission,"mission");
        Objects.requireNonNull(task,"task");
        if(!verified.isFresh()||!verified.hasScope("orchestrator:write")
          ||!key(tenant)||!key(project))return false;
        var facts=verified.requireRevocationFacts();
        try(Connection c=transaction(writer)){
            try(PreparedStatement p=c.prepareStatement(ATTACH)){
                p.setQueryTimeout(3);
                signature(p,facts);
                p.setObject(6,mission);p.setObject(7,task);
                p.setString(8,tenant);p.setString(9,project);
                boolean accepted;
                try(ResultSet rows=p.executeQuery()){
                    if(!rows.next())throw new SQLException("Missing link verdict");
                    accepted=rows.getBoolean(1);
                    if(rows.wasNull()||rows.next())
                        throw new SQLException("Invalid link verdict");
                }
                if(!verified.isFresh()){c.rollback();return false;}
                c.commit();
                return accepted;
            }catch(SQLException|RuntimeException ex){
                c.rollback();throw ex;
            }
        }
    }

    public List<UUID> taskIds(
           RuntimePinnedPrincipalVerifier.VerifiedPrincipal verified,
           UUID mission,String tenant,String project,int limit)
           throws SQLException {
        Objects.requireNonNull(verified,"independently signed principal");
        Objects.requireNonNull(mission,"mission");
        if(!verified.isFresh()||!verified.hasScope("orchestrator:read")
           ||!key(tenant)||!key(project)||limit<1||limit>25)
            return List.of();
        var facts=verified.requireRevocationFacts();
        try(Connection c=transaction(reader)){
            try(PreparedStatement p=c.prepareStatement(TASK_IDS)){
                p.setQueryTimeout(3);
                signature(p,facts);
                p.setObject(6,mission);p.setString(7,tenant);
                p.setString(8,project);p.setInt(9,limit);
                var tasks=new ArrayList<UUID>();
                try(ResultSet rows=p.executeQuery()){
                    while(rows.next()){
                        if(tasks.size()>=limit)throw new SQLException("Unbounded tasks");
                        UUID id=rows.getObject(1,UUID.class);
                        if(id==null)throw new SQLException("NULL task");
                        tasks.add(id);
                    }
                }
                if(!verified.isFresh()){c.rollback();return List.of();}
                c.commit();
                return List.copyOf(tasks);
            }catch(SQLException|RuntimeException ex){
                c.rollback();throw ex;
            }
        }
    }

    public List<EventHeader> eventHeaders(
           RuntimePinnedPrincipalVerifier.VerifiedPrincipal verified,
           UUID mission,String tenant,String project,int limit)
           throws SQLException {
        Objects.requireNonNull(verified,"independently signed principal");
        Objects.requireNonNull(mission,"mission");
        if(!verified.isFresh()||!verified.hasScope("orchestrator:read")
           ||!key(tenant)||!key(project)||limit<1||limit>25)
            return List.of();
        var facts=verified.requireRevocationFacts();
        try(Connection c=transaction(reader)){
            try(PreparedStatement p=c.prepareStatement(HEADERS)){
                p.setQueryTimeout(3);
                signature(p,facts);
                p.setObject(6,mission);p.setString(7,tenant);
                p.setString(8,project);p.setInt(9,limit);
                var events=new ArrayList<EventHeader>();
                try(ResultSet rows=p.executeQuery()){
                    while(rows.next()){
                        if(events.size()>=limit)
                            throw new SQLException("Unbounded event headers");
                        UUID task=rows.getObject(1,UUID.class);
                        var at=rows.getTimestamp(2);
                        String state=rows.getString(3);
                        if(task==null||at==null||state==null)
                            throw new SQLException("Incomplete event header");
                        events.add(new EventHeader(task,at.toInstant(),state));
                    }
                }
                if(!verified.isFresh()){c.rollback();return List.of();}
                c.commit();
                return List.copyOf(events);
            }catch(SQLException|RuntimeException ex){
                c.rollback();throw ex;
            }
        }
    }

    private static void signature(PreparedStatement p,
            RuntimePinnedPrincipalVerifier.VerifiedRevocationFacts facts)
            throws SQLException {
        p.setString(1,facts.verifiedIssuer());
        p.setString(2,facts.verifiedKeyId());
        p.setString(3,facts.signedTokenId());
        p.setLong(4,facts.signedIdentityUid());
        p.setLong(5,facts.signedIssuedAt().getEpochSecond());
    }

    private static Connection transaction(DataSource source) throws SQLException{
        Connection c=source.getConnection();
        try{
            if(c.isReadOnly())throw new SQLException("Writable authority primary required");
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            if(c.isReadOnly()||c.getAutoCommit()
                ||c.getTransactionIsolation()!=Connection.TRANSACTION_READ_COMMITTED)
                throw new SQLException("READ COMMITTED primary required");
            return c;
        }catch(SQLException|RuntimeException ex){c.close();throw ex;}
    }
    private static boolean key(String v){
        return v!=null&&v.matches("[A-Za-z0-9._-]{1,96}");
    }
}
