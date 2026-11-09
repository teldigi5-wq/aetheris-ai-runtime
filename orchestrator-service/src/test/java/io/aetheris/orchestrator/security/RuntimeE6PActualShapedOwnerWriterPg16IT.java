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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S0-E6P: real PostgreSQL16 additive migration and function-only atomic
 * creation of actual-shaped Task/Mission persistence, using real Platform
 * RSA/JTI signed tokens generated from immutable Platform PR #415 source.
 *
 * Disposable CI only: no real legacy data, no production migration and
 * NO registration on product Task/Mission HTTP routes.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RuntimeE6PActualShapedOwnerWriterPg16IT {
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
    private static final UUID MISSION_A=
        UUID.fromString("d2d2d2d2-2222-4222-8222-222222222222");
    private static final UUID LEGACY_TASK=
        UUID.fromString("aaaaaaaa-1111-4111-8111-111111111111");
    private static final UUID LEGACY_MISSION=
        UUID.fromString("bbbbbbbb-2222-4222-8222-222222222222");
    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal ownerA, consumerB;
    private static RuntimePinnedPrincipalVerifier verifier;
    private static RuntimeE6POwnedPersistenceWriter writer;

    private static Path fixtures(){
        var dir=System.getenv("AETHERIS_E6L_FIXTURE_DIR");
        if(dir==null)throw new IllegalStateException("Missing ephemeral token fixture");
        return Path.of(dir);
    }
    private static String file(String name){
        try {
            return Files.readString(fixtures().resolve(name),
                StandardCharsets.UTF_8).trim();
        }catch(Exception e){throw new IllegalStateException("Missing signed fixture");}
    }
    private static long uid(String name){return Long.parseLong(file(name));}
    private static Connection admin() throws Exception {
        return DriverManager.getConnection(DB,"e6j_test_only",
            System.getenv("PGPASSWORD"));
    }
    private static DataSource dbRole(String role,String envPassword){
        var ds=new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(DB);
        ds.setUsername(role);
        ds.setPassword(System.getenv(envPassword));
        return ds;
    }
    private static int count(String table) throws Exception{
        if(!table.equals("aetheris_tasks") &&
                !table.equals("aetheris_mission_sessions"))
            throw new IllegalArgumentException("not a review table");
        try(var c=admin();var s=c.createStatement();
            var rs=s.executeQuery("SELECT count(*) FROM public."+table)){
            assertTrue(rs.next());return rs.getInt(1);
        }
    }

    @BeforeAll
    static void requireRealSignedAndIsolatedDbAndSetup() throws Exception {
        assertEquals("true",System.getenv("GITHUB_ACTIONS"));
        assertEquals("ONLY_DISPOSABLE_E6P_PG16",System.getenv("AETHERIS_E6P_MUST_RUN"));
        assertEquals("127.0.0.1",System.getenv("PGHOST"));
        assertEquals("aetheris_e6j_disposable",System.getenv("PGDATABASE"));
        assertEquals("e6j_test_only",System.getenv("PGUSER"));
        assertNotNull(System.getenv("AETHERIS_E6P_WRITER_PASSWORD"));
        assertNotNull(System.getenv("AETHERIS_E6P_READER_PASSWORD"));
        assertEquals("e6j_test_only",System.getenv("PGUSER"));
        verifier=new RuntimePinnedPrincipalVerifier(
            file("e6l-platform-public.pem"),KID,ISSUER,AUDIENCE,Clock.systemUTC());
        ownerA=verifier.verifyBearerForRevocation("Bearer "+file("user-a.jwt"));
        consumerB=verifier.verifyBearerForRevocation("Bearer "+file("user-b.jwt"));
        assertEquals(uid("expected-uid-a"),ownerA.identityUid());
        assertEquals(uid("expected-uid-b"),consumerB.identityUid());
        assertTrue(ownerA.hasScope("orchestrator:write"));
        assertFalse(consumerB.hasScope("orchestrator:write"));
        writer=new RuntimeE6POwnedPersistenceWriter(
            dbRole("e6p_writer_login","AETHERIS_E6P_WRITER_PASSWORD"),
            dbRole("e6p_reader_login","AETHERIS_E6P_READER_PASSWORD"));
        try(Connection c=admin()){
            c.setAutoCommit(false);
            try(var ps=c.prepareStatement("""
                INSERT INTO e6o_review.resource_memberships(
                  uid,tenant_key,project_key,membership_epoch,active)
                VALUES(?,?,?,0,true)
                """)){
                ps.setLong(1,ownerA.identityUid());
                ps.setString(2,"tenant-a");ps.setString(3,"project-a");
                assertEquals(1,ps.executeUpdate());
                ps.setLong(1,consumerB.identityUid());
                ps.setString(2,"tenant-b");ps.setString(3,"project-b");
                assertEquals(1,ps.executeUpdate());
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
    }

    @Test @Order(1)
    void historicalOwnerlessTaskAndMissionStayUnboundAndNotReadable()
            throws Exception {
        assertEquals(1,count("aetheris_tasks"));
        assertEquals(1,count("aetheris_mission_sessions"));
        try(var c=admin();var ps=c.prepareStatement("""
            SELECT owner_identity_uid,owner_tenant_key,owner_project_key,
                   owner_verified_at,owner_workload_fence_id
            FROM public.aetheris_tasks WHERE id=?
            """)){
            ps.setObject(1,LEGACY_TASK);
            try(var rs=ps.executeQuery()){
                assertTrue(rs.next());
                for(int i=1;i<=5;i++)assertNull(rs.getObject(i));
            }
        }
        assertTrue(writer.read(ownerA,RuntimeE6POwnedPersistenceWriter.Kind.TASK,
            LEGACY_TASK,"tenant-a","project-a").isEmpty());
        assertTrue(writer.read(ownerA,RuntimeE6POwnedPersistenceWriter.Kind.MISSION,
            LEGACY_MISSION,"tenant-a","project-a").isEmpty());
    }

    @Test @Order(2)
    void realPlatformSignedDeveloperCreatesNewBoundTaskAndMissionAtomically()
            throws Exception {
        assertTrue(writer.create(ownerA,RuntimeE6POwnedPersistenceWriter.Kind.TASK,
            TASK_A,"tenant-a","project-a",FENCE_A,
            "owned new task","safe disposable command"));
        assertTrue(writer.create(ownerA,RuntimeE6POwnedPersistenceWriter.Kind.MISSION,
            MISSION_A,"tenant-a","project-a",FENCE_A,
            "owned new mission","synthetic review objective"));
        assertEquals(2,count("aetheris_tasks"));
        assertEquals(2,count("aetheris_mission_sessions"));
        assertEquals("owned new task",writer.read(ownerA,
            RuntimeE6POwnedPersistenceWriter.Kind.TASK,TASK_A,
            "tenant-a","project-a").orElseThrow());
        assertEquals("owned new mission",writer.read(ownerA,
            RuntimeE6POwnedPersistenceWriter.Kind.MISSION,MISSION_A,
            "tenant-a","project-a").orElseThrow());
        assertTrue(writer.read(consumerB,
            RuntimeE6POwnedPersistenceWriter.Kind.TASK,TASK_A,
            "tenant-a","project-a").isEmpty());
        assertTrue(writer.read(ownerA,
            RuntimeE6POwnedPersistenceWriter.Kind.TASK,TASK_A,
            "tenant-b","project-b").isEmpty());
        try(var c=admin();var p=c.prepareStatement("""
            SELECT owner_identity_uid,owner_workload_fence_id
            FROM public.aetheris_mission_sessions WHERE id=?
            """)){
            p.setObject(1,MISSION_A);
            try(var rs=p.executeQuery()){
                assertTrue(rs.next());
                assertEquals(ownerA.identityUid(),rs.getLong(1));
                assertEquals(FENCE_A,rs.getObject(2));
            }
        }
    }

    @Test @Order(3)
    void readonlyConsumerWrongTenantAndUnregisteredJwtCannotCreate()
            throws Exception {
        int baseline=count("aetheris_tasks");
        assertFalse(writer.create(consumerB,RuntimeE6POwnedPersistenceWriter.Kind.TASK,
            UUID.randomUUID(),"tenant-b","project-b",FENCE_B,
            "denied consumer","no write scope"));
        assertFalse(writer.create(ownerA,RuntimeE6POwnedPersistenceWriter.Kind.TASK,
            UUID.randomUUID(),"tenant-b","project-b",FENCE_B,
            "denied cross tenant","wrong owner membership"));
        assertFalse(writer.create(ownerA,RuntimeE6POwnedPersistenceWriter.Kind.TASK,
            UUID.randomUUID(),"tenant-a","project-a",FENCE_B,
            "denied cross fence","not an approved tenant fence"));
        var notEnrolled=verifier.verifyBearerForRevocation(
            "Bearer "+file("unregistered.jwt"));
        assertFalse(writer.create(notEnrolled,
            RuntimeE6POwnedPersistenceWriter.Kind.TASK,
            UUID.randomUUID(),"tenant-a","project-a",FENCE_A,
            "denied unregistered","no durable JTI"));
        assertEquals(baseline,count("aetheris_tasks"));
    }

    @Test @Order(4)
    void realLimitedDatabaseRolesCannotRawReadWriteOrReassign()
            throws Exception {
        for(String[] role:new String[][]{
            {"e6p_writer_login","AETHERIS_E6P_WRITER_PASSWORD"},
            {"e6p_reader_login","AETHERIS_E6P_READER_PASSWORD"}}){
            try(var c=DriverManager.getConnection(DB,role[0],
                       System.getenv(role[1]))){
                SQLException denied=assertThrows(SQLException.class,
                    ()->c.createStatement().executeQuery(
                        "SELECT * FROM public.aetheris_tasks"));
                assertEquals("42501",denied.getSQLState());
            }
            try(var c=DriverManager.getConnection(DB,role[0],
                       System.getenv(role[1]))){
                SQLException denied=assertThrows(SQLException.class,
                    ()->c.createStatement().execute(
                        "UPDATE public.aetheris_mission_sessions "+
                        "SET owner_identity_uid=1"));
                assertEquals("42501",denied.getSQLState());
            }
        }
        try(var c=DriverManager.getConnection(DB,"e6p_reader_login",
                   System.getenv("AETHERIS_E6P_READER_PASSWORD"))){
            SQLException rejected=assertThrows(SQLException.class,
                ()->c.createStatement().execute(
                  "SELECT e6p_review.create_owned('x','x','x',1,1,"+
                  "gen_random_uuid(),'TASK','a','b',gen_random_uuid(),'x','x')"));
            assertEquals("42501",rejected.getSQLState());
        }
    }

    @Test @Order(5)
    void evenPrivilegedFixtureCannotClaimOrReassignExistingOwnerlessRows()
            throws Exception {
        for(String[] item:new String[][]{
            {"aetheris_tasks",LEGACY_TASK.toString()},
            {"aetheris_mission_sessions",LEGACY_MISSION.toString()},
            {"aetheris_tasks",TASK_A.toString()},
            {"aetheris_mission_sessions",MISSION_A.toString()}}){
            try(var c=admin();var p=c.prepareStatement(
                "UPDATE public."+item[0]+" SET owner_identity_uid=999 WHERE id=?")){
                p.setObject(1,UUID.fromString(item[1]));
                SQLException denied=assertThrows(SQLException.class,p::executeUpdate);
                assertEquals("P0001",denied.getSQLState());
            }
        }
        assertEquals(2,count("aetheris_tasks"));
        assertEquals(2,count("aetheris_mission_sessions"));
    }

    @Test @Order(6)
    void deactivatedBoundFenceDeniesNewCreationAndExistingReads()
            throws Exception {
        try(var c=admin();var s=c.createStatement()){
            s.execute("UPDATE e6o_review.workload_fences SET active=false "+
                "WHERE fence_id='"+FENCE_A+"'");
        }
        try {
            assertFalse(writer.create(ownerA,RuntimeE6POwnedPersistenceWriter.Kind.TASK,
                UUID.randomUUID(),"tenant-a","project-a",FENCE_A,
                "denied inactive fence","no active workload authority"));
            assertTrue(writer.read(ownerA,RuntimeE6POwnedPersistenceWriter.Kind.TASK,
                TASK_A,"tenant-a","project-a").isEmpty());
        }finally{
            try(var c=admin();var s=c.createStatement()){
                s.execute("UPDATE e6o_review.workload_fences SET active=true "+
                    "WHERE fence_id='"+FENCE_A+"'");
            }
        }
    }

    @Test @Order(7)
    void concurrentMembershipRevocationBlocksCreateAndPreventsCommittedWrite()
            throws Exception {
        final UUID blocked=UUID.randomUUID();
        try(var c=admin()){
            c.setAutoCommit(false);
            try(var p=c.prepareStatement("""
                 UPDATE e6o_review.resource_memberships SET active=false,
                   membership_epoch=membership_epoch+1
                 WHERE uid=? AND tenant_key='tenant-a' AND project_key='project-a'
                """)){
                p.setLong(1,ownerA.identityUid());
                assertEquals(1,p.executeUpdate());
            }
            // E6P's membership FOR SHARE must wait behind this exclusive
            // row UPDATE; query timeout is fail closed, not a stale insert.
            assertThrows(SQLException.class,()->writer.create(
                ownerA,RuntimeE6POwnedPersistenceWriter.Kind.TASK,
                blocked,"tenant-a","project-a",FENCE_A,
                "race denied","do not admit after membership revocation"));
            c.commit();
        }
        assertFalse(writer.create(ownerA,RuntimeE6POwnedPersistenceWriter.Kind.TASK,
            blocked,"tenant-a","project-a",FENCE_A,
            "still denied","membership was revoked"));
        try(var c=admin();var p=c.prepareStatement(
                "SELECT count(*) FROM public.aetheris_tasks WHERE id=?")){
            p.setObject(1,blocked);
            try(var rs=p.executeQuery()){
                assertTrue(rs.next());assertEquals(0,rs.getInt(1));
            }
        }
        assertTrue(writer.read(ownerA,RuntimeE6POwnedPersistenceWriter.Kind.TASK,
            TASK_A,"tenant-a","project-a").isEmpty());
    }
}
