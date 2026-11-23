package io.aetheris.orchestrator.security;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import io.aetheris.orchestrator.mission.RuntimeE6TCanonicalMissionRepository;
import io.aetheris.orchestrator.mission.RuntimeE6TCanonicalMissionService;
import io.aetheris.orchestrator.mission.MissionStatus;

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
 * S0-E6T real signed Platform JTI through canonical Mission repository/service preview.
 * No application Task/Mission/SSE controller, migration, live account or
 * Gateway service identity is activated. All tests run with real restricted
 * PostgreSQL LOGINs and real signed session enrollments from Platform #415.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RuntimeE6TMissionServicePg16IT {
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
    private static RuntimeE6SCanonicalMissionApplication canonical;
    private static RuntimeE6TCanonicalMissionRepository repository;
    private static RuntimeE6TCanonicalMissionService service;
    private static final UUID SECOND=UUID.fromString("eeeeeeee-5555-4555-8555-555555555555");
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
        assertEquals("ONLY_DISPOSABLE_E6S_PG16",System.getenv("AETHERIS_E6S_MUST_RUN"));
        assertEquals("ONLY_DISPOSABLE_E6T_PG16",System.getenv("AETHERIS_E6T_MUST_RUN"));
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
        canonical=new RuntimeE6SCanonicalMissionApplication(write,read);
        repository=new RuntimeE6TCanonicalMissionRepository(read);
        service=new RuntimeE6TCanonicalMissionService(repository,canonical);
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
    void unpromotedOrLegacyOwnerlessMissionHasNoServiceRepositoryProjection()
            throws Exception {
        assertTrue(service.describe(a,MISSION_A,"tenant-a","project-a").isEmpty());
        assertTrue(service.describe(a,LEGACY_MISSION,"tenant-a","project-a").isEmpty());
        assertTrue(service.taskIds(a,MISSION_A,"tenant-a","project-a",25).isEmpty());
        assertTrue(service.eventHeaders(a,MISSION_A,"tenant-a","project-a",25).isEmpty());
        assertFalse(service.attach(a,MISSION_A,TASK_A,"tenant-a","project-a"));
        assertEquals(0,count());
    }

    @Test @Order(2)
    void explicitCanonicalPromotionReturnsOnlyTypedOwnedJPAColumnProjection()
            throws Exception {
        assertEquals("CANONICAL",cutover.review(
            MISSION_A,UUID.randomUUID(),true));
        var snapshot=service.describe(
            a,MISSION_A,"tenant-a","project-a").orElseThrow();
        assertEquals(MISSION_A,snapshot.id());
        assertEquals("review owned mission A",snapshot.title());
        assertEquals(MissionStatus.ACTIVE,snapshot.status());
        assertNotNull(snapshot.createdAt());
        assertNotNull(snapshot.updatedAt());
        assertTrue(snapshot.version()>=0);
        // Projection is intentionally narrower than MissionSessionView:
        // no objective, taskIdsCsv, liveStreamPath or unverified task IDs.
        var output=snapshot.toString();
        assertFalse(output.contains("synthetic objective"));
        assertFalse(output.contains("taskIdsCsv"));
        assertFalse(output.contains("/api/orchestrator/live/events"));
    }

    @Test @Order(3)
    void serviceAttachesAndReadsOnlyCanonicalOwnedLinksAndPrivateEventHeaders()
            throws Exception {
        assertTrue(service.attach(a,MISSION_A,TASK_A,"tenant-a","project-a"));
        assertTrue(service.attach(a,MISSION_A,TASK_A,"tenant-a","project-a"));
        assertEquals(1,count());
        assertEquals(java.util.List.of(TASK_A),
            service.taskIds(a,MISSION_A,"tenant-a","project-a",25));
        assertEquals("",csv(MISSION_A),"Legacy CSV was not a data source");
        var headers=service.eventHeaders(a,MISSION_A,
            "tenant-a","project-a",25);
        assertEquals(2,headers.size());
        for(var item:headers) {
            assertEquals(TASK_A,item.taskId());
            assertNotNull(item.eventAt());
        }
        String projection=headers.toString();
        for(String forbidden:new String[]{"SECRET_","private-agent",
                "FORBIDDEN_RAW_META","B private","metadata",
                "synthetic command","synthetic objective"}){
            assertFalse(projection.contains(forbidden),forbidden);
        }
        assertEquals(1,service.eventHeaders(a,MISSION_A,
            "tenant-a","project-a",1).size());
    }

    @Test @Order(4)
    void crossTenantReadOnlyAndValidButUnenrolledJwtDenyEveryServiceMethod()
            throws Exception {
        assertTrue(service.describe(b,MISSION_A,"tenant-a","project-a").isEmpty());
        assertTrue(service.describe(a,MISSION_A,"tenant-b","project-b").isEmpty());
        assertTrue(service.describe(a,MISSION_A,"tenant-a","project-b").isEmpty());
        assertTrue(service.taskIds(b,MISSION_A,"tenant-a","project-a",25).isEmpty());
        assertTrue(service.eventHeaders(b,MISSION_A,"tenant-a","project-a",25).isEmpty());
        assertFalse(service.attach(b,MISSION_A,TASK_A,"tenant-a","project-a"));
        assertFalse(service.attach(a,MISSION_A,TASK_B,"tenant-a","project-a"));
        var neverEnrolled=verifier.verifyBearerForRevocation(
            "Bearer "+file("unregistered.jwt"));
        assertTrue(service.describe(neverEnrolled,MISSION_A,
            "tenant-a","project-a").isEmpty());
        assertFalse(service.attach(neverEnrolled,MISSION_A,TASK_A,
            "tenant-a","project-a"));
        assertTrue(service.taskIds(neverEnrolled,MISSION_A,
            "tenant-a","project-a",25).isEmpty());
        assertTrue(service.eventHeaders(neverEnrolled,MISSION_A,
            "tenant-a","project-a",25).isEmpty());
        assertEquals(1,count());
    }

    @Test @Order(5)
    void historicalDirtyCsvIsQuarantinedAndCannotFeedCanonicalRepository()
            throws Exception {
        setCsv(LEGACY_MISSION,LEGACY_TASK+","+TASK_A);
        assertEquals("QUARANTINED",
            cutover.review(LEGACY_MISSION,UUID.randomUUID(),true));
        assertEquals(LEGACY_TASK+","+TASK_A,csv(LEGACY_MISSION));
        assertTrue(service.describe(a,LEGACY_MISSION,
            "tenant-a","project-a").isEmpty());
        assertTrue(service.taskIds(a,LEGACY_MISSION,
            "tenant-a","project-a",25).isEmpty());
        assertFalse(service.attach(a,LEGACY_MISSION,TASK_A,
            "tenant-a","project-a"));
        deniedCsvWrite(LEGACY_MISSION);
        newMission(DIRTY,"owned but dirty Mission");
        setCsv(DIRTY,TASK_B.toString());
        assertEquals("QUARANTINED",
            cutover.review(DIRTY,UUID.randomUUID(),true));
        assertTrue(service.describe(a,DIRTY,"tenant-a","project-a").isEmpty());
    }

    @Test @Order(6)
    void scopedRepositorySqlLoginCannotSelectRawMissionOrEventData()
            throws Exception {
        try(var c=DriverManager.getConnection(DB,"e6p_reader_login",
                System.getenv("AETHERIS_E6P_READER_PASSWORD"))){
            SQLException denied=assertThrows(SQLException.class,()->
                c.createStatement().executeQuery(
                    "SELECT objective,task_ids_csv FROM public.aetheris_mission_sessions"));
            assertEquals("42501",denied.getSQLState());
        }
        try(var c=DriverManager.getConnection(DB,"e6p_reader_login",
                System.getenv("AETHERIS_E6P_READER_PASSWORD"))){
            SQLException denied=assertThrows(SQLException.class,()->
                c.createStatement().executeQuery(
                    "SELECT message,metadata_json FROM public.aetheris_task_events"));
            assertEquals("42501",denied.getSQLState());
        }
        try(var c=DriverManager.getConnection(DB,"e6p_writer_login",
                System.getenv("AETHERIS_E6P_WRITER_PASSWORD"))){
            SQLException denied=assertThrows(SQLException.class,()->
                c.createStatement().execute(
                    "SELECT * FROM e6t_review.canonical_mission_projection('x','x','x',1,1,"+
                    "gen_random_uuid(),'tenant-a','project-a')"));
            assertEquals("42501",denied.getSQLState());
        }
    }

    @Test @Order(7)
    void missingCanonicalMissionProjectionFailsClosedWithoutJPAFallback()
            throws Exception {
        try(var c=admin();var stmt=c.createStatement()){
            stmt.execute("""
                ALTER FUNCTION e6t_review.canonical_mission_projection(
                varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar)
                RENAME TO temporarily_disabled
                """);
        }
        try{
            assertThrows(SQLException.class,()->
                service.describe(a,MISSION_A,"tenant-a","project-a"));
        }finally{
            try(var c=admin();var stmt=c.createStatement()){
                stmt.execute("""
                    ALTER FUNCTION e6t_review.temporarily_disabled(
                    varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar)
                    RENAME TO canonical_mission_projection
                    """);
            }
        }
        assertTrue(service.describe(a,MISSION_A,"tenant-a","project-a").isPresent());
    }

    @Test @Order(8)
    void concurrentMembershipRevocationDoesNotRevealStaleMissionMetadata()
            throws Exception {
        try(var c=admin()){
            c.setAutoCommit(false);
            try(var stmt=c.prepareStatement("""
                UPDATE e6o_review.resource_memberships SET active=false,
                    membership_epoch=membership_epoch+1
                WHERE uid=? AND tenant_key='tenant-a' AND project_key='project-a'
                """)){
                stmt.setLong(1,a.identityUid());
                assertEquals(1,stmt.executeUpdate());
            }
            assertThrows(SQLException.class,()->
                service.describe(a,MISSION_A,"tenant-a","project-a"));
            c.rollback();
        }
        assertTrue(service.describe(a,MISSION_A,"tenant-a","project-a").isPresent());
    }

    @Test @Order(9)
    void rollbackFreezesCanonicalServiceProjectionLinksAndEventHeaders()
            throws Exception {
        newMission(SECOND,"another canonical Mission");
        assertEquals("CANONICAL",
            cutover.review(SECOND,UUID.randomUUID(),true));
        assertTrue(service.attach(a,SECOND,TASK_A,"tenant-a","project-a"));
        assertTrue(service.describe(a,SECOND,"tenant-a","project-a").isPresent());

        assertEquals("FROZEN",cutover.review(MISSION_A,UUID.randomUUID(),false));
        assertTrue(service.describe(a,MISSION_A,"tenant-a","project-a").isEmpty());
        assertTrue(service.taskIds(a,MISSION_A,"tenant-a","project-a",25).isEmpty());
        assertTrue(service.eventHeaders(a,MISSION_A,"tenant-a","project-a",25).isEmpty());
        assertFalse(service.attach(a,MISSION_A,TASK_A,"tenant-a","project-a"));
        assertThrows(SQLException.class,()->
            links.headers(a,MISSION_A,"tenant-a","project-a",25));
        assertThrows(SQLException.class,()->
            links.attach(a,MISSION_A,TASK_A,"tenant-a","project-a"));
        assertEquals("",csv(MISSION_A));
    }

    @Test @Order(10)
    void signedPlatformJtiRevocationRemovesServiceAccessEvenWhenDataPersists()
            throws Exception {
        assertTrue(service.describe(a,SECOND,"tenant-a","project-a").isPresent());
        var signed=a.requireRevocationFacts();
        try(var c=DriverManager.getConnection(DB,"e6l_revoker_login",
                System.getenv("AETHERIS_E6L_REVOKER_PASSWORD"))){
            c.setAutoCommit(false);
            try(var p=c.prepareStatement(
                "SELECT e6j_runtime_auth.revoke_signed_session(?,?,?,?)")){
                p.setString(1,signed.verifiedIssuer());
                p.setString(2,signed.verifiedKeyId());
                p.setString(3,signed.signedTokenId());
                p.setObject(4,UUID.randomUUID());
                try(var rows=p.executeQuery()){
                    assertTrue(rows.next());assertTrue(rows.getBoolean(1));
                }
            }
            c.commit();
        }
        assertTrue(service.describe(a,SECOND,"tenant-a","project-a").isEmpty());
        assertTrue(service.taskIds(a,SECOND,"tenant-a","project-a",25).isEmpty());
        assertTrue(service.eventHeaders(a,SECOND,"tenant-a","project-a",25).isEmpty());
        assertFalse(service.attach(a,SECOND,TASK_A,"tenant-a","project-a"));
        assertEquals(2,count());
    }
}
