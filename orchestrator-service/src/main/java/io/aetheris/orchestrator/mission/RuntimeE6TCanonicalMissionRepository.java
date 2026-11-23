package io.aetheris.orchestrator.mission;

import io.aetheris.orchestrator.security.RuntimePinnedPrincipalVerifier;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * S0-E6T prototype of a read-only, owner-bound Mission repository.
 *
 * Not an @Entity, @Repository, @Component or existing JPA repository change.
 * It projects only real MissionSessionEntity column SHAPES from a disposable
 * PostgreSQL schema after the SAME-TRANSACTION signed-session, tenant,
 * project, Mission owner, CANONICAL mode, membership and workload checks.
 * It NEVER calls MissionSessionRepository.findById/getTaskIds(), parses the
 * legacy CSV, or exposes the Mission objective through an unsafe JPA read.
 *
 * An independently authenticated function-only SQL reader must be supplied.
 * No production migrations, Spring registration, HTTP controllers or SSE.
 */
public final class RuntimeE6TCanonicalMissionRepository {
    private static final String SQL = """
        SELECT mission_id,mission_title,mission_status,
               mission_created_at,mission_updated_at,mission_version
        FROM e6t_review.canonical_mission_projection(?,?,?,?,?,?,?,?)
        """;
    public record MissionProjection(
            UUID id,String title,MissionStatus status,
            Instant createdAt,Instant updatedAt,long version) {}

    private final DataSource scopedReader;

    public RuntimeE6TCanonicalMissionRepository(DataSource scopedReader) {
        this.scopedReader=Objects.requireNonNull(scopedReader,"function-only reader");
    }

    public Optional<MissionProjection> findAuthorized(
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal,
            UUID missionId,String tenant,String project) throws SQLException {
        Objects.requireNonNull(principal,"verified signed principal");
        Objects.requireNonNull(missionId,"mission ID");
        if(!principal.isFresh()||!principal.hasScope("orchestrator:read")
            ||!key(tenant)||!key(project))return Optional.empty();
        var signed=principal.requireRevocationFacts();

        try(Connection c=scopedReader.getConnection()){
            if(c.isReadOnly())throw new SQLException("Writable primary required");
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            if(c.isReadOnly()||c.getAutoCommit()
                ||c.getTransactionIsolation()!=Connection.TRANSACTION_READ_COMMITTED)
                throw new SQLException("READ COMMITTED primary required");
            try(PreparedStatement p=c.prepareStatement(SQL)){
                p.setQueryTimeout(3);
                p.setString(1,signed.verifiedIssuer());
                p.setString(2,signed.verifiedKeyId());
                p.setString(3,signed.signedTokenId());
                p.setLong(4,signed.signedIdentityUid());
                p.setLong(5,signed.signedIssuedAt().getEpochSecond());
                p.setObject(6,missionId);
                p.setString(7,tenant);
                p.setString(8,project);

                Optional<MissionProjection> result;
                try(ResultSet rows=p.executeQuery()){
                    if(!rows.next()){
                        result=Optional.empty();
                    }else{
                        UUID id=rows.getObject(1,UUID.class);
                        String title=rows.getString(2);
                        String status=rows.getString(3);
                        var created=rows.getTimestamp(4);
                        var updated=rows.getTimestamp(5);
                        long version=rows.getLong(6);
                        if(id==null||!id.equals(missionId)||title==null||
                            title.isBlank()||status==null||created==null||
                            updated==null||version<0||rows.wasNull())
                            throw new SQLException("Invalid canonical projection");
                        MissionStatus state;
                        try{
                            state=MissionStatus.valueOf(status);
                        }catch(IllegalArgumentException invalid){
                            throw new SQLException("Unknown Mission status",invalid);
                        }
                        result=Optional.of(new MissionProjection(
                            id,title,state,created.toInstant(),updated.toInstant(),
                            version));
                        if(rows.next())
                            throw new SQLException("Ambiguous canonical mission");
                    }
                }
                if(!principal.isFresh()){c.rollback();return Optional.empty();}
                c.commit();
                return result;
            }catch(SQLException|RuntimeException ex){
                c.rollback();
                throw ex;
            }
        }
    }
    private static boolean key(String value){
        return value!=null&&value.matches("[A-Za-z0-9._-]{1,96}");
    }
}
