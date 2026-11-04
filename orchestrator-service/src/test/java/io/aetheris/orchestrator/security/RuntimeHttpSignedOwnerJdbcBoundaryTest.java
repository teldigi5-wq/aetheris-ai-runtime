package io.aetheris.orchestrator.security;

import io.aetheris.orchestrator.api.OwnerlessTaskMissionHttpFilter;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REVIEW-ONLY synthetic S0 boundary test, deliberately NOT a deployable
 * authorization endpoint: raw loopback Tomcat + actual production quarantine
 * filters + pinned RS256 verifier + actual disposable PostgreSQL16 JDBC query.
 *
 * The only positive owner-read path is /__s0/owner-read, a servlet assembled
 * in this test class. Application /api and /actuator remain denied. This does
 * NOT prove an authenticated production HTTP resource server, service mTLS,
 * session revocation, migrations, or safe API availability.
 */
class RuntimeHttpSignedOwnerJdbcBoundaryTest {
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String AUDIENCE = "aetheris-orchestrator-api";
    private static final String KID = "s0-http-jdbc-ci";
    private static final UUID ALICE_TASK =
        UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID BOB_TASK =
        UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID LEGACY_TASK =
        UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final AtomicInteger BLOCKED_DISPATCHES = new AtomicInteger();
    private static final RuntimeVerifiedTaskReadProbe PROBE = new RuntimeVerifiedTaskReadProbe();

    private static Tomcat server;
    private static HttpClient client;
    private static String origin;
    private static String disposableSchema;
    private static String password;
    private static KeyPair trustedKey;
    private static KeyPair attackerKey;
    private static RuntimePinnedPrincipalVerifier verifier;

    private static Connection connect() throws Exception {
        Connection connection = DriverManager.getConnection(
            "jdbc:postgresql://127.0.0.1:5432/ownerlab_disposable",
            "ownerlab_ci", password);
        connection.setSchema(disposableSchema);
        return connection;
    }

