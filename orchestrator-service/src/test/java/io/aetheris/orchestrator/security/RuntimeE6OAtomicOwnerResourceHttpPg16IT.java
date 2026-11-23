package io.aetheris.orchestrator.security;

import io.aetheris.orchestrator.api.OwnerlessTaskMissionHttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S0-E6L: actual Platform PR #415 Identity RS256 tokens and JTI enrollments,
 * real Runtime embedded Tomcat loopback HTTP, independent PostgreSQL 16
 * reader/revoker LOGIN roles, exact function-only E6J schema.
 *
 * Deliberately ends in IT (explicit CI execution), unregistered in default
 * Runtime app and NOT a customer route/HTTPS/mTLS certification.
 *
 * Tests are ordered because the final two permanently revoke session rows.
 * All key material, tokens and database roles are disposable CI-only.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(
    classes=RuntimeE6OAtomicOwnerResourceHttpPg16IT.IsolatedApp.class,
    webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={
        "server.address=127.0.0.1",
        "server.port=0",
        "server.servlet.context-path=/runtime",
        "management.endpoints.enabled-by-default=false",
        "spring.task.scheduling.enabled=false"
    })
class RuntimeE6OAtomicOwnerResourceHttpPg16IT {
    private static final String DB=
        "jdbc:postgresql://127.0.0.1:5432/aetheris_e6j_disposable";
    private static final String ISSUER="https://identity.aetheris.example.invalid";
    private static final String KID="e6l-platform-real-issuer";
    private static final String AUDIENCE="aetheris-orchestrator-api";
    private static final String READ="/__e6o_owned_task_read";
    private static final String WRITE="/__e6o_owned_mission_read";
    private static final String TASK_A="11111111-1111-4111-8111-111111111111";
    private static final String TASK_B="22222222-2222-4222-8222-222222222222";
    private static final String MISSION_B="33333333-3333-4333-8333-333333333333";
    private static final String OWNERLESS="44444444-4444-4444-8444-444444444444";
    private static final String FENCE_A="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String FENCE_B="bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    private static final HttpClient HTTP=HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    @LocalServerPort int port;

    @BeforeAll
    static void requireIsolatedRealPlatformFixtures() throws Exception {
        assertEquals("true",System.getenv("GITHUB_ACTIONS"));
        assertEquals("ONLY_DISPOSABLE_E6L_PG16",System.getenv("AETHERIS_E6L_MUST_RUN"));
        assertEquals("127.0.0.1",System.getenv("PGHOST"));
        assertEquals("aetheris_e6j_disposable",System.getenv("PGDATABASE"));
        assertEquals("e6j_test_only",System.getenv("PGUSER"));
        assertNotNull(System.getenv("PGPASSWORD"));
        assertNotNull(System.getenv("AETHERIS_E6L_READER_PASSWORD"));
        assertNotNull(System.getenv("AETHERIS_E6L_REVOKER_PASSWORD"));
        Path dir=fixtureDir();
        for(String name:new String[]{"e6l-platform-public.pem","user-a.jwt",
                "user-b.jwt","unregistered.jwt","legacy-hs.jwt",
                "expected-uid-a","expected-uid-b"}) {
            Path file=dir.resolve(name);
            assertTrue(Files.isRegularFile(file),name);
            assertEquals("rw-------",PosixFilePermissions.toString(
                Files.getPosixFilePermissions(file)),name);
        }
        seedReviewObjects();
    }

