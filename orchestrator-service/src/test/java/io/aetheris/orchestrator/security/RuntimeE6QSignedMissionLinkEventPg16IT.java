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
 * S0-E6Q disposable PG16 + actual source-pinned Platform-issued RS256/JTI.
 * No application Task/Mission/SSE controller, migration, live account or
 * Gateway service identity is activated. All tests run with real restricted
 * PostgreSQL LOGINs and real signed session enrollments from Platform #415.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RuntimeE6QSignedMissionLinkEventPg16IT {
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

    @Test @Order(1)
    void exactPlatformSignedOwnerMayLinkMatchingTaskToMissionOnce() throws Exception {
        assertEquals(0,count());
        assertTrue(links.attach(a,MISSION_A,TASK_A,"tenant-a","project-a"));
        assertEquals(1,count());
        assertTrue(links.attach(a,MISSION_A,TASK_A,"tenant-a","project-a"));
        assertEquals(1,count());
        try(var c=admin();var ps=c.prepareStatement(
            "SELECT task_ids_csv FROM public.aetheris_mission_sessions WHERE id=?")){
            ps.setObject(1,MISSION_A);
            try(var rs=ps.executeQuery()){
                assertTrue(rs.next());
                assertEquals("",rs.getString(1),
                    "Untrusted legacy CSV was not made a permission ledger");
            }
        }
    }

    @Test @Order(2)
    void wrongOwnerTenantProjectAndOwnerlessRowsNeverLink() throws Exception {
        int before=count();
        assertFalse(links.attach(a,MISSION_A,TASK_B,"tenant-a","project-a"));
        assertFalse(links.attach(a,MISSION_B,TASK_A,"tenant-a","project-a"));
        assertFalse(links.attach(a,MISSION_A,TASK_A,"tenant-b","project-b"));
        assertFalse(links.attach(a,MISSION_A,TASK_A,"tenant-a","wrong-project"));
        assertFalse(links.attach(a,LEGACY_MISSION,TASK_A,"tenant-a","project-a"));
        assertFalse(links.attach(a,MISSION_A,LEGACY_TASK,"tenant-a","project-a"));
        assertFalse(links.attach(a,LEGACY_MISSION,LEGACY_TASK,"tenant-a","project-a"));
        assertEquals(before,count());
    }

    @Test @Order(3)
    void readOnlyPrincipalAndUnregisteredSignedJtiCannotAttach() throws Exception {
        int before=count();
        assertFalse(links.attach(b,MISSION_B,TASK_B,"tenant-b","project-b"));
        var unenrolled=verifier.verifyBearerForRevocation(
             "Bearer "+file("unregistered.jwt"));
        assertFalse(links.attach(unenrolled,MISSION_A,TASK_A,
             "tenant-a","project-a"));
        assertThrows(RuntimeException.class,()->
             verifier.verifyBearerForRevocation("Bearer "+file("legacy-hs.jwt")));
        assertEquals(before,count());
    }

    @Test @Order(4)
    void sqlWriterAndReaderCannotDirectlyInspectLinksOrRawEventPayload()
            throws Exception {
        for(String[] role:new String[][]{
            {"e6p_writer_login","AETHERIS_E6P_WRITER_PASSWORD"},
            {"e6p_reader_login","AETHERIS_E6P_READER_PASSWORD"}}){
            for(String table:new String[]{"e6q_review.mission_task_links",
                "public.aetheris_task_events"}){
                try(var c=DriverManager.getConnection(DB,role[0],
                         System.getenv(role[1]))){
                    SQLException rejected=assertThrows(SQLException.class,
                        ()->c.createStatement().executeQuery(
                            "SELECT * FROM "+table));
                    assertEquals("42501",rejected.getSQLState());
                }
            }
        }
        try(var c=DriverManager.getConnection(
                DB,"e6p_reader_login",System.getenv("AETHERIS_E6P_READER_PASSWORD"))){
            SQLException reject=assertThrows(SQLException.class,()->
                c.createStatement().execute(
                    "SELECT e6q_review.link_owned('a','b','c',1,1,"+
                    "gen_random_uuid(),gen_random_uuid(),'x','y')"));
            assertEquals("42501",reject.getSQLState());
        }
    }

    @Test @Order(5)
    void ownerHistoryReturnsOnlyBoundedEventHeadersNoMessagesOrMetadata()
            throws Exception {
        var headers=links.headers(a,MISSION_A,"tenant-a","project-a",25);
        assertEquals(2,headers.size());
        assertEquals(TASK_A,headers.get(0).taskId());
        assertEquals(TASK_A,headers.get(1).taskId());
        assertNotNull(headers.get(0).eventAt());
        assertTrue(java.util.Set.of("QUEUED","PLANNING").contains(headers.get(0).state()));
        assertEquals(1,links.headers(a,MISSION_A,"tenant-a","project-a",1).size());
        assertTrue(links.headers(a,MISSION_A,"tenant-a","project-a",26).isEmpty());
        assertTrue(links.headers(a,MISSION_A,"tenant-a","project-b",25).isEmpty());
        assertTrue(links.headers(b,MISSION_A,"tenant-a","project-a",25).isEmpty());
        assertTrue(links.headers(a,LEGACY_MISSION,"tenant-a","project-a",25).isEmpty());
        String output=headers.toString();
        for(String forbidden:new String[]{"SECRET_","private-agent","metadata",
            "FORBIDDEN_RAW_META","B private","historic-secret","synthetic command"}){
            assertFalse(output.contains(forbidden),forbidden);
        }
    }

    @Test @Order(6)
    void inactiveFenceOrClosedMissionDeniesNewLinkAndEventHistory()
            throws Exception {
        try(var c=admin();var s=c.createStatement()){
            s.execute("UPDATE e6o_review.workload_fences SET active=false "+
                "WHERE fence_id='"+FENCE_A+"'");
        }
        try{
            assertFalse(links.attach(a,MISSION_A,TASK_A,"tenant-a","project-a"));
            assertTrue(links.headers(a,MISSION_A,"tenant-a","project-a",10).isEmpty());
        }finally{
            try(var c=admin();var s=c.createStatement()){
                s.execute("UPDATE e6o_review.workload_fences SET active=true "+
                    "WHERE fence_id='"+FENCE_A+"'");
            }
        }
        try(var c=admin();var s=c.createStatement()){
            s.execute("UPDATE public.aetheris_mission_sessions "+
                "SET status='COMPLETED' WHERE id='"+MISSION_A+"'");
        }
        try{
            assertFalse(links.attach(a,MISSION_A,TASK_A,"tenant-a","project-a"));
        }finally{
            try(var c=admin();var s=c.createStatement()){
                s.execute("UPDATE public.aetheris_mission_sessions "+
                    "SET status='ACTIVE' WHERE id='"+MISSION_A+"'");
            }
        }
        assertEquals(2,links.headers(a,MISSION_A,"tenant-a","project-a",10).size());
    }

    @Test @Order(7)
    void uncommittedMembershipRevocationCannotExposeStaleEventRows()
            throws Exception {
        try(var c=admin()){
            c.setAutoCommit(false);
            try(var p=c.prepareStatement("""
                UPDATE e6o_review.resource_memberships
                   SET active=false,membership_epoch=membership_epoch+1
                 WHERE uid=? AND tenant_key='tenant-a' AND project_key='project-a'
                """)){
                p.setLong(1,a.identityUid());
                assertEquals(1,p.executeUpdate());
            }
            // Real separate JDBC connection: bounded 3s timeout must fail
            // closed rather than read a stale membership and private events.
            assertThrows(SQLException.class,()->
                links.headers(a,MISSION_A,"tenant-a","project-a",25));
            c.rollback();
        }
        assertEquals(2,links.headers(a,MISSION_A,"tenant-a","project-a",10).size());
    }

    @Test @Order(8)
    void signedSessionRevocationStopsEventHistoryAndFurtherLinkWrites()
            throws Exception {
        var facts=a.requireRevocationFacts();
        try(var c=DriverManager.getConnection(DB,"e6l_revoker_login",
                System.getenv("AETHERIS_E6L_REVOKER_PASSWORD"))){
            c.setAutoCommit(false);
            try(var p=c.prepareStatement("""
                  SELECT e6j_runtime_auth.revoke_signed_session(?,?,?,?)
                  """)){
                p.setString(1,facts.verifiedIssuer());
                p.setString(2,facts.verifiedKeyId());
                p.setString(3,facts.signedTokenId());
                p.setObject(4,UUID.randomUUID());
                try(var rs=p.executeQuery()){
                    assertTrue(rs.next());assertTrue(rs.getBoolean(1));
                }
            }
            c.commit();
        }
        assertTrue(links.headers(a,MISSION_A,"tenant-a","project-a",10).isEmpty());
        assertFalse(links.attach(a,MISSION_A,TASK_A,"tenant-a","project-a"));
        assertEquals(1,count());
    }
}
