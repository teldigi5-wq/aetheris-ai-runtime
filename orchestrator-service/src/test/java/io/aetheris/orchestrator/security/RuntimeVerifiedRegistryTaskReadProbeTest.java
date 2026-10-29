package io.aetheris.orchestrator.security;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real constrained PostgreSQL16 login, actual RS256 signed tokens.
 * No mock JDBC and no role or persistent database created by this class.
 * CI provisions a fixed-name, disposable DB before running this test.
 */
class RuntimeVerifiedRegistryTaskReadProbeTest {
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String AUDIENCE = "aetheris-orchestrator-api";
    private static final String KID = "s0-restricted-read-ci";
    private static final UUID LEGACY = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ALICE = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ALICE_BETA = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID BOB = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID REVOKED = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID SUSPENDED = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID VIEWER = UUID.fromString("88888888-8888-4888-8888-888888888888");

    private static KeyPair trusted;
    private static RuntimePinnedPrincipalVerifier verifier;
    private static String dbPassword;
    private final RuntimeVerifiedRegistryTaskReadProbe probe =
        new RuntimeVerifiedRegistryTaskReadProbe();

    @BeforeAll
    static void initialize() throws Exception {
        Assumptions.assumeTrue("yes".equals(System.getenv("AETHERIS_S0_READROLE_MUST_RUN")),
            "Requires explicitly isolated PostgreSQL16 CI test");
        assertEquals("true", System.getenv("GITHUB_ACTIONS"));
        assertEquals("yes", System.getenv("AETHERIS_S0_DISPOSABLE_DB_ONLY"));
        assertEquals("127.0.0.1", System.getenv("PGHOST"));
        assertEquals("s0read_disposable", System.getenv("PGDATABASE"));
        assertEquals("s0_readonly_runtime", System.getenv("AETHERIS_S0_READROLE_USER"));
        dbPassword = System.getenv("AETHERIS_S0_READROLE_PASSWORD");
        assertNotNull(dbPassword);
        var gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        trusted = gen.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(trusted.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
        verifier = new RuntimePinnedPrincipalVerifier(
            pem, KID, ISSUER, AUDIENCE, Clock.systemUTC());
    }

    private static Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(
            "jdbc:postgresql://127.0.0.1:5432/s0read_disposable",
            "s0_readonly_runtime", dbPassword);
        try (var stmt = c.createStatement();
             var rows = stmt.executeQuery("SELECT current_user")) {
            assertTrue(rows.next());
            assertEquals("s0_readonly_runtime", rows.getString(1));
        }
        return c;
    }

    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal signed(
            KeyPair signingKey, long uid, List<String> scopes) {
        Instant now = Instant.now();
        String token = Jwts.builder().header().keyId(KID).and()
            .subject("synthetic-" + uid + "@example.invalid")
            .issuer(ISSUER).claim("aud", List.of(AUDIENCE))
            .claim("uid", uid).claim("role", "DEVELOPER")
            .claim("scopes", scopes)
            .issuedAt(Date.from(now.minusSeconds(10)))
            .expiration(Date.from(now.plusSeconds(180)))
            .signWith(signingKey.getPrivate(), Jwts.SIG.RS256).compact();
        return verifier.verifyBearer("Bearer " + token);
    }

    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal readAs(long uid) {
        return signed(trusted, uid, List.of("orchestrator:read"));
    }

    private static void readTransaction(Connection c) throws SQLException {
        c.setReadOnly(true);
        c.setAutoCommit(false);
        assertTrue(c.isReadOnly());
        assertFalse(c.getAutoCommit());
    }

