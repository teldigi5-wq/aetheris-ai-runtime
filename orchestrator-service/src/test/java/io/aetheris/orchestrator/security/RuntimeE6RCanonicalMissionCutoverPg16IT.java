package io.aetheris.orchestrator.security;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S0-E6R disposable PG16 canonical CSV quarantine and safe link cutover.
 * No application Task/Mission/SSE controller, migration, live account or
 * Gateway service identity is activated. All tests run with real restricted
 * PostgreSQL LOGINs and real signed session enrollments from Platform #415.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RuntimeE6RCanonicalMissionCutoverPg16IT {
    private static final String DB=
        "jdbc:postgresql://127.0.0.1:5432/aetheris_e6j_disposable";
    private static final String ISSUER="https://identity.aetheris.example.invalid";
    private static final String KID="e6l-platform-real-issuer";
    private static final String AUDIENCE="aetheris-orchestrator-api";
    private static final UUID FENCE_A=
        UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID FENCE_B=
        UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID TASK_A=
        UUID.fromString("d1d1d1d1-1111-4111-8111-111111111111");
    private static final UUID TASK_B=
        UUID.fromString("e1e1e1e1-1111-4111-8111-111111111111");
    private static final UUID MISSION_A=
        UUID.fromString("d2d2d2d2-2222-4222-8222-222222222222");
    private static final UUID MISSION_B=
        UUID.fromString("e2e2e2e2-2222-4222-8222-222222222222");
    private static final UUID LEGACY_TASK=
        UUID.fromString("aaaaaaaa-1111-4111-8111-111111111111");
    private static final UUID LEGACY_MISSION=
        UUID.fromString("bbbbbbbb-2222-4222-8222-222222222222");
    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal a,b;
    private static RuntimePinnedPrincipalVerifier verifier;
    private static RuntimeE6QMissionLinkHistory links;
    private static RuntimeE6RCanonicalMissionCutover cutover;
    private static final UUID DIRTY=UUID.fromString("cccccccc-1111-4111-8111-111111111111");
    private static final UUID POISON=UUID.fromString("cccccccc-2222-4222-8222-222222222222");
    private static final UUID RACE=UUID.fromString("cccccccc-3333-4333-8333-333333333333");
    private static UUID initialRequest;

    private static RuntimeE6POwnedPersistenceWriter creator;

    private static Path fixtures(){
        String value=System.getenv("AETHERIS_E6L_FIXTURE_DIR");
        if(value==null)throw new IllegalStateException("No ephemeral Platform fixtures");
        return Path.of(value);
    }
    private static String file(String name){
        try{return Files.readString(fixtures().resolve(name),
                    StandardCharsets.UTF_8).trim();}
        catch(Exception ex){throw new IllegalStateException("Missing signed fixture");}
    }
    private static DataSource dbRole(String role,String passwordEnv){
        var ds=new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(DB);
        ds.setUsername(role);
        ds.setPassword(System.getenv(passwordEnv));
        return ds;
    }
    private static Connection admin() throws Exception {
        return DriverManager.getConnection(DB,"e6j_test_only",
            System.getenv("PGPASSWORD"));
    }
    private static int count() throws Exception {
        try(var c=admin();var s=c.createStatement();
            var rs=s.executeQuery("SELECT count(*) FROM e6q_review.mission_task_links")){
            assertTrue(rs.next());return rs.getInt(1);
        }
    }

    @BeforeAll
    static void realPlatformAndDisposableFixture() throws Exception {
        assertEquals("true",System.getenv("GITHUB_ACTIONS"));
        assertEquals("ONLY_DISPOSABLE_E6Q_PG16",System.getenv("AETHERIS_E6Q_MUST_RUN"));
        assertEquals("ONLY_DISPOSABLE_E6R_PG16",System.getenv("AETHERIS_E6R_MUST_RUN"));
        assertNotNull(System.getenv("AETHERIS_E6R_OPERATOR_PASSWORD"));
        assertEquals("127.0.0.1",System.getenv("PGHOST"));
        assertEquals("e6j_test_only",System.getenv("PGUSER"));
        assertEquals("aetheris_e6j_disposable",System.getenv("PGDATABASE"));
        assertNotNull(System.getenv("AETHERIS_E6P_WRITER_PASSWORD"));
        assertNotNull(System.getenv("AETHERIS_E6P_READER_PASSWORD"));
        assertNotNull(System.getenv("AETHERIS_E6L_REVOKER_PASSWORD"));
        verifier=new RuntimePinnedPrincipalVerifier(
            file("e6l-platform-public.pem"),KID,ISSUER,AUDIENCE,Clock.systemUTC());
        a=verifier.verifyBearerForRevocation("Bearer "+file("user-a.jwt"));
        b=verifier.verifyBearerForRevocation("Bearer "+file("user-b.jwt"));
        assertTrue(a.hasScope("orchestrator:read"));
        assertTrue(a.hasScope("orchestrator:write"));
        assertTrue(b.hasScope("orchestrator:read"));
        assertFalse(b.hasScope("orchestrator:write"));
        assertNotEquals(a.identityUid(),b.identityUid());
        assertEquals(Long.parseLong(file("expected-uid-a")),a.identityUid());
        assertEquals(Long.parseLong(file("expected-uid-b")),b.identityUid());
        var write=dbRole("e6p_writer_login","AETHERIS_E6P_WRITER_PASSWORD");
        var read=dbRole("e6p_reader_login","AETHERIS_E6P_READER_PASSWORD");
        links=new RuntimeE6QMissionLinkHistory(write,read);
        cutover=new RuntimeE6RCanonicalMissionCutover(
            dbRole("e6r_operator_login","AETHERIS_E6R_OPERATOR_PASSWORD"),read);
        creator=new RuntimeE6POwnedPersistenceWriter(write,read);

        // Fixture admin is test runner only. No production user can register
        // memberships, provision fences or synthesize another owner's data.
        try(var c=admin()){
            c.setAutoCommit(false);
            try(var p=c.prepareStatement("""
                 INSERT INTO e6o_review.resource_memberships
                   (uid,tenant_key,project_key,membership_epoch,active)
                 VALUES(?,?,?,0,true)
                 """)){
                p.setLong(1,a.identityUid());p.setString(2,"tenant-a");
                p.setString(3,"project-a");assertEquals(1,p.executeUpdate());
                p.setLong(1,b.identityUid());p.setString(2,"tenant-b");
                p.setString(3,"project-b");assertEquals(1,p.executeUpdate());
            }
            try(var s=c.createStatement()){
                s.execute("INSERT INTO e6o_review.workload_fences VALUES "+
                     "('"+FENCE_A+"',true,1),('"+FENCE_B+"',true,1)");
                s.execute("INSERT INTO e6p_review.fence_bindings VALUES "+
                     "('"+FENCE_A+"','tenant-a','project-a'),"+
                     "('"+FENCE_B+"','tenant-b','project-b')");
            }
            c.commit();
        }
        assertTrue(creator.create(a,RuntimeE6POwnedPersistenceWriter.Kind.TASK,
            TASK_A,"tenant-a","project-a",FENCE_A,
            "review owned task A","synthetic command"));
        assertTrue(creator.create(a,RuntimeE6POwnedPersistenceWriter.Kind.MISSION,
            MISSION_A,"tenant-a","project-a",FENCE_A,
            "review owned mission A","synthetic objective"));

        // Admin-created B objects test cross-owner joins without making the
        // actual Platform API_CONSUMER JWT a writer.
        try(var c=admin()){
            c.setAutoCommit(false);
            try(var p=c.prepareStatement("""
                INSERT INTO public.aetheris_tasks
                (id,title,command_text,state,mode,created_at,updated_at,version,
                 owner_identity_uid,owner_tenant_key,owner_project_key,
                 owner_verified_at,owner_workload_fence_id)
                VALUES (?,'B private task','B private command','QUEUED','BALANCED',
                       now(),now(),0,?,'tenant-b','project-b',now(),?)
                """)){
                p.setObject(1,TASK_B);p.setLong(2,b.identityUid());
                p.setObject(3,FENCE_B);assertEquals(1,p.executeUpdate());
            }
            try(var p=c.prepareStatement("""
                INSERT INTO public.aetheris_mission_sessions
                (id,title,objective,status,task_ids_csv,created_at,updated_at,
                 version,owner_identity_uid,owner_tenant_key,owner_project_key,
                 owner_verified_at,owner_workload_fence_id)
                VALUES (?,'B private mission','B private objective','ACTIVE','',
                        now(),now(),0,?,'tenant-b','project-b',now(),?)
                """)){
                p.setObject(1,MISSION_B);p.setLong(2,b.identityUid());
                p.setObject(3,FENCE_B);assertEquals(1,p.executeUpdate());
            }
            try(var p=c.prepareStatement("""
                INSERT INTO public.aetheris_task_events
                  (id,task_id,event_at,state,agent_id,message,metadata_json)
                VALUES (?,?,clock_timestamp(),?,'private-agent',?,?)
                """)){
                p.setObject(1,UUID.fromString("f1f1f1f1-1111-4111-8111-111111111111"));
                p.setObject(2,TASK_A);p.setString(3,"QUEUED");
                p.setString(4,"SECRET_A_MESSAGE_DO_NOT_EXPORT");
                p.setString(5,"{\"secret\":\"A_METADATA_DO_NOT_EXPORT\"}");
                assertEquals(1,p.executeUpdate());
                p.setObject(1,UUID.fromString("f2f2f2f2-2222-4222-8222-222222222222"));
                p.setObject(2,TASK_A);p.setString(3,"PLANNING");
                p.setString(4,"SECRET_A_MESSAGE_TWO");
                p.setString(5,"{\"token\":\"FORBIDDEN_RAW_META\"}");
                assertEquals(1,p.executeUpdate());
                p.setObject(1,UUID.fromString("f3f3f3f3-3333-4333-8333-333333333333"));
                p.setObject(2,TASK_B);p.setString(3,"QUEUED");
                p.setString(4,"SECRET_B_NEVER_EXPORT");
                p.setString(5,"{\"crossTenant\":true}");
                assertEquals(1,p.executeUpdate());
            }
            c.commit();
        }
    }


    private static int auditCount() throws Exception {
        try(var c=admin();var s=c.createStatement();
            var rs=s.executeQuery("SELECT count(*) FROM e6r_review.cutover_audit")){
            assertTrue(rs.next());return rs.getInt(1);
        }
    }
    private static String csv(UUID mission) throws Exception {
        try(var c=admin();var p=c.prepareStatement(
                "SELECT task_ids_csv FROM public.aetheris_mission_sessions WHERE id=?")){
            p.setObject(1,mission);
            try(var rs=p.executeQuery()){
                assertTrue(rs.next());return rs.getString(1);
            }
        }
    }
    private static void setCsv(UUID mission,String value) throws Exception {
        try(var c=admin();var p=c.prepareStatement(
                "UPDATE public.aetheris_mission_sessions SET task_ids_csv=? WHERE id=?")){
            p.setString(1,value);p.setObject(2,mission);
            assertEquals(1,p.executeUpdate());
        }
    }
    private static void newMission(UUID id,String title) throws Exception {
        assertTrue(creator.create(a,RuntimeE6POwnedPersistenceWriter.Kind.MISSION,
            id,"tenant-a","project-a",FENCE_A,title,"synthetic objective"));
    }
    private static void deniedCsvWrite(UUID id) throws Exception {
        try(var c=admin();var p=c.prepareStatement(
                "UPDATE public.aetheris_mission_sessions SET task_ids_csv=? WHERE id=?")){
            p.setString(1,TASK_A.toString());p.setObject(2,id);
            var ex=assertThrows(SQLException.class,p::executeUpdate);
            assertEquals("42501",ex.getSQLState());
        }
    }

    @Test @Order(1)
    void legacyOwnerlessCsvIsQuarantinedNeverAutoLinkedOrBackfilled()
            throws Exception {
        setCsv(LEGACY_MISSION,LEGACY_TASK.toString()+","+TASK_A);
        initialRequest=UUID.randomUUID();
        assertEquals("QUARANTINED",
            cutover.review(LEGACY_MISSION,initialRequest,true));
        assertEquals(0,count());
        assertEquals(LEGACY_TASK+","+TASK_A,csv(LEGACY_MISSION));
        assertTrue(cutover.authorizedTaskIds(a,LEGACY_MISSION,
            "tenant-a","project-a",10).isEmpty());
        deniedCsvWrite(LEGACY_MISSION);
        assertEquals(1,auditCount());
    }

    @Test @Order(2)
    void unverifiedOwnedCsvAndMismatchedLinkAreBothQuarantined()
            throws Exception {
        newMission(DIRTY,"dirty pre-cutover csv");
        setCsv(DIRTY,TASK_B.toString());
        assertEquals("QUARANTINED",
            cutover.review(DIRTY,UUID.randomUUID(),true));
        assertTrue(cutover.authorizedTaskIds(a,DIRTY,
            "tenant-a","project-a",10).isEmpty());
        assertEquals(TASK_B.toString(),csv(DIRTY));
        newMission(POISON,"poisoned prior link");
        // The fixture administrator intentionally injects a malicious
        // cross-owner link that the restricted E6Q writer cannot create.
        try(var c=admin();var p=c.prepareStatement("""
              INSERT INTO e6q_review.mission_task_links(
              mission_id,task_id,linked_by_uid) VALUES(?,?,?)
              """)){
            p.setObject(1,POISON);p.setObject(2,TASK_B);
            p.setLong(3,a.identityUid());assertEquals(1,p.executeUpdate());
        }
        assertEquals("QUARANTINED",
            cutover.review(POISON,UUID.randomUUID(),true));
        assertTrue(cutover.authorizedTaskIds(a,POISON,
            "tenant-a","project-a",10).isEmpty());
        assertEquals(3,auditCount());
    }

    @Test @Order(3)
    void canonicalTasksRemainEmptyUntilExplicitScopedOperatorPromotion()
            throws Exception {
        assertTrue(links.attach(a,MISSION_A,TASK_A,"tenant-a","project-a"));
        assertTrue(cutover.authorizedTaskIds(a,MISSION_A,
            "tenant-a","project-a",25).isEmpty());
        assertEquals("",csv(MISSION_A));
        var request=UUID.randomUUID();
        assertEquals("CANONICAL",cutover.review(MISSION_A,request,true));
        assertEquals(java.util.List.of(TASK_A),
            cutover.authorizedTaskIds(a,MISSION_A,"tenant-a","project-a",25));
        assertEquals("",csv(MISSION_A));
        assertEquals(4,auditCount());
    }

    @Test @Order(4)
    void operatorAndReaderLoginAreSeparatedFromRawResourceTables()
            throws Exception {
        try(var c=DriverManager.getConnection(DB,"e6r_operator_login",
                System.getenv("AETHERIS_E6R_OPERATOR_PASSWORD"))){
            var ex=assertThrows(SQLException.class,()->
                c.createStatement().executeQuery(
                    "SELECT * FROM public.aetheris_mission_sessions"));
            assertEquals("42501",ex.getSQLState());
        }
        try(var c=DriverManager.getConnection(DB,"e6p_reader_login",
                System.getenv("AETHERIS_E6P_READER_PASSWORD"))){
            var ex=assertThrows(SQLException.class,()->
                c.createStatement().execute(
                    "SELECT e6r_review.operator_decide(gen_random_uuid(),gen_random_uuid(),true)"));
            assertEquals("42501",ex.getSQLState());
        }
        var unsigned=verifier.verifyBearerForRevocation(
             "Bearer "+file("unregistered.jwt"));
        assertTrue(cutover.authorizedTaskIds(unsigned,MISSION_A,
            "tenant-a","project-a",25).isEmpty());
        assertTrue(cutover.authorizedTaskIds(b,MISSION_A,
            "tenant-a","project-a",25).isEmpty());
        assertTrue(cutover.authorizedTaskIds(a,MISSION_A,
            "tenant-b","project-b",25).isEmpty());
        assertTrue(cutover.authorizedTaskIds(a,MISSION_A,
            "tenant-a","project-a",26).isEmpty());
        assertEquals(4,auditCount());
    }

    @Test @Order(5)
    void replayOrLegacyCsvMutationCannotBypassCanonicalDecision()
            throws Exception {
        assertNull(cutover.review(MISSION_A,initialRequest,true));
        assertNull(cutover.review(MISSION_A,UUID.randomUUID(),true));
        deniedCsvWrite(MISSION_A);
        assertEquals(java.util.List.of(TASK_A),
            cutover.authorizedTaskIds(a,MISSION_A,"tenant-a","project-a",10));
        assertEquals(4,auditCount());
    }

    @Test @Order(6)
    void concurrentLegacyCsvWriteCannotSlipPastOperatorPromotion()
            throws Exception {
        newMission(RACE,"cutover lock race");
        assertTrue(links.attach(a,RACE,TASK_A,"tenant-a","project-a"));
        try(var c=admin()){
            c.setAutoCommit(false);
            try(var p=c.prepareStatement(
                 "UPDATE public.aetheris_mission_sessions SET task_ids_csv=? WHERE id=?")){
                p.setString(1,TASK_B.toString());p.setObject(2,RACE);
                assertEquals(1,p.executeUpdate());
            }
            assertThrows(SQLException.class,()->
                cutover.review(RACE,UUID.randomUUID(),true));
            c.rollback();
        }
        assertEquals("",csv(RACE));
        assertEquals("CANONICAL",
            cutover.review(RACE,UUID.randomUUID(),true));
        assertEquals(java.util.List.of(TASK_A),
            cutover.authorizedTaskIds(a,RACE,"tenant-a","project-a",25));
        assertEquals(5,auditCount());
    }

    @Test @Order(7)
    void rollbackFreezesCanonicalReadsNeverReenablesLegacyCsv()
            throws Exception {
        assertEquals("FROZEN",cutover.review(RACE,UUID.randomUUID(),false));
        assertTrue(cutover.authorizedTaskIds(a,RACE,
            "tenant-a","project-a",25).isEmpty());
        deniedCsvWrite(RACE);
        assertNull(cutover.review(RACE,UUID.randomUUID(),true));
        // Links remain stored; reverting the app cannot treat CSV or links
        // as authorized without another independently reviewed release.
        try(var c=admin();var p=c.prepareStatement(
                "SELECT count(*) FROM e6q_review.mission_task_links WHERE mission_id=?")){
            p.setObject(1,RACE);
            try(var rs=p.executeQuery()){
                assertTrue(rs.next());assertEquals(1,rs.getInt(1));
            }
        }
        assertEquals(6,auditCount());
    }

    @Test @Order(8)
    void liveMembershipRevocationBlocksCanonicalReadAndCannotLeakStaleIds()
            throws Exception {
        try(var c=admin()){
            c.setAutoCommit(false);
            try(var p=c.prepareStatement("""
                UPDATE e6o_review.resource_memberships
                   SET active=false,membership_epoch=membership_epoch+1
                 WHERE uid=? AND tenant_key='tenant-a' AND project_key='project-a'
                """)){
                p.setLong(1,a.identityUid());assertEquals(1,p.executeUpdate());
            }
            assertThrows(SQLException.class,()->
                cutover.authorizedTaskIds(a,MISSION_A,
                    "tenant-a","project-a",10));
            c.rollback();
        }
        assertEquals(java.util.List.of(TASK_A),
            cutover.authorizedTaskIds(a,MISSION_A,"tenant-a","project-a",10));
    }

    @Test @Order(9)
    void revokedPlatformSessionStopsCanonicalReadDespitePersistedLinks()
            throws Exception {
        var claims=a.requireRevocationFacts();
        try(var c=DriverManager.getConnection(DB,"e6l_revoker_login",
               System.getenv("AETHERIS_E6L_REVOKER_PASSWORD"))){
            c.setAutoCommit(false);
            try(var p=c.prepareStatement(
                 "SELECT e6j_runtime_auth.revoke_signed_session(?,?,?,?)")){
                p.setString(1,claims.verifiedIssuer());
                p.setString(2,claims.verifiedKeyId());
                p.setString(3,claims.signedTokenId());
                p.setObject(4,UUID.randomUUID());
                try(var rs=p.executeQuery()){
                    assertTrue(rs.next());assertTrue(rs.getBoolean(1));
                }
            }
            c.commit();
        }
        assertTrue(cutover.authorizedTaskIds(a,MISSION_A,
            "tenant-a","project-a",10).isEmpty());
        assertEquals(6,auditCount());
    }
}