    private static void seedReviewObjects() throws Exception {
        long a=uid("expected-uid-a"), b=uid("expected-uid-b");
        assertNotEquals(a,b);
        try(Connection conn=admin()){
            conn.setAutoCommit(false);
            try(var st=conn.createStatement()){
                st.execute("INSERT INTO e6o_review.workload_fences VALUES "+
                    "('"+FENCE_A+"',true,1),('"+FENCE_B+"',true,1)");
            }
            try(var m=conn.prepareStatement("""
                INSERT INTO e6o_review.resource_memberships
                  (uid,tenant_key,project_key,membership_epoch,active)
                VALUES (?,?,?,1,true)
                """)){
                m.setLong(1,a);m.setString(2,"tenant-a");m.setString(3,"project-a");
                assertEquals(1,m.executeUpdate());
                m.setLong(1,b);m.setString(2,"tenant-b");m.setString(3,"project-b");
                assertEquals(1,m.executeUpdate());
            }
            try(var q=conn.prepareStatement("""
                INSERT INTO e6o_review.bound_resources
                  (id,kind,owner_uid,tenant_key,project_key,owner_verified_at,
                   workload_fence_id,object_label)
                VALUES (?::uuid,?,?,?, ?, clock_timestamp(),?::uuid,?)
                """)){
                addResource(q,TASK_A,"TASK",a,"tenant-a","project-a",FENCE_A,"ALLOWED_TASK_A");
                addResource(q,TASK_B,"TASK",b,"tenant-b","project-b",FENCE_B,"ALLOWED_TASK_B");
                addResource(q,MISSION_B,"MISSION",b,"tenant-b","project-b",FENCE_B,"ALLOWED_MISSION_B");
            }
            try(var q=conn.prepareStatement("""
                INSERT INTO e6o_review.bound_resources
                  (id,kind,owner_uid,tenant_key,project_key,owner_verified_at,
                   workload_fence_id,object_label)
                VALUES (?::uuid,'TASK',NULL,'tenant-a','project-a',NULL,?::uuid,'OWNERLESS_MUST_DENY')
                """)){
                q.setString(1,OWNERLESS);q.setString(2,FENCE_A);
                assertEquals(1,q.executeUpdate());
            }
            conn.commit();
        }
    }
    private static void addResource(java.sql.PreparedStatement q,String id,
            String kind,long uid,String tenant,String project,String fence,
            String label) throws SQLException {
        q.setString(1,id);q.setString(2,kind);q.setLong(3,uid);
        q.setString(4,tenant);q.setString(5,project);
        q.setString(6,fence);q.setString(7,label);
        assertEquals(1,q.executeUpdate());
    }

    private static Path fixtureDir() {
        String raw=System.getenv("AETHERIS_E6L_FIXTURE_DIR");
        if(raw==null) throw new IllegalStateException("No E6L fixture dir");
        return Path.of(raw);
    }
    private static String fixture(String name) {
        try {
            return Files.readString(fixtureDir().resolve(name),
                StandardCharsets.UTF_8).trim();
        }catch(Exception ex) {
            throw new IllegalStateException("Missing isolated Platform fixture");
        }
    }
    private static String bearer(String file) {
        return "Bearer "+fixture(file);
    }
    private static long uid(String file) {return Long.parseLong(fixture(file));}

