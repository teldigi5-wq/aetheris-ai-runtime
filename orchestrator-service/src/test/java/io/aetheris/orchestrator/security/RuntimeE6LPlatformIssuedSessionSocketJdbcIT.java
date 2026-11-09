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
    classes=RuntimeE6LPlatformIssuedSessionSocketJdbcIT.IsolatedApp.class,
    webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={
        "server.address=127.0.0.1",
        "server.port=0",
        "server.servlet.context-path=/runtime",
        "management.endpoints.enabled-by-default=false",
        "spring.task.scheduling.enabled=false"
    })
class RuntimeE6LPlatformIssuedSessionSocketJdbcIT {
    private static final String DB=
        "jdbc:postgresql://127.0.0.1:5432/aetheris_e6j_disposable";
    private static final String ISSUER="https://identity.aetheris.example.invalid";
    private static final String KID="e6l-platform-real-issuer";
    private static final String AUDIENCE="aetheris-orchestrator-api";
    private static final String READ="/__e6l_signed_read_probe";
    private static final String WRITE="/__e6l_signed_write_probe";
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
            return testOnly(READ,"orchestrator:read");
        }
        @Bean FilterRegistrationBean<RuntimeSignedIdentityHttpFilter> writeFilter() {
            return testOnly(WRITE,"orchestrator:write");
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
        @GetMapping(READ)
        String read(HttpServletRequest request) {return inspected(request);}
        @GetMapping(WRITE)
        String write(HttpServletRequest request) {return inspected(request);}
        private static String inspected(HttpServletRequest request) {
            var value=request.getAttribute(
                RuntimeSignedIdentityHttpFilter.VERIFIED_PRINCIPAL_ATTRIBUTE);
            if(value instanceof RuntimePinnedPrincipalVerifier.VerifiedPrincipal p
                    && p.isFresh()) {
                return "TEST_ONLY_UID_"+p.identityUid();
            }
            throw new IllegalStateException("No independently verified principal");
        }
        @GetMapping("/api/orchestrator/tasks")
        String quarantined() {return "TASK_HANDLER_MUST_NOT_RUN";}
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
    private void rejected(String path,String bearer,int code) throws Exception {
        var response=get(path,bearer);
        assertEquals(code,response.statusCode());
        assertEquals("no-store",response.headers()
            .firstValue("Cache-Control").orElse(null));
        assertFalse(response.body().contains("TEST_ONLY_UID_"));
        assertFalse(response.body().contains("e6j_runtime_auth"));
        assertFalse(response.body().contains("org.postgresql"));
    }

    @Test @Order(1)
    void twoRealPlatformIssuedAndEnrolledUsersPassRuntimeDirectHttp()
            throws Exception {
        var a=get(READ,bearer("user-a.jwt"));
        var b=get(READ,bearer("user-b.jwt"));
        assertEquals(200,a.statusCode());assertEquals(200,b.statusCode());
        assertEquals("TEST_ONLY_UID_"+uid("expected-uid-a"),a.body());
        assertEquals("TEST_ONLY_UID_"+uid("expected-uid-b"),b.body());
        assertNotEquals(a.body(),b.body());
        assertEquals(404,get("/api/orchestrator/tasks",
            bearer("user-a.jwt")).statusCode());
    }

    @Test @Order(2)
    void signedButUnregisteredAndActualPlatformHs256AreDenied()
            throws Exception {
        rejected(READ,bearer("unregistered.jwt"),401);
        rejected(READ,bearer("legacy-hs.jwt"),401);
        rejected(READ,null,401);
        rejected(READ,"Bearer malformed.token.envelope",401);
        rejected(READ,bearer("user-a.jwt")+"junk",401);
        var spoof=get(READ,bearer("user-a.jwt"),"forged-owner");
        assertEquals(401,spoof.statusCode());
        assertEquals(404,get("/api/orchestrator/tasks",null).statusCode());
    }

    @Test @Order(3)
    void separateVerifiedRoleCeilingsEnforceWriteScopeOverRealHttp()
            throws Exception {
        assertEquals(200,get(WRITE,bearer("user-a.jwt")).statusCode());
        rejected(WRITE,bearer("user-b.jwt"),403);
        assertEquals(200,get(READ,bearer("user-b.jwt")).statusCode());
    }

    @Test @Order(4)
    void authenticatedRuntimeReaderCannotReadOrMutateSessionTables()
            throws Exception {
        try(Connection limited=restricted("e6l_reader_login",
                    System.getenv("AETHERIS_E6L_READER_PASSWORD"))) {
            assertEquals("e6l_reader_login",limited.getMetaData().getUserName());
            var raw=assertThrows(SQLException.class,()->
                limited.createStatement().execute(
                    "SELECT * FROM e6j_runtime_auth.session_registrations"));
            assertEquals("42501",raw.getSQLState());
        }
        try(Connection limited=restricted("e6l_reader_login",
                    System.getenv("AETHERIS_E6L_READER_PASSWORD"))) {
            var change=assertThrows(SQLException.class,()->
                limited.createStatement().execute(
                    "UPDATE e6j_runtime_auth.session_registrations "+
                    "SET state='REVOKED'"));
            assertEquals("42501",change.getSQLState());
        }
        assertEquals(200,get(READ,bearer("user-a.jwt")).statusCode());
    }

    @Test @Order(5)
    void missingPlatformAuthorityFunctionFailsClosed503NoFallback()
            throws Exception {
        try(Connection c=admin();var stmt=c.createStatement()) {
            stmt.execute("""
                ALTER FUNCTION e6j_runtime_auth.check_signed_session(
                    varchar,varchar,varchar,bigint,bigint)
                    RENAME TO check_signed_session_disabled
                """);
        }
        try{
            rejected(READ,bearer("user-a.jwt"),503);
        }finally{
            try(Connection c=admin();var stmt=c.createStatement()){
                stmt.execute("""
                    ALTER FUNCTION e6j_runtime_auth.check_signed_session_disabled(
                        varchar,varchar,varchar,bigint,bigint)
                        RENAME TO check_signed_session
                    """);
            }
        }
        assertEquals(200,get(READ,bearer("user-a.jwt")).statusCode());
    }

    @Test @Order(6)
    void functionOnlyRevokerImmediatelyInvalidatesOneUserNotOther()
            throws Exception {
        var verifier=new RuntimePinnedPrincipalVerifier(
            fixture("e6l-platform-public.pem"),
            KID,ISSUER,AUDIENCE,Clock.systemUTC());
        var facts=verifier.verifyBearerForRevocation(
            bearer("user-a.jwt")).requireRevocationFacts();
        try(Connection writer=restricted("e6l_revoker_login",
                    System.getenv("AETHERIS_E6L_REVOKER_PASSWORD"))) {
            writer.setAutoCommit(false);
            try(var stmt=writer.prepareStatement("""
                    SELECT e6j_runtime_auth.revoke_signed_session(?,?,?,?)
                    """)){
                stmt.setString(1,facts.verifiedIssuer());
                stmt.setString(2,facts.verifiedKeyId());
                stmt.setString(3,facts.signedTokenId());
                stmt.setObject(4,UUID.randomUUID());
                try(var rows=stmt.executeQuery()){
                    assertTrue(rows.next());assertTrue(rows.getBoolean(1));
                }
            }
            writer.commit();
        }
        rejected(READ,bearer("user-a.jwt"),401);
        assertEquals(200,get(READ,bearer("user-b.jwt")).statusCode());
    }

    @Test @Order(7)
    void concurrentPendingRevocationDoesNotAuthorizeStaleSignedSession()
            throws Exception {
        var verifier=new RuntimePinnedPrincipalVerifier(
            fixture("e6l-platform-public.pem"),
            KID,ISSUER,AUDIENCE,Clock.systemUTC());
        var facts=verifier.verifyBearerForRevocation(
            bearer("user-b.jwt")).requireRevocationFacts();
        try(Connection writer=restricted("e6l_revoker_login",
                    System.getenv("AETHERIS_E6L_REVOKER_PASSWORD"))) {
            writer.setAutoCommit(false);
            try(var stmt=writer.prepareStatement("""
                    SELECT e6j_runtime_auth.revoke_signed_session(?,?,?,?)
                    """)){
                stmt.setString(1,facts.verifiedIssuer());
                stmt.setString(2,facts.verifiedKeyId());
                stmt.setString(3,facts.signedTokenId());
                stmt.setObject(4,UUID.randomUUID());
                try(var rows=stmt.executeQuery()){
                    assertTrue(rows.next());assertTrue(rows.getBoolean(1));
                }
            }
            // Real UPDATE lock is held by a separate network-authenticated
            // revoker transaction. Runtime reader must block, then return
            // generic HTTP 503 on its bounded 3s JDBC query timeout.
            rejected(READ,bearer("user-b.jwt"),503);
            writer.commit();
        }
        rejected(READ,bearer("user-b.jwt"),401);
    }
}
