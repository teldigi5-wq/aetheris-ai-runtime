package io.aetheris.orchestrator.security;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Savepoint;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real PostgreSQL JDBC + actual RS256 signed tokens. Runs ONLY under a
 * separate mandatory disposable GitHub CI opt-in with hard-pinned localhost
 * DB details; normal Maven regression intentionally skips this suite.
 *
 * All created SQL objects and rows are TEMP and transaction-rolled-back.
 */
class RuntimeVerifiedTaskReadProbeTest {
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String AUDIENCE = "aetheris-orchestrator-api";
    private static final String KEY_ID = "ci-disposable-jdbc-identity";
    private static final UUID LEGACY = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ALICE_ALPHA = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ALICE_BETA = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID BOB_ALPHA = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID EVE_REVOKED = UUID.fromString("55555555-5555-4555-8555-555555555555");

    private static Connection connection;
    private static KeyPair signingKey;
    private static RuntimePinnedPrincipalVerifier verifier;
    private final RuntimeVerifiedTaskReadProbe probe = new RuntimeVerifiedTaskReadProbe();

    @BeforeAll
    static void initialize() throws Exception {
        boolean required = "yes".equals(System.getenv("AETHERIS_MEMBERSHIP_JDBC_MUST_RUN"));
        Assumptions.assumeTrue(required,
            "Real JDBC test runs only in explicit disposable PostgreSQL16 CI");
        assertEquals("true", System.getenv("GITHUB_ACTIONS"));
        assertEquals("yes", System.getenv("AETHERIS_OWNER_SCHEMA_DISPOSABLE_CI_ONLY"));
        assertEquals("127.0.0.1", System.getenv("PGHOST"));
        assertEquals("ownerlab_disposable", System.getenv("PGDATABASE"));
        assertEquals("ownerlab_ci", System.getenv("PGUSER"));
        String password = System.getenv("PGPASSWORD");
        assertNotNull(password);
        connection = DriverManager.getConnection(
            "jdbc:postgresql://127.0.0.1:5432/ownerlab_disposable",
            "ownerlab_ci", password);
        connection.setAutoCommit(false);
        try (var sql = connection.createStatement()) {
            sql.execute("""
                CREATE TEMP TABLE aetheris_tasks (
                  id uuid PRIMARY KEY,
                  owner_identity_uid bigint,
                  owner_tenant_key varchar(96),
                  owner_project_key varchar(96),
                  owner_verified_at timestamptz
                )
                """);
            sql.execute("""
                CREATE TEMP TABLE tenant_memberships (
                  tenant_key varchar(96) NOT NULL,
                  identity_uid bigint NOT NULL CHECK (identity_uid > 0),
                  status varchar(16) NOT NULL CHECK (status IN ('ACTIVE','REVOKED')),
                  revoked_at timestamptz,
                  valid_until timestamptz,
                  PRIMARY KEY (tenant_key,identity_uid)
                )
                """);
            sql.execute("""
                CREATE TEMP TABLE project_memberships (
                  tenant_key varchar(96) NOT NULL,
                  project_key varchar(96) NOT NULL,
                  identity_uid bigint NOT NULL CHECK (identity_uid > 0),
                  status varchar(16) NOT NULL CHECK (status IN ('ACTIVE','REVOKED')),
                  revoked_at timestamptz,
                  valid_until timestamptz,
                  PRIMARY KEY (tenant_key,project_key,identity_uid),
                  FOREIGN KEY (tenant_key,identity_uid)
                    REFERENCES tenant_memberships (tenant_key,identity_uid)
                )
                """);
            sql.executeUpdate("""
                INSERT INTO tenant_memberships
                  (tenant_key,identity_uid,status,revoked_at,valid_until) VALUES
                  ('tenant-alpha',42,'ACTIVE',NULL,now()+interval '1 day'),
                  ('tenant-beta',42,'ACTIVE',NULL,now()+interval '1 day'),
                  ('tenant-alpha',77,'ACTIVE',NULL,now()+interval '1 day'),
                  ('tenant-alpha',99,'REVOKED',now(),NULL)
                """);
            sql.executeUpdate("""
                INSERT INTO project_memberships
                  (tenant_key,project_key,identity_uid,status,revoked_at,valid_until) VALUES
                  ('tenant-alpha','project-red',42,'ACTIVE',NULL,now()+interval '1 day'),
                  ('tenant-beta','project-red',42,'ACTIVE',NULL,now()+interval '1 day'),
                  ('tenant-alpha','project-blue',77,'ACTIVE',NULL,now()+interval '1 day'),
                  ('tenant-alpha','project-red',99,'REVOKED',now(),NULL)
                """);
            sql.executeUpdate("""
                INSERT INTO aetheris_tasks
                  (id,owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at) VALUES
                  ('11111111-1111-4111-8111-111111111111',NULL,NULL,NULL,NULL),
                  ('22222222-2222-4222-8222-222222222222',42,'tenant-alpha','project-red',now()),
                  ('33333333-3333-4333-8333-333333333333',42,'tenant-beta','project-red',now()),
                  ('44444444-4444-4444-8444-444444444444',77,'tenant-alpha','project-blue',now()),
                  ('55555555-5555-4555-8555-555555555555',99,'tenant-alpha','project-red',now())
                """);
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        signingKey = generator.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(signingKey.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
        verifier = new RuntimePinnedPrincipalVerifier(pem, KEY_ID, ISSUER,
            AUDIENCE, Clock.systemUTC());
    }

    @AfterAll
    static void rollbackAndClose() throws Exception {
        if (connection != null) {
            try { connection.rollback(); } finally { connection.close(); }
        }
    }

    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal signed(
            long uid, List<String> scopes) {
        Instant now = Instant.now();
        String jwt = Jwts.builder().header().keyId(KEY_ID).and()
            .subject("synthetic-user-" + uid + "@example.invalid")
            .issuer(ISSUER).claim("aud", List.of(AUDIENCE))
            .claim("uid", uid).claim("role", "DEVELOPER")
            .claim("scopes", scopes)
            .issuedAt(Date.from(now.minusSeconds(10)))
            .expiration(Date.from(now.plusSeconds(180)))
            .signWith(signingKey.getPrivate(), Jwts.SIG.RS256).compact();
        return verifier.verifyBearer("Bearer " + jwt);
    }

    private boolean allowed(long uid, String tenant, String project, UUID id) throws Exception {
        return probe.canRead(connection, signed(uid, List.of("orchestrator:read")),
            tenant, project, id);
    }

    @Test
    void explicitJdbcTransactionAndActualPrimaryRequiredForOwnerReads()
            throws Exception {
        // A signed owner does not make JDBC autocommit a reviewable
        // authorization transaction; reject it before SQL is prepared.
        try (Connection autocommit = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:5432/ownerlab_disposable",
                "ownerlab_ci", System.getenv("PGPASSWORD"))) {
            assertTrue(autocommit.getAutoCommit());
            var owner = signed(42, List.of("orchestrator:read"));
            var denial = assertThrows(IllegalStateException.class,
                () -> probe.canRead(autocommit, owner,
                    "tenant-alpha", "project-red", ALICE_ALPHA));
            assertEquals("Explicit JDBC transaction required",
                denial.getMessage());
        }
        // This confirms the positive test uses a real PostgreSQL primary;
        // only the query's recovery guard is responsible for standby denial.
        try (var sql = connection.createStatement();
             var primary = sql.executeQuery("SELECT NOT pg_is_in_recovery()")) {
            assertTrue(primary.next());
            assertTrue(primary.getBoolean(1));
        }
        assertTrue(allowed(42, "tenant-alpha", "project-red", ALICE_ALPHA));
    }

    @Test
    void exactSignedUidAndLiveMembershipIsNeededAcrossTwoTenants() throws Exception {
        assertTrue(allowed(42,"tenant-alpha","project-red",ALICE_ALPHA));
        assertTrue(allowed(42,"tenant-beta","project-red",ALICE_BETA));
        assertTrue(allowed(77,"tenant-alpha","project-blue",BOB_ALPHA));
        assertFalse(allowed(77,"tenant-alpha","project-red",ALICE_ALPHA));
        assertFalse(allowed(42,"tenant-alpha","project-blue",BOB_ALPHA));
        assertFalse(allowed(42,"tenant-beta","project-red",ALICE_ALPHA));
        assertFalse(allowed(42,"tenant-alpha","project-red",ALICE_BETA));
        assertFalse(allowed(42,"tenant-alpha","project-red",LEGACY));
        assertFalse(allowed(99,"tenant-alpha","project-red",EVE_REVOKED));
    }

    @Test
    void missingOrForgedSelectorsAndSignedTokenWithoutReadScopeFailClosed()
            throws Exception {
        assertFalse(allowed(42,"TENANT-ALPHA","project-red",ALICE_ALPHA));
        assertFalse(allowed(42,"tenant-alpha","PROJECT-RED",ALICE_ALPHA));
        assertFalse(allowed(42,"tenant-other","project-red",ALICE_ALPHA));
        assertFalse(allowed(42,null,"project-red",ALICE_ALPHA));
        assertFalse(allowed(42,"tenant-alpha",null,ALICE_ALPHA));
        assertFalse(probe.canRead(connection,
            signed(42,List.of("services:read")),
            "tenant-alpha","project-red",ALICE_ALPHA));
        assertFalse(probe.canRead(connection,
            signed(42,List.of("orchestrator:read")),
            "tenant-alpha","project-red",null));
    }

    @Test
    void membershipRevocationAndExpiryDenyNextParameterizedRead()
            throws Exception {
        Savepoint restore = connection.setSavepoint("revoke_test");
        try {
            assertTrue(allowed(42,"tenant-alpha","project-red",ALICE_ALPHA));
            try (var sql = connection.createStatement()) {
                sql.executeUpdate("""
                    UPDATE tenant_memberships SET status='REVOKED',revoked_at=now()
                    WHERE tenant_key='tenant-alpha' AND identity_uid=42
                    """);
            }
            assertFalse(allowed(42,"tenant-alpha","project-red",ALICE_ALPHA));
            assertTrue(allowed(42,"tenant-beta","project-red",ALICE_BETA));
        } finally {
            connection.rollback(restore);
        }
        Savepoint projectRestore = connection.setSavepoint("project_revoke_test");
        try {
            try (var sql = connection.createStatement()) {
                sql.executeUpdate("""
                    UPDATE project_memberships SET status='REVOKED',revoked_at=now()
                    WHERE tenant_key='tenant-alpha' AND project_key='project-red'
                      AND identity_uid=42
                    """);
            }
            assertFalse(allowed(42,"tenant-alpha","project-red",ALICE_ALPHA));
        } finally {
            connection.rollback(projectRestore);
        }
        Savepoint expiryRestore = connection.setSavepoint("expiry_test");
        try {
            try (var sql = connection.createStatement()) {
                sql.executeUpdate("""
                    UPDATE project_memberships SET valid_until=now()-interval '1 minute'
                    WHERE tenant_key='tenant-alpha' AND project_key='project-red'
                      AND identity_uid=42
                    """);
            }
            assertFalse(allowed(42,"tenant-alpha","project-red",ALICE_ALPHA));
        } finally {
            connection.rollback(expiryRestore);
        }
    }

    @Test
    void membershipExpiryAdvancesWithinOneOpenTransaction() throws Exception {
        // PostgreSQL CURRENT_TIMESTAMP is fixed at transaction start. Both
        // tenant and project permissions must expire on later reads, even
        // when the JDBC transaction has remained open the entire time.
        Savepoint tenantRestore = connection.setSavepoint("tenant_elapsed_expiry");
        try {
            try (var sql = connection.createStatement()) {
                assertEquals(1, sql.executeUpdate("""
                    UPDATE tenant_memberships
                    SET valid_until = statement_timestamp() + interval '2 seconds'
                    WHERE tenant_key='tenant-alpha' AND identity_uid=42
                    """));
            }
            assertTrue(allowed(42,"tenant-alpha","project-red",ALICE_ALPHA));
            Thread.sleep(2300);
            assertFalse(allowed(42,"tenant-alpha","project-red",ALICE_ALPHA),
                "Expired tenant membership must not be accepted by an old transaction clock");
        } finally {
            connection.rollback(tenantRestore);
        }

        Savepoint projectRestore = connection.setSavepoint("project_elapsed_expiry");
        try {
            try (var sql = connection.createStatement()) {
                assertEquals(1, sql.executeUpdate("""
                    UPDATE project_memberships
                    SET valid_until = statement_timestamp() + interval '2 seconds'
                    WHERE tenant_key='tenant-alpha'
                      AND project_key='project-red' AND identity_uid=42
                    """));
            }
            assertTrue(allowed(42,"tenant-alpha","project-red",ALICE_ALPHA));
            Thread.sleep(2300);
            assertFalse(allowed(42,"tenant-alpha","project-red",ALICE_ALPHA),
                "Expired project membership must not be accepted by an old transaction clock");
        } finally {
            connection.rollback(projectRestore);
        }
    }

    @Test
    void matchingMembershipMustRejectStaleTransactionIsolation() throws Exception {
        // Real PG16 sessions contain valid matching task and membership rows.
        // A stale-snapshot denial cannot pass merely because a row is absent.
        assertTrue(ownerReadAtIsolation(Connection.TRANSACTION_READ_COMMITTED));
        assertFalse(ownerReadAtIsolation(Connection.TRANSACTION_REPEATABLE_READ));
        assertFalse(ownerReadAtIsolation(Connection.TRANSACTION_SERIALIZABLE));
        // PG treats this as READ COMMITTED internally, but policy is exact.
        assertFalse(ownerReadAtIsolation(Connection.TRANSACTION_READ_UNCOMMITTED));
    }

    private boolean ownerReadAtIsolation(int isolation) throws Exception {
        try (Connection other = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:5432/ownerlab_disposable",
                "ownerlab_ci", System.getenv("PGPASSWORD"))) {
            other.setTransactionIsolation(isolation);
            other.setAutoCommit(false);
            try {
                try (var sql = other.createStatement()) {
                    sql.execute("""
                        CREATE TEMP TABLE aetheris_tasks AS
                        SELECT '22222222-2222-4222-8222-222222222222'::uuid AS id,
                               42::bigint AS owner_identity_uid,
                               'tenant-alpha'::text AS owner_tenant_key,
                               'project-red'::text AS owner_project_key,
                               statement_timestamp() AS owner_verified_at
                        """);
                    sql.execute("""
                        CREATE TEMP TABLE tenant_memberships AS
                        SELECT 'tenant-alpha'::text AS tenant_key,
                               42::bigint AS identity_uid,
                               'ACTIVE'::text AS status,
                               NULL::timestamptz AS revoked_at,
                               statement_timestamp() + interval '1 day' AS valid_until
                        """);
                    sql.execute("""
                        CREATE TEMP TABLE project_memberships AS
                        SELECT 'tenant-alpha'::text AS tenant_key,
                               'project-red'::text AS project_key,
                               42::bigint AS identity_uid,
                               'ACTIVE'::text AS status,
                               NULL::timestamptz AS revoked_at,
                               statement_timestamp() + interval '1 day' AS valid_until
                        """);
                }
                try (var sql = other.createStatement();
                     var settings = sql.executeQuery("SHOW transaction_isolation")) {
                    assertTrue(settings.next());
                    String expected = switch (isolation) {
                        case Connection.TRANSACTION_READ_COMMITTED -> "read committed";
                        case Connection.TRANSACTION_REPEATABLE_READ -> "repeatable read";
                        case Connection.TRANSACTION_SERIALIZABLE -> "serializable";
                        case Connection.TRANSACTION_READ_UNCOMMITTED -> "read uncommitted";
                        default -> throw new IllegalArgumentException("Unsupported test isolation");
                    };
                    assertEquals(expected, settings.getString(1));
                }
                return probe.canRead(other, signed(42, List.of("orchestrator:read")),
                    "tenant-alpha", "project-red", ALICE_ALPHA);
            } finally {
                other.rollback(); // TEMP objects and records remain disposable.
            }
        }
    }

