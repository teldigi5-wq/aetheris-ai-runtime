package io.aetheris.orchestrator.security;

import io.aetheris.orchestrator.api.OwnerlessTaskMissionHttpFilter;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deliberately excluded from the ordinary fast Java unit suite (*IT suffix).
 * MUST be explicitly selected in the disposable GitHub PostgreSQL16 CI job.
 * Actual loopback TCP/Tomcat + real JDBC/PG16 row locks, ephemeral signed JWTs,
 * no production database, no public route activation or durable writer claims.
 */
@SpringBootTest(
    classes=RuntimePg16ReviewSessionAuthoritySocketJdbcIT.IsolatedApp.class,
    webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={
        "server.address=127.0.0.1",
        "server.port=0",
        "server.servlet.context-path=/runtime",
        "management.endpoints.enabled-by-default=false",
        "spring.task.scheduling.enabled=false"
    })
class RuntimePg16ReviewSessionAuthoritySocketJdbcIT {
    private static final String DB=
        "jdbc:postgresql://127.0.0.1:5432/ownerlab_disposable";
    private static final String SCHEMA=
        "owner_http_pg_"+UUID.randomUUID().toString().replace("-","");
    private static final String ISSUER="https://identity.aetheris.example.invalid";
    private static final String AUDIENCE="aetheris-orchestrator-api";
    private static final String KID="pg-http-review-key";
    private static final String PROBE="/__pg_review_bearer_probe";
    private static final KeyPair KEY=makeKey();
    private static final HttpClient CLIENT=HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(3)).build();

    @LocalServerPort int port;
    @Autowired DataSource reviewDataSource;

    private record SignedToken(String bearer,String jti,long uid,long iat) {}

    private static KeyPair makeKey() {
        try {
            var gen=KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch(Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static String pem() {
        return "-----BEGIN PUBLIC KEY-----\n"+
            Base64.getMimeEncoder(64,new byte[]{'\n'})
                .encodeToString(KEY.getPublic().getEncoded())+
            "\n-----END PUBLIC KEY-----";
    }

    private static SignedToken issue(long uid) {
        Instant now=Instant.now().minusSeconds(10);
        String id="pg_socket_"+UUID.randomUUID();
        String jwt=Jwts.builder().header().keyId(KID).and()
            .subject("disposable-"+uid+"@example.invalid").issuer(ISSUER)
            .claim("aud",List.of(AUDIENCE)).claim("uid",uid)
            .claim("role","DEVELOPER")
            .claim("scopes",List.of("orchestrator:read"))
            .id(id).issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(180)))
            .signWith(KEY.getPrivate(),Jwts.SIG.RS256).compact();
        return new SignedToken("Bearer "+jwt,id,uid,now.getEpochSecond());
    }

    @BeforeAll
    static void createDisposableSchema() throws Exception {
        Assumptions.assumeTrue(
            "yes".equals(System.getenv("AETHERIS_SESSION_CREATE_JDBC_MUST_RUN")),
            "Only explicitly scheduled disposable PG16 CI may run this test");
        assertEquals("true",System.getenv("GITHUB_ACTIONS"));
        assertEquals("127.0.0.1",System.getenv("PGHOST"));
        assertEquals("ownerlab_disposable",System.getenv("PGDATABASE"));
        assertEquals("ownerlab_ci",System.getenv("PGUSER"));
        assertNotNull(System.getenv("PGPASSWORD"));
        try(Connection c=admin();Statement s=c.createStatement()) {
            s.execute("CREATE SCHEMA "+SCHEMA);
            s.execute("SET search_path TO "+SCHEMA);
            s.execute("""
                CREATE TABLE synthetic_session_registration(
                    issuer varchar(255) NOT NULL,kid varchar(64) NOT NULL,
                    jti varchar(128) NOT NULL,uid bigint NOT NULL,
                    iat_seconds bigint NOT NULL,state varchar(16) NOT NULL
                    CHECK(state IN ('ACTIVE','REVOKED')),
                    PRIMARY KEY(issuer,kid,jti))""");
            s.execute("""
                CREATE TABLE signer_key_states(
                    issuer varchar(255) NOT NULL,kid varchar(64) NOT NULL,
                    state varchar(16) NOT NULL CHECK(state IN ('ACTIVE','REVOKED')),
                    PRIMARY KEY(issuer,kid))""");
            s.execute("""
                CREATE TABLE principal_epochs(
                    principal_id bigint PRIMARY KEY,
                    reject_issued_at_or_before_epoch bigint NOT NULL)""");
            s.execute("INSERT INTO signer_key_states VALUES ('"+
                ISSUER+"','"+KID+"','ACTIVE')");
            s.execute("INSERT INTO principal_epochs VALUES (42,0),(77,0)");
        }
    }

    @AfterAll
    static void dropDisposableSchema() throws Exception {
        if(!"yes".equals(System.getenv("AETHERIS_SESSION_CREATE_JDBC_MUST_RUN")))
            return;
        try(Connection c=admin();Statement s=c.createStatement()) {
            s.execute("DROP SCHEMA IF EXISTS "+SCHEMA+" CASCADE");
            try(var r=s.executeQuery(
                    "SELECT count(*) FROM pg_namespace WHERE nspname='"+SCHEMA+"'")) {
                assertTrue(r.next());
                assertEquals(0,r.getInt(1),"Disposable review schema must be removed");
            }
        }
    }

    private static Connection admin() throws Exception {
        return DriverManager.getConnection(DB,"ownerlab_ci",
            System.getenv("PGPASSWORD"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude={
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
    })
    @Import({OwnerlessTaskMissionHttpFilter.class,ProbeRoutes.class})
    static class IsolatedApp {
        @Bean DataSource reviewDataSource() {
            var ds=new DriverManagerDataSource();
            ds.setDriverClassName("org.postgresql.Driver");
            ds.setUrl(DB+"?currentSchema="+SCHEMA);
            ds.setUsername("ownerlab_ci");
            ds.setPassword(System.getenv("PGPASSWORD"));
            return ds;
        }
        @Bean FilterRegistrationBean<RuntimeSignedIdentityHttpFilter>
                isolatedPgHttpFilter(DataSource reviewDataSource) {
            var verifier=new RuntimePinnedPrincipalVerifier(
                pem(),KID,ISSUER,AUDIENCE,Clock.systemUTC());
            var bean=new FilterRegistrationBean<RuntimeSignedIdentityHttpFilter>();
            bean.setFilter(new RuntimeSignedIdentityHttpFilter(
                verifier,new RuntimePg16ReviewSessionAuthority(reviewDataSource),
                "orchestrator:read"));
            // EXACT test-only path; real Task/Mission quarantine stays unconditional.
            bean.addUrlPatterns(PROBE);
            bean.setOrder(Ordered.HIGHEST_PRECEDENCE+20);
            return bean;
        }
    }

    @RestController
    static class ProbeRoutes {
        @GetMapping(PROBE)
        String probe(HttpServletRequest request) {
            var value=request.getAttribute(
                RuntimeSignedIdentityHttpFilter.VERIFIED_PRINCIPAL_ATTRIBUTE);
            if(value instanceof RuntimePinnedPrincipalVerifier.VerifiedPrincipal p
                    && p.isFresh()) {
                return "TEST_ONLY_PG_UID_"+p.identityUid();
            }
            throw new IllegalStateException("Missing verified principal");
        }
        @GetMapping("/api/orchestrator/tasks")
        String quarantined() {return "FORBIDDEN_TASK_HANDLER_REACHED";}
    }

    private static void register(SignedToken token,long storedUid,long storedIat)
            throws Exception {
        try(Connection c=admin();Statement s=c.createStatement()) {
            s.execute("SET search_path TO "+SCHEMA);
            try(PreparedStatement p=c.prepareStatement("""
                INSERT INTO synthetic_session_registration
                   (issuer,kid,jti,uid,iat_seconds,state)
                   VALUES (?,?,?,?,?,'ACTIVE')""")) {
                p.setString(1,ISSUER);p.setString(2,KID);
                p.setString(3,token.jti());
                p.setLong(4,storedUid);p.setLong(5,storedIat);
                assertEquals(1,p.executeUpdate());
            }
        }
    }

    private static void update(String sql) throws Exception {
        try(Connection c=admin();Statement s=c.createStatement()) {
            s.execute("SET search_path TO "+SCHEMA);
            int changed=s.executeUpdate(sql);
            assertEquals(sql.startsWith("ALTER TABLE")?0:1,changed);
        }
    }

    private HttpResponse<String> get(String path,String bearer) throws Exception {
        HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(
            "http://127.0.0.1:"+port+"/runtime"+path))
            .timeout(Duration.ofSeconds(9)).GET();
        if(bearer!=null) b.header("Authorization",bearer);
        return CLIENT.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }

    private void denied(String bearer,int code) throws Exception {
        var x=get(PROBE,bearer);
        assertEquals(code,x.statusCode());
        assertEquals("no-store",
            x.headers().firstValue("Cache-Control").orElse(null));
        assertFalse(x.body().contains("TEST_ONLY_PG_UID_"));
        assertFalse(x.body().contains("synthetic_session_registration"));
        assertFalse(x.body().contains("org.postgresql"));
    }

    @Test
    void twoSignedUsersUseRealPrimaryPgSessionsAndRevocation() throws Exception {
        var a=issue(42);var b=issue(77);
        denied(a.bearer(),401);
        register(a,42,a.iat());register(b,77,b.iat());
        assertEquals("TEST_ONLY_PG_UID_42",get(PROBE,a.bearer()).body());
        assertEquals("TEST_ONLY_PG_UID_77",get(PROBE,b.bearer()).body());
        update("UPDATE synthetic_session_registration SET state='REVOKED' "+
            "WHERE jti='"+a.jti()+"'");
        denied(a.bearer(),401);
        assertEquals(200,get(PROBE,b.bearer()).statusCode());
        assertEquals(404,get("/api/orchestrator/tasks",b.bearer()).statusCode());
    }

    @Test
    void signerAndEpochRevocationsCannotBeBypassedByValidJwt() throws Exception {
        var a=issue(42);
        register(a,42,a.iat());
        assertEquals(200,get(PROBE,a.bearer()).statusCode());
        try {
            update("UPDATE signer_key_states SET state='REVOKED' "+
                "WHERE kid='"+KID+"'");
            denied(a.bearer(),401);
        } finally {
            update("UPDATE signer_key_states SET state='ACTIVE' "+
                "WHERE kid='"+KID+"'");
        }
        assertEquals(200,get(PROBE,a.bearer()).statusCode());
        try {
            update("UPDATE principal_epochs SET "+
                "reject_issued_at_or_before_epoch="+a.iat()+
                " WHERE principal_id=42");
            denied(a.bearer(),401);
        } finally {
            update("UPDATE principal_epochs SET "+
                "reject_issued_at_or_before_epoch=0 WHERE principal_id=42");
        }
    }

    @Test
    void exactUidAndIssuedAtBindingAreRequiredEvenForSignedTokens()
            throws Exception {
        var wrongUid=issue(42);
        register(wrongUid,77,wrongUid.iat());
        denied(wrongUid.bearer(),401);
        var wrongTime=issue(42);
        register(wrongTime,42,wrongTime.iat()+1);
        denied(wrongTime.bearer(),401);
        denied(null,401);
    }

    @Test
    void registryUnavailableYieldsGeneric503WithoutFallback() throws Exception {
        var a=issue(42);register(a,42,a.iat());
        assertEquals(200,get(PROBE,a.bearer()).statusCode());
        update("ALTER TABLE synthetic_session_registration "+
            "RENAME TO synthetic_session_registration_offline");
        try {
            denied(a.bearer(),503);
        } finally {
            update("ALTER TABLE synthetic_session_registration_offline "+
                "RENAME TO synthetic_session_registration");
        }
        assertEquals(200,get(PROBE,a.bearer()).statusCode());
    }

    @Test
    void pendingRevocationDoesNotUseStaleSessionSnapshot() throws Exception {
        var a=issue(42);register(a,42,a.iat());
        assertEquals(200,get(PROBE,a.bearer()).statusCode());
        // Keep an UPDATE uncommitted on a separate real PostgreSQL connection.
        // HTTP preflight must not read the old ACTIVE row through that lock.
        try(Connection writer=reviewDataSource.getConnection()) {
            writer.setAutoCommit(false);
            try(Statement s=writer.createStatement()) {
                assertEquals(1,s.executeUpdate(
                    "UPDATE synthetic_session_registration "+
                    "SET state='REVOKED' WHERE jti='"+a.jti()+"'"));
            }
            denied(a.bearer(),503); // 3s query lock timeout -> no stale allow.
            writer.commit();
        }
        denied(a.bearer(),401);
    }
}