    @BeforeAll
    static void initializeDisposableOnly() throws Exception {
        Assumptions.assumeTrue("yes".equals(System.getenv("AETHERIS_MEMBERSHIP_JDBC_MUST_RUN")),
            "Must only run in explicit disposable PostgreSQL16 GitHub CI");
        assertEquals("true", System.getenv("GITHUB_ACTIONS"));
        assertEquals("yes", System.getenv("AETHERIS_OWNER_SCHEMA_DISPOSABLE_CI_ONLY"));
        assertEquals("127.0.0.1", System.getenv("PGHOST"));
        assertEquals("ownerlab_disposable", System.getenv("PGDATABASE"));
        assertEquals("ownerlab_ci", System.getenv("PGUSER"));
        password = System.getenv("PGPASSWORD");
        assertNotNull(password);
        assertFalse(password.isBlank());

        // A generated identifier is never derived from an HTTP/JWT parameter.
        disposableSchema = "ownerlab_http_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection setup = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:5432/ownerlab_disposable",
                "ownerlab_ci", password);
             var sql = setup.createStatement()) {
            sql.execute("CREATE SCHEMA " + disposableSchema);
            setup.setSchema(disposableSchema);
            sql.execute("""
                CREATE TABLE aetheris_tasks (
                  id uuid PRIMARY KEY, owner_identity_uid bigint,
                  owner_tenant_key text, owner_project_key text,
                  owner_verified_at timestamptz
                )""");
            sql.execute("""
                CREATE TABLE tenant_memberships (
                  tenant_key text, identity_uid bigint, status text,
                  revoked_at timestamptz, valid_until timestamptz
                )""");
            sql.execute("""
                CREATE TABLE project_memberships (
                  tenant_key text, project_key text, identity_uid bigint,
                  status text, revoked_at timestamptz, valid_until timestamptz
                )""");
            sql.execute("""
                INSERT INTO aetheris_tasks VALUES
                  ('22222222-2222-4222-8222-222222222222',42,'tenant-alpha','project-red',statement_timestamp()),
                  ('44444444-4444-4444-8444-444444444444',77,'tenant-alpha','project-blue',statement_timestamp()),
                  ('11111111-1111-4111-8111-111111111111',NULL,NULL,NULL,NULL)
                """);
            sql.execute("""
                INSERT INTO tenant_memberships VALUES
                  ('tenant-alpha',42,'ACTIVE',NULL,statement_timestamp() + interval '1 day'),
                  ('tenant-alpha',77,'ACTIVE',NULL,statement_timestamp() + interval '1 day')
                """);
            sql.execute("""
                INSERT INTO project_memberships VALUES
                  ('tenant-alpha','project-red',42,'ACTIVE',NULL,statement_timestamp() + interval '1 day'),
                  ('tenant-alpha','project-blue',77,'ACTIVE',NULL,statement_timestamp() + interval '1 day')
                """);
        }

        var keys = KeyPairGenerator.getInstance("RSA");
        keys.initialize(2048);
        trustedKey = keys.generateKeyPair();
        attackerKey = keys.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(trustedKey.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
        verifier = new RuntimePinnedPrincipalVerifier(
            pem, KID, ISSUER, AUDIENCE, Clock.systemUTC());

        server = new Tomcat();
        server.setBaseDir(Files.createTempDirectory("s0-http-jdbc-tomcat-").toString());
        server.setHostname("127.0.0.1");
        server.setPort(0);
        server.getConnector();
        Context context = server.addContext(
            "", Files.createTempDirectory("s0-http-jdbc-webroot-").toString());

        // Synthetic proof endpoint: absent from production source and route map.
        HttpServlet testOnlyOwnerRead = new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) {
                response.setHeader("Cache-Control", "no-store");
                response.setHeader("X-Content-Type-Options", "nosniff");
                RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal;
                try {
                    principal = verifier.verifyBearer(request.getHeader("Authorization"));
                } catch (IllegalArgumentException invalid) {
                    response.setStatus(401);
                    return;
                }
                if (!principal.hasScope("orchestrator:read")) {
                    response.setStatus(403);
                    return;
                }
                for (String name : new String[]{"tenant", "project", "task"}) {
                    String[] values = request.getParameterValues(name);
                    if (values == null || values.length != 1) {
                        response.setStatus(404);
                        return;
                    }
                }
                UUID task;
                try {
                    task = UUID.fromString(request.getParameter("task"));
                } catch (IllegalArgumentException invalid) {
                    response.setStatus(404);
                    return;
                }
                // One fresh READ COMMITTED connection per HTTP request: a
                // preexisting identity grant never caches DB membership.
                try (Connection connection = connect()) {
                    connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                    connection.setReadOnly(true);
                    connection.setAutoCommit(false);
                    boolean allowed = PROBE.canRead(connection, principal,
                        request.getParameter("tenant"),
                        request.getParameter("project"), task);
                    connection.rollback();
                    response.setStatus(allowed ? 204 : 404);
                } catch (Exception unavailable) {
                    // No SQL, schema, signed claims or customer data in response.
                    response.setStatus(503);
                }
            }
        };
        Tomcat.addServlet(context, "s0-owner-jdbc-test-only", testOnlyOwnerRead);
        context.addServletMappingDecoded("/__s0/owner-read", "s0-owner-jdbc-test-only");

        HttpServlet productionPathTrap = new HttpServlet() {
            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response) {
                BLOCKED_DISPATCHES.incrementAndGet();
                response.setStatus(200);
            }
        };
        Tomcat.addServlet(context, "s0-never-reachable-business", productionPathTrap);
        context.addServletMappingDecoded(
            "/api/orchestrator/tasks/*", "s0-never-reachable-business");

        // The exact production deny-only filters execute BEFORE test servlets.
        register(context, "s0-ownerless-filter", new OwnerlessTaskMissionHttpFilter());
        register(context, "s0-global-api-quarantine", new RuntimeUncertifiedApiHttpFilter());
        server.start();
        int port = server.getConnector().getLocalPort();
        assertTrue(port > 0);
        origin = "http://127.0.0.1:" + port;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private static void register(Context context, String name, jakarta.servlet.Filter filter) {
        FilterDef definition = new FilterDef();
        definition.setFilterName(name);
        definition.setFilter(filter);
        context.addFilterDef(definition);
        FilterMap mapping = new FilterMap();
        mapping.setFilterName(name);
        mapping.addURLPattern("/*");
        mapping.setDispatcher(DispatcherType.REQUEST.name());
        context.addFilterMap(mapping);
    }

    @AfterAll
    static void closeEverythingAndAssertNoSchemaSurvives() throws Exception {
        try {
            if (server != null) {
                try { server.stop(); } finally { server.destroy(); }
            }
        } finally {
            if (disposableSchema != null) {
                try (Connection cleanup = DriverManager.getConnection(
                        "jdbc:postgresql://127.0.0.1:5432/ownerlab_disposable",
                        "ownerlab_ci", password);
                     var sql = cleanup.createStatement()) {
                    sql.execute("DROP SCHEMA IF EXISTS " + disposableSchema + " CASCADE");
                    try (PreparedStatement check = cleanup.prepareStatement(
                            "SELECT COUNT(*) FROM pg_namespace WHERE nspname = ?")) {
                        check.setString(1, disposableSchema);
                        try (var result = check.executeQuery()) {
                            assertTrue(result.next());
                            assertEquals(0L, result.getLong(1),
                                "Disposable HTTP-to-JDBC fixture must be removed");
                        }
                    }
                }
            }
        }
    }

    private static String bearer(KeyPair signer, long uid, List<String> scopes) {
        Instant issued = Instant.now().minusSeconds(10);
        return "Bearer " + Jwts.builder().header().keyId(KID).and()
            .subject("synthetic-" + uid + "@example.invalid")
            .issuer(ISSUER).claim("aud", List.of(AUDIENCE))
            .claim("uid", uid).claim("role", "DEVELOPER")
            .claim("scopes", scopes)
            .issuedAt(Date.from(issued))
            .expiration(Date.from(issued.plusSeconds(300)))
            .signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static HttpResponse<String> send(String path, String bearer,
                                               Map<String,String> headers)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create(origin + path))
            .timeout(Duration.ofSeconds(8)).GET();
        if (bearer != null) request.header("Authorization", bearer);
        for (var h : headers.entrySet()) request.header(h.getKey(), h.getValue());
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String proofUrl(String tenant, String project, UUID task) {
        return "/__s0/owner-read?tenant=" + tenant + "&project=" + project
            + "&task=" + task;
    }

    private static void status(int expected, String path, String token,
                               Map<String,String> headers) throws Exception {
        var response = send(path, token, headers);
        assertEquals(expected, response.statusCode(), path);
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null));
        assertEquals("nosniff",
            response.headers().firstValue("X-Content-Type-Options").orElse(null));
        assertTrue(response.body().isEmpty(), "No private contents may be returned");
    }

    @Test
    void realSignedIdentityAndJdbcMembershipProtectTwoUsersAndOwnerlessData()
            throws Exception {
        String alice = bearer(trustedKey, 42, List.of("orchestrator:read"));
        String bob = bearer(trustedKey, 77, List.of("orchestrator:read"));
        String own = proofUrl("tenant-alpha", "project-red", ALICE_TASK);
        status(204, own, alice, Map.of());
        status(404, own, bob, Map.of("X-Aetheris-Owner", "42",
            "X-Aetheris-Project", "project-red",
            "X-Forwarded-Client-Cert", "spiffe://fake-owner"));
        status(404, proofUrl("tenant-alpha", "project-blue", ALICE_TASK), alice, Map.of());
        status(404, proofUrl("tenant-alpha", "project-red", LEGACY_TASK), alice, Map.of());
        status(204, proofUrl("tenant-alpha", "project-blue", BOB_TASK), bob, Map.of());
        status(401, own, null, Map.of("X-Aetheris-User-Id", "42"));
        status(401, own, bearer(attackerKey, 42, List.of("orchestrator:read")), Map.of());
        status(403, own, bearer(trustedKey, 42, List.of("services:read")), Map.of());
        status(404, "/__s0/owner-read?tenant=tenant-alpha&tenant=tenant-alpha"
            + "&project=project-red&task=" + ALICE_TASK, alice, Map.of());
    }

    @Test
    void committedTenantRevocationDeniesNextRealSignedHttpRequest()
            throws Exception {
        String alice = bearer(trustedKey, 42, List.of("orchestrator:read"));
        String own = proofUrl("tenant-alpha", "project-red", ALICE_TASK);
        status(204, own, alice, Map.of());
        try {
            tenantStatus("REVOKED");
            status(404, own, alice, Map.of());
        } finally {
            tenantStatus("ACTIVE");
        }
        status(204, own, alice, Map.of());
    }

    private static void tenantStatus(String newStatus) throws Exception {
        try (Connection writer = connect();
             PreparedStatement change = writer.prepareStatement("""
                 UPDATE tenant_memberships
                 SET status=?, revoked_at=CASE WHEN ?='REVOKED'
                     THEN statement_timestamp() ELSE NULL END
                 WHERE tenant_key='tenant-alpha' AND identity_uid=42
                 """)) {
            change.setString(1, newStatus);
            change.setString(2, newStatus);
            assertEquals(1, change.executeUpdate(), "Test fixture must update exactly one membership");
        }
    }

    @Test
    void realProductionHttpFiltersStillQuarantineBusinessApiAfterValidJwt()
            throws Exception {
        String alice = bearer(trustedKey, 42, List.of("orchestrator:read"));
        var path = "/api/orchestrator/tasks/" + ALICE_TASK;
        status(404, path, alice, Map.of("X-Aetheris-Owner", "42"));
        status(404, "/api/orchestrator/live/events", alice, Map.of());
        status(404, "/actuator/health", alice, Map.of());
        assertEquals(0, BLOCKED_DISPATCHES.get(),
            "A signed owner/DB membership never unlocks the live application API");
    }
}