    @Test
    void committedExternalMembershipRevocationIsSeenByOpenReaderTransaction()
            throws Exception {
        // PostgreSQL TEMP tables are session-local and cannot prove a
        // cross-connection revocation. Use a uniquely named, short-lived
        // schema ONLY in the disposable CI database. Drop it in finally.
        String schema = "ownerlab_xconn_" + UUID.randomUUID().toString().replace("-", "");
        String password = System.getenv("PGPASSWORD");
        try {
            try (Connection setup = DriverManager.getConnection(
                    "jdbc:postgresql://127.0.0.1:5432/ownerlab_disposable",
                    "ownerlab_ci", password)) {
                try (var ddl = setup.createStatement()) {
                    ddl.execute("CREATE SCHEMA " + schema);
                }
                setup.setSchema(schema);
                try (var ddl = setup.createStatement()) {
                    ddl.execute("""
                        CREATE TABLE aetheris_tasks AS
                        SELECT '22222222-2222-4222-8222-222222222222'::uuid AS id,
                               42::bigint AS owner_identity_uid,
                               'tenant-alpha'::text AS owner_tenant_key,
                               'project-red'::text AS owner_project_key,
                               statement_timestamp() AS owner_verified_at
                        """);
                    ddl.execute("""
                        CREATE TABLE tenant_memberships AS
                        SELECT 'tenant-alpha'::text AS tenant_key,
                               42::bigint AS identity_uid,
                               'ACTIVE'::text AS status,
                               NULL::timestamptz AS revoked_at,
                               statement_timestamp() + interval '1 day' AS valid_until
                        """);
                    ddl.execute("""
                        CREATE TABLE project_memberships AS
                        SELECT 'tenant-alpha'::text AS tenant_key,
                               'project-red'::text AS project_key,
                               42::bigint AS identity_uid,
                               'ACTIVE'::text AS status,
                               NULL::timestamptz AS revoked_at,
                               statement_timestamp() + interval '1 day' AS valid_until
                        """);
                }
            }

            try (Connection reader = DriverManager.getConnection(
                     "jdbc:postgresql://127.0.0.1:5432/ownerlab_disposable",
                     "ownerlab_ci", password);
                 Connection writer = DriverManager.getConnection(
                     "jdbc:postgresql://127.0.0.1:5432/ownerlab_disposable",
                     "ownerlab_ci", password)) {
                reader.setSchema(schema);
                writer.setSchema(schema);
                reader.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                reader.setAutoCommit(false);
                writer.setAutoCommit(false);
                var alice = signed(42, List.of("orchestrator:read"));
                var bob = signed(77, List.of("orchestrator:read"));
                try {
                    assertTrue(probe.canRead(reader, alice,
                        "tenant-alpha", "project-red", ALICE_ALPHA));
                    assertFalse(probe.canRead(reader, bob,
                        "tenant-alpha", "project-red", ALICE_ALPHA));

                    try (var update = writer.prepareStatement("""
                        UPDATE tenant_memberships
                        SET status='REVOKED', revoked_at=statement_timestamp()
                        WHERE tenant_key='tenant-alpha' AND identity_uid=42
                        """)) {
                        assertEquals(1, update.executeUpdate());
                    }
                    assertTrue(probe.canRead(reader, alice,
                        "tenant-alpha", "project-red", ALICE_ALPHA),
                        "Uncommitted changes by another connection must not leak");
                    writer.commit();
                    assertFalse(probe.canRead(reader, alice,
                        "tenant-alpha", "project-red", ALICE_ALPHA),
                        "A committed tenant revocation must deny the next read");
                    try (var restore = writer.prepareStatement("""
                        UPDATE tenant_memberships
                        SET status='ACTIVE', revoked_at=NULL
                        WHERE tenant_key='tenant-alpha' AND identity_uid=42
                        """)) {
                        assertEquals(1, restore.executeUpdate());
                    }
                    writer.commit();
                    assertTrue(probe.canRead(reader, alice,
                        "tenant-alpha", "project-red", ALICE_ALPHA),
                        "A newly committed tenant membership must be visible");

                    try (var update = writer.prepareStatement("""
                        UPDATE project_memberships
                        SET status='REVOKED', revoked_at=statement_timestamp()
                        WHERE tenant_key='tenant-alpha'
                          AND project_key='project-red' AND identity_uid=42
                        """)) {
                        assertEquals(1, update.executeUpdate());
                    }
                    assertTrue(probe.canRead(reader, alice,
                        "tenant-alpha", "project-red", ALICE_ALPHA),
                        "Uncommitted project revocation must not leak");
                    writer.commit();
                    assertFalse(probe.canRead(reader, alice,
                        "tenant-alpha", "project-red", ALICE_ALPHA),
                        "A committed project revocation must deny the next read");
                    try (var restore = writer.prepareStatement("""
                        UPDATE project_memberships
                        SET status='ACTIVE', revoked_at=NULL
                        WHERE tenant_key='tenant-alpha'
                          AND project_key='project-red' AND identity_uid=42
                        """)) {
                        assertEquals(1, restore.executeUpdate());
                    }
                    writer.commit();
                    assertTrue(probe.canRead(reader, alice,
                        "tenant-alpha", "project-red", ALICE_ALPHA),
                        "The reader must see the latest committed project grant");
                } finally {
                    writer.rollback();
                    reader.rollback();
                }
            }
        } finally {
            // CI-only short-lived shared tables are never left behind.
            // The schema identifier is generated locally from UUID hex,
            // never derived from untrusted user or model input.
            try (Connection cleanup = DriverManager.getConnection(
                    "jdbc:postgresql://127.0.0.1:5432/ownerlab_disposable",
                    "ownerlab_ci", password)) {
                try (var drop = cleanup.createStatement()) {
                    drop.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                }
                try (var check = cleanup.prepareStatement(
                        "SELECT COUNT(*) FROM pg_namespace WHERE nspname = ?")) {
                    check.setString(1, schema);
                    try (var result = check.executeQuery()) {
                        assertTrue(result.next());
                        assertEquals(0L, result.getLong(1),
                            "No short-lived revocation schema may survive cleanup");
                    }
                }
            }
        }
    }