    @Test
    void signedIdentityAllowsExactRegistryBoundReadsButNeverCrossTenantOrLegacy() throws Exception {
        try (Connection c = connect()) {
            readTransaction(c);
            var alice = readAs(42);
            var bob = readAs(77);
            assertTrue(probe.canRead(c, alice, "alpha", "red", ALICE));
            assertTrue(probe.canRead(c, alice, "beta", "red", ALICE_BETA));
            assertTrue(probe.canRead(c, bob, "alpha", "blue", BOB));
            assertTrue(probe.canRead(c, readAs(88), "alpha", "red", VIEWER));
            assertFalse(probe.canRead(c, bob, "alpha", "red", ALICE));
            assertFalse(probe.canRead(c, alice, "beta", "red", ALICE));
            assertFalse(probe.canRead(c, alice, "alpha", "blue", BOB));
            assertFalse(probe.canRead(c, alice, "alpha", "red", LEGACY));
            assertFalse(probe.canRead(c, readAs(99), "alpha", "red", REVOKED));
            assertFalse(probe.canRead(c, alice, "suspended", "red", SUSPENDED));
            assertFalse(probe.canRead(c, alice, "alpha", "red",
                UUID.fromString("77777777-7777-4777-8777-777777777777")));
            c.rollback();
        }
    }

    @Test
    void rejectsInvalidScopeSelectorsPrincipalAndConnectionMode() throws Exception {
        try (Connection c = connect()) {
            readTransaction(c);
            assertFalse(probe.canRead(c,
                signed(trusted, 42, List.of("services:read")), "alpha","red", ALICE));
            assertFalse(probe.canRead(c, readAs(42), "ALPHA","red", ALICE));
            assertFalse(probe.canRead(c, readAs(42), "alpha",null, ALICE));
            assertFalse(probe.canRead(c, readAs(42), "alpha","red", null));
            var attacker = KeyPairGenerator.getInstance("RSA");
            attacker.initialize(2048);
            assertThrows(IllegalArgumentException.class, () ->
                signed(attacker.generateKeyPair(),42,List.of("orchestrator:read")));
            c.rollback();
        }
        try (Connection c = connect()) {
            assertThrows(IllegalStateException.class, () ->
                probe.canRead(c,readAs(42),"alpha","red",ALICE));
        }
    }

    private static void expectNoPrivilege(String sql) throws Exception {
        // Fresh connection each time; SQLSTATE 42501 aborts its transaction.
        try (Connection c = connect(); var statement = c.createStatement()) {
            SQLException rejected = assertThrows(SQLException.class,
                () -> statement.execute(sql));
            assertEquals("42501", rejected.getSQLState(), sql);
        }
    }

    @Test
    void actualDatabaseRoleDeniesPayloadAccessAndEveryMutationOrDdl() throws Exception {
        expectNoPrivilege("SELECT title FROM aetheris_tasks LIMIT 1");
        expectNoPrivilege("SELECT command_text FROM aetheris_tasks LIMIT 1");
        expectNoPrivilege("SELECT * FROM aetheris_tasks LIMIT 1");
        expectNoPrivilege("UPDATE aetheris_tasks SET state='PLANNING' "
            + "WHERE id='22222222-2222-4222-8222-222222222222'");
        expectNoPrivilege("DELETE FROM aetheris_tasks WHERE id="
            + "'22222222-2222-4222-8222-222222222222'");
        expectNoPrivilege("TRUNCATE aetheris_tasks");
        expectNoPrivilege("UPDATE registry_tenant_memberships SET status='REVOKED' "
            + "WHERE tenant_key='alpha' AND identity_uid=42");
        expectNoPrivilege("INSERT INTO registry_tenants(tenant_key,status) "
            + "VALUES ('intruder','ACTIVE')");
        expectNoPrivilege("CREATE TABLE public.s0_privilege_escape(id bigint)");
        expectNoPrivilege("ALTER TABLE aetheris_tasks ADD COLUMN s0_intrusion integer");
        expectNoPrivilege("SET ROLE s0_owner_ci");
        try (Connection c = connect();
             var statement = c.createStatement();
             var privileges = statement.executeQuery("""
                 SELECT has_database_privilege(current_user, current_database(), 'CREATE'),
                        has_table_privilege(current_user, 'aetheris_tasks', 'UPDATE'),
                        has_column_privilege(current_user,'aetheris_tasks','command_text','SELECT')
                 """)) {
            assertTrue(privileges.next());
            assertFalse(privileges.getBoolean(1));
            assertFalse(privileges.getBoolean(2));
            assertFalse(privileges.getBoolean(3));
        }
    }
}