    private static DataSource limitedReader() {
        var ds=new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(DB);
        ds.setUsername("e6l_reader_login");
        ds.setPassword(System.getenv("AETHERIS_E6L_READER_PASSWORD"));
        return ds;
    }
    private static Connection restricted(String user,String password)
            throws Exception {
        return DriverManager.getConnection(DB,user,password);
    }
    private static Connection admin() throws Exception {
        return restricted("e6j_test_only",System.getenv("PGPASSWORD"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude={
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
    })
    @Import({OwnerlessTaskMissionHttpFilter.class,ProbeRoutes.class})
    static class IsolatedApp {
        @Bean FilterRegistrationBean<RuntimeSignedIdentityHttpFilter> readFilter() {
            var bean=testOnly(READ,"orchestrator:read");
            bean.addUrlPatterns(WRITE);
            return bean;
        }
        private static FilterRegistrationBean<RuntimeSignedIdentityHttpFilter>
                testOnly(String exactPath,String requiredScope) {
            var verifier=new RuntimePinnedPrincipalVerifier(
                fixture("e6l-platform-public.pem"),
                KID,ISSUER,AUDIENCE,Clock.systemUTC());
            var bean=new FilterRegistrationBean<RuntimeSignedIdentityHttpFilter>();
            bean.setFilter(new RuntimeSignedIdentityHttpFilter(
                verifier,new RuntimeE6LFunctionSessionAuthority(limitedReader()),
                requiredScope));
            bean.addUrlPatterns(exactPath);
            bean.setOrder(Ordered.HIGHEST_PRECEDENCE+20);
            return bean;
        }
    }

    @RestController
    static class ProbeRoutes {
        private String authorized(HttpServletRequest request,
                RuntimeE6OScopedResourceRead.Kind kind,
                UUID id,String tenant,String project) {
            var value=request.getAttribute(
                RuntimeSignedIdentityHttpFilter.VERIFIED_PRINCIPAL_ATTRIBUTE);
            if(!(value instanceof RuntimePinnedPrincipalVerifier.VerifiedPrincipal p)
                    || !p.isFresh()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            }
            try {
                return new RuntimeE6OScopedResourceRead(limitedReader())
                    .read(p,id,kind,tenant,project)
                    .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
            }catch(SQLException failure){
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
            }
        }
        @GetMapping(READ)
        String task(HttpServletRequest req,@RequestParam UUID id,
                @RequestParam String tenant,@RequestParam String project){
            return authorized(req,RuntimeE6OScopedResourceRead.Kind.TASK,
                id,tenant,project);
        }
        @GetMapping(WRITE)
        String mission(HttpServletRequest req,@RequestParam UUID id,
                @RequestParam String tenant,@RequestParam String project){
            return authorized(req,RuntimeE6OScopedResourceRead.Kind.MISSION,
                id,tenant,project);
        }
        @GetMapping("/api/orchestrator/tasks")
        String stillQuarantined(){return "UNSAFE_TASK_HANDLER_MUST_NOT_RUN";}
        @GetMapping("/api/orchestrator/missions")
        String missionStillQuarantined(){return "UNSAFE_MISSION_HANDLER_MUST_NOT_RUN";}
    }

    private HttpResponse<String> get(String path,String token,String spoofHeader)
            throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(
            "http://127.0.0.1:"+port+"/runtime"+path))
            .timeout(Duration.ofSeconds(8)).GET();
        if(token!=null) builder.header("Authorization",token);
        if(spoofHeader!=null) builder.header("X-Forwarded-User",spoofHeader);
        return HTTP.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(String path,String token) throws Exception {
        return get(path,token,null);
    }
    private static String path(String kind,String id,String tenant,String project) {
        return kind+"?id="+id+"&tenant="+tenant+"&project="+project;
    }
    private static String task(String id,String tenant,String project) {
        return path(READ,id,tenant,project);
    }
    private static String mission(String id,String tenant,String project) {
        return path(WRITE,id,tenant,project);
    }
    private void code(String target,String token,int expected) throws Exception {
        var resp=get(target,token);
        assertEquals(expected,resp.statusCode(),target);
        assertEquals("no-store",resp.headers()
            .firstValue("Cache-Control").orElse(null));
        assertFalse(resp.body().contains("ALLOWED_"));
        assertFalse(resp.body().contains("e6o_review"));
        assertFalse(resp.body().contains("org.postgresql"));
    }

    @Test @Order(1)
    void realPlatformTwoUsersReadOnlyTheirBoundTaskOrMission() throws Exception {
        assertEquals("ALLOWED_TASK_A",
            get(task(TASK_A,"tenant-a","project-a"),bearer("user-a.jwt")).body());
        assertEquals("ALLOWED_TASK_B",
            get(task(TASK_B,"tenant-b","project-b"),bearer("user-b.jwt")).body());
        assertEquals("ALLOWED_MISSION_B",
            get(mission(MISSION_B,"tenant-b","project-b"),bearer("user-b.jwt")).body());
        assertEquals(404,get("/api/orchestrator/tasks",
            bearer("user-a.jwt")).statusCode());
        assertEquals(404,get("/api/orchestrator/missions",
            bearer("user-b.jwt")).statusCode());
    }

    @Test @Order(2)
    void crossUserCrossTenantWrongKindAndHistoricOwnerlessAreAlwaysDenied()
            throws Exception {
        code(task(TASK_B,"tenant-b","project-b"),bearer("user-a.jwt"),404);
        code(mission(MISSION_B,"tenant-b","project-b"),bearer("user-a.jwt"),404);
        code(task(TASK_A,"tenant-a","project-a"),bearer("user-b.jwt"),404);
        code(task(TASK_A,"tenant-b","project-b"),bearer("user-a.jwt"),404);
        code(mission(TASK_A,"tenant-a","project-a"),bearer("user-a.jwt"),404);
        code(task(OWNERLESS,"tenant-a","project-a"),bearer("user-a.jwt"),404);
        code(task(TASK_A,"tenant-a","project-a"),bearer("unregistered.jwt"),401);
        code(task(TASK_A,"tenant-a","project-a"),bearer("legacy-hs.jwt"),401);
        code(task(TASK_A,"tenant-a","project-a"),null,401);
        code(task(TASK_A,"tenant-a","project-a"),"Bearer malformed",401);
        assertEquals(401,get(task(TASK_A,"tenant-a","project-a"),
            bearer("user-a.jwt"),"forged-tenant").statusCode());
    }

    @Test @Order(3)
    void restrictedRuntimeSqlReaderCannotInspectOrMutateResourceRows()
            throws Exception {
        try(Connection c=restricted("e6l_reader_login",
                 System.getenv("AETHERIS_E6L_READER_PASSWORD"))){
            SQLException read=assertThrows(SQLException.class,
                ()->c.createStatement().execute(
                    "SELECT * FROM e6o_review.bound_resources"));
            assertEquals("42501",read.getSQLState());
        }
        try(Connection c=restricted("e6l_reader_login",
                 System.getenv("AETHERIS_E6L_READER_PASSWORD"))){
            SQLException write=assertThrows(SQLException.class,
                ()->c.createStatement().execute(
                    "UPDATE e6o_review.bound_resources SET object_label='attack'"));
            assertEquals("42501",write.getSQLState());
        }
        assertEquals("ALLOWED_TASK_A",
            get(task(TASK_A,"tenant-a","project-a"),bearer("user-a.jwt")).body());
    }

    @Test @Order(4)
    void inactiveWorkloadFenceDeniesBoundTaskWithoutAffectingOtherUser()
            throws Exception {
        try(Connection c=admin();var s=c.createStatement()){
            s.execute("UPDATE e6o_review.workload_fences SET active=false "+
                "WHERE fence_id='"+FENCE_A+"'");
        }
        try{
            code(task(TASK_A,"tenant-a","project-a"),bearer("user-a.jwt"),404);
            assertEquals("ALLOWED_MISSION_B",
                get(mission(MISSION_B,"tenant-b","project-b"),bearer("user-b.jwt")).body());
        }finally{
            // Disposable test admin reset is NOT a permitted production actor.
            try(Connection c=admin();var s=c.createStatement()){
                s.execute("UPDATE e6o_review.workload_fences SET active=true "+
                    "WHERE fence_id='"+FENCE_A+"'");
            }
        }
    }

    @Test @Order(5)
    void unavailableObjectAuthorityFailsClosedNoDirectTableFallback()
            throws Exception {
        try(Connection c=admin();var s=c.createStatement()){
            s.execute("""
                ALTER FUNCTION e6o_review.read_bound_resource(
                  varchar,varchar,varchar,bigint,bigint,uuid,
                  varchar,varchar,varchar)
                RENAME TO e6o_temporarily_disabled
                """);
        }
        try{
            code(task(TASK_A,"tenant-a","project-a"),bearer("user-a.jwt"),503);
        }finally{
            try(Connection c=admin();var s=c.createStatement()){
                s.execute("""
                    ALTER FUNCTION e6o_review.e6o_temporarily_disabled(
                      varchar,varchar,varchar,bigint,bigint,uuid,
                      varchar,varchar,varchar)
                    RENAME TO read_bound_resource
                    """);
            }
        }
        assertEquals("ALLOWED_TASK_A",
            get(task(TASK_A,"tenant-a","project-a"),bearer("user-a.jwt")).body());
    }

    @Test @Order(6)
    void concurrentUncommittedMembershipRevocationCannotReturnStaleObject()
            throws Exception {
        try(Connection revoker=admin()){
            revoker.setAutoCommit(false);
            try(var u=revoker.prepareStatement("""
                UPDATE e6o_review.resource_memberships
                   SET active=false,membership_epoch=membership_epoch+1
                 WHERE uid=? AND tenant_key='tenant-a'
                   AND project_key='project-a'
                """)){
                u.setLong(1,uid("expected-uid-a"));
                assertEquals(1,u.executeUpdate());
            }
            // UPDATE lock is held by separate transaction; E6O read must
            // timeout generically (503), never return stale ALLOWED_TASK_A.
            code(task(TASK_A,"tenant-a","project-a"),bearer("user-a.jwt"),503);
            revoker.commit();
        }
        code(task(TASK_A,"tenant-a","project-a"),bearer("user-a.jwt"),404);
        assertEquals("ALLOWED_MISSION_B",
            get(mission(MISSION_B,"tenant-b","project-b"),bearer("user-b.jwt")).body());
    }

    @Test @Order(7)
    void revokedPlatformSessionCannotReadEvenRemainingOwnedMission()
            throws Exception {
        var verifier=new RuntimePinnedPrincipalVerifier(
            fixture("e6l-platform-public.pem"),
            KID,ISSUER,AUDIENCE,Clock.systemUTC());
        var facts=verifier.verifyBearerForRevocation(
            bearer("user-b.jwt")).requireRevocationFacts();
        try(Connection c=restricted("e6l_revoker_login",
                System.getenv("AETHERIS_E6L_REVOKER_PASSWORD"))){
            c.setAutoCommit(false);
            try(var p=c.prepareStatement("""
                SELECT e6j_runtime_auth.revoke_signed_session(?,?,?,?)
                """)){
                p.setString(1,facts.verifiedIssuer());
                p.setString(2,facts.verifiedKeyId());
                p.setString(3,facts.signedTokenId());
                p.setObject(4,UUID.randomUUID());
                try(var rows=p.executeQuery()){
                    assertTrue(rows.next());assertTrue(rows.getBoolean(1));
                }
            }
            c.commit();
        }
        code(mission(MISSION_B,"tenant-b","project-b"),bearer("user-b.jwt"),401);
    }
}