    @Test
    void cachedPrincipalExpiresBeforeAnotherRealJdbcTaskRead() throws Exception {
        // The identity was verified only once; reusing the same sealed Java
        // object after signed JWT expiry must deny the NEXT database read.
        Instant issued = Instant.now();
        AtomicReference<Instant> trustedNow = new AtomicReference<>(issued);
        Clock advancing = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) {
                return Clock.fixed(trustedNow.get(), zone);
            }
            @Override public Instant instant() { return trustedNow.get(); }
        };
        String pem = "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(signingKey.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
        var localVerifier = new RuntimePinnedPrincipalVerifier(
            pem, KEY_ID, ISSUER, AUDIENCE, advancing);
        String jwt = Jwts.builder().header().keyId(KEY_ID).and()
            .subject("synthetic-owner-42@example.invalid").issuer(ISSUER)
            .claim("aud", List.of(AUDIENCE)).claim("uid", 42L)
            .claim("role", "DEVELOPER")
            .claim("scopes", List.of("orchestrator:read"))
            .issuedAt(Date.from(issued.minusSeconds(10)))
            .expiration(Date.from(issued.plusSeconds(75)))
            .signWith(signingKey.getPrivate(), Jwts.SIG.RS256).compact();
        var retained = localVerifier.verifyBearer("Bearer " + jwt);
        assertTrue(probe.canRead(connection, retained,
            "tenant-alpha", "project-red", ALICE_ALPHA));
        trustedNow.set(issued.plusSeconds(76));
        assertFalse(retained.hasScope("orchestrator:read"));
        assertFalse(probe.canRead(connection, retained,
            "tenant-alpha", "project-red", ALICE_ALPHA),
            "An expired retained principal must not authorize a fresh SQL read");
    }

    @Test
    void verifiedPrincipalCannotBeManufacturedWithPublicJavaConstructor() {
        assertEquals(0, RuntimePinnedPrincipalVerifier.VerifiedPrincipal.class
            .getConstructors().length);
        assertFalse(RuntimePinnedPrincipalVerifier.VerifiedPrincipal.class.isRecord());
    }

    @Test
    void invalidSignatureCannotBecomePrincipalForMembershipQuery()
            throws Exception {
        var other = KeyPairGenerator.getInstance("RSA");
        other.initialize(2048);
        KeyPair attackerKey = other.generateKeyPair();
        Instant now = Instant.now();
        String forged = Jwts.builder().header().keyId(KEY_ID).and()
            .subject("synthetic-user-42@example.invalid")
            .issuer(ISSUER).claim("aud", List.of(AUDIENCE))
            .claim("uid", 42).claim("role","DEVELOPER")
            .claim("scopes",List.of("orchestrator:read"))
            .issuedAt(Date.from(now.minusSeconds(10)))
            .expiration(Date.from(now.plusSeconds(180)))
            .signWith(attackerKey.getPrivate(),Jwts.SIG.RS256).compact();
        assertThrows(IllegalArgumentException.class,
            () -> verifier.verifyBearer("Bearer " + forged));
        assertTrue(allowed(42,"tenant-alpha","project-red",ALICE_ALPHA));
    }
}
