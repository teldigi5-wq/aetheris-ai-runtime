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
 * Real PostgreSQL16 FORCE RLS / two *separate* authenticated DB login roles,
 * plus actual ephemeral RS256 signed Identity JWTs. Never runs outside the
 * fixed disposable GitHub Actions environment. Not a credential provisioning
 * design for real Aetheris users, and not a Spring/HTTP endpoint.
 */
class RuntimeVerifiedLoginBoundRlsReadProbeTest {
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String AUDIENCE = "aetheris-orchestrator-api";
    private static final String KID = "ci-rls-login-bound";
    private static final UUID HISTORIC = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ALICE_ALPHA = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ALICE_BETA = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID BOB = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID REVOKED = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID SUSPENDED = UUID.fromString("66666666-6666-4666-8666-666666666666");

    private static final String DB = "jdbc:postgresql://127.0.0.1:5432/s0rls_disposable";
    private static KeyPair trustedKey;
    private static RuntimePinnedPrincipalVerifier verifier;
    private static String ownerPassword;
    private final RuntimeVerifiedLoginBoundRlsReadProbe probe =
        new RuntimeVerifiedLoginBoundRlsReadProbe();

    @BeforeAll
    static void setup() throws Exception {
        Assumptions.assumeTrue("yes".equals(System.getenv("AETHERIS_S0_RLS_MUST_RUN")),
            "Only in explicitly mandatory disposable PostgreSQL16 CI");
        assertEquals("true", System.getenv("GITHUB_ACTIONS"));
        assertEquals("yes", System.getenv("AETHERIS_S0_RLS_DISPOSABLE_ONLY"));
        assertEquals("127.0.0.1", System.getenv("PGHOST"));
        assertEquals("s0rls_disposable", System.getenv("PGDATABASE"));
        assertEquals("s0_owner_ci", System.getenv("PGUSER"));
        ownerPassword = System.getenv("PGPASSWORD");
        assertNotNull(ownerPassword);
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        trustedKey = generator.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(trustedKey.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
        verifier = new RuntimePinnedPrincipalVerifier(pem, KID, ISSUER, AUDIENCE, Clock.systemUTC());
    }

    private static Connection login(String role) throws SQLException {
        String password = switch (role) {
            case "s0_rls_alice" -> "synthetic_ci_alice_not_a_real_secret";
            case "s0_rls_bob" -> "synthetic_ci_bob_not_a_real_secret";
            default -> throw new IllegalArgumentException("Non-test RLS role forbidden");
        };
        Connection connection = DriverManager.getConnection(DB, role, password);
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT session_user,current_user")) {
            assertTrue(result.next());
            assertEquals(role, result.getString(1));
            assertEquals(role, result.getString(2));
        }
        return connection;
    }

    private static Connection admin() throws SQLException {
        return DriverManager.getConnection(DB, "s0_owner_ci", ownerPassword);
    }

    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal signed(
            KeyPair signingKey, long identityUid, List<String> scopes) {
        Instant now = Instant.now();
        String token = Jwts.builder().header().keyId(KID).and()
            .subject("s0-synthetic-" + identityUid + "@example.invalid")
            .issuer(ISSUER).claim("aud", List.of(AUDIENCE))
            .claim("uid", identityUid).claim("role", "DEVELOPER")
            .claim("scopes", scopes)
            .issuedAt(Date.from(now.minusSeconds(10)))
            .expiration(Date.from(now.plusSeconds(180)))
            .signWith(signingKey.getPrivate(), Jwts.SIG.RS256).compact();
        return verifier.verifyBearer("Bearer " + token);
    }

    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal readAs(long uid) {
        return signed(trustedKey, uid, List.of("orchestrator:read"));
    }

    private static void readonly(Connection connection) throws SQLException {
        connection.setReadOnly(true);
        connection.setAutoCommit(false);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED,
            connection.getTransactionIsolation());
    }

    private static int directlyVisibleTaskCount(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM public.aetheris_tasks")) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static void assertForbidden(String role, String sql) throws Exception {
        try (Connection connection = login(role);
             var statement = connection.createStatement()) {
            SQLException denied = assertThrows(SQLException.class,
                () -> statement.execute(sql));
            assertEquals("42501", denied.getSQLState(), sql);
        }
    }

    @Test
    void signedUidAndIndependentDbSessionBothMustMatch() throws Exception {
        try (Connection alice = login("s0_rls_alice");
             Connection bob = login("s0_rls_bob")) {
            readonly(alice);
            readonly(bob);
            assertTrue(probe.canRead(alice, readAs(42), "alpha","red",ALICE_ALPHA));
            assertTrue(probe.canRead(alice, readAs(42), "beta","red",ALICE_BETA));
            assertTrue(probe.canRead(bob, readAs(77), "alpha","blue",BOB));
            assertFalse(probe.canRead(alice, readAs(77), "alpha","blue",BOB));
            assertFalse(probe.canRead(bob, readAs(42), "alpha","red",ALICE_ALPHA));
            assertFalse(probe.canRead(alice, readAs(42), "alpha","blue",BOB));
            assertFalse(probe.canRead(alice, readAs(42), "beta","red",ALICE_ALPHA));
            assertFalse(probe.canRead(alice, readAs(42), "alpha","red",HISTORIC));
            assertFalse(probe.canRead(alice, readAs(42), "suspended","red",SUSPENDED));
            assertFalse(probe.canRead(bob, readAs(77), "alpha","red",REVOKED));
            assertFalse(probe.canRead(alice, readAs(42), "alpha","red",
                UUID.fromString("77777777-7777-4777-8777-777777777777")));
            alice.rollback();
            bob.rollback();
        }
    }

    @Test
    void actualDirectSqlMetadataQueriesAreRestrictedByForceRlsNotOnlyJava() throws Exception {
        try (Connection alice = login("s0_rls_alice");
             Connection bob = login("s0_rls_bob")) {
            readonly(alice);
            readonly(bob);
            assertEquals(2, directlyVisibleTaskCount(alice));
            assertEquals(1, directlyVisibleTaskCount(bob));
            try (var statement = alice.createStatement();
                 var result = statement.executeQuery(
                     "SELECT count(*) FROM public.aetheris_tasks WHERE owner_identity_uid=77")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
            try (var statement = bob.createStatement();
                 var result = statement.executeQuery(
                     "SELECT count(*) FROM public.aetheris_tasks WHERE owner_identity_uid=42")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
            alice.rollback();
            bob.rollback();
        }
        assertForbidden("s0_rls_alice", "SELECT title FROM public.aetheris_tasks");
        assertForbidden("s0_rls_alice", "SELECT command_text FROM public.aetheris_tasks");
        assertForbidden("s0_rls_bob", "SELECT * FROM public.aetheris_tasks");
        assertForbidden("s0_rls_bob", "SELECT * FROM public.s0_rls_login_identity");
        assertForbidden("s0_rls_alice", "SELECT * FROM public.registry_tenant_memberships");
        assertForbidden("s0_rls_bob", "UPDATE public.aetheris_tasks SET state='PLANNING'");
        assertForbidden("s0_rls_alice", "UPDATE public.registry_tenant_memberships SET status='REVOKED'");
        assertForbidden("s0_rls_bob", "CREATE TABLE public.s0_ci_escape(id bigint)");
        assertForbidden("s0_rls_alice", "SET ROLE s0_owner_ci");
        assertForbidden("s0_rls_bob", "SET ROLE s0_rls_alice");
        assertForbidden("s0_rls_bob", "SET SESSION AUTHORIZATION s0_rls_alice");
    }

    @Test
    void spoofedMutableGucsDoNotChangeDbAuthenticatedOwnership() throws Exception {
        try (Connection bob = login("s0_rls_bob")) {
            try (var statement = bob.createStatement()) {
                statement.execute("SELECT set_config('app.identity_uid','42',false)");
                statement.execute("SELECT set_config('request.jwt.claim.uid','42',false)");
                statement.execute("SELECT set_config('app.tenant_key','beta',false)");
            }
            readonly(bob);
            assertEquals(1, directlyVisibleTaskCount(bob));
            assertFalse(probe.canRead(bob, readAs(42), "alpha","red",ALICE_ALPHA));
            assertTrue(probe.canRead(bob, readAs(77), "alpha","blue",BOB));
            bob.rollback();
        }
    }

    @Test
    void invalidScopesInvalidSessionAndForgedSignatureFailClosed() throws Exception {
        try (Connection alice = login("s0_rls_alice")) {
            readonly(alice);
            assertFalse(probe.canRead(alice,
                signed(trustedKey,42,List.of("services:read")),
                "alpha","red",ALICE_ALPHA));
            assertFalse(probe.canRead(alice,readAs(42),"ALPHA","red",ALICE_ALPHA));
            assertFalse(probe.canRead(alice,readAs(42),"alpha",null,ALICE_ALPHA));
            assertFalse(probe.canRead(alice,readAs(42),"alpha","red",null));
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair adversary = generator.generateKeyPair();
            assertThrows(IllegalArgumentException.class,
                () -> signed(adversary,42,List.of("orchestrator:read")));
            alice.rollback();
        }
        try (Connection alice = login("s0_rls_alice")) {
            assertThrows(IllegalStateException.class, () ->
                probe.canRead(alice,readAs(42),"alpha","red",ALICE_ALPHA));
        }
    }

    @Test
    void committedMembershipRevocationRemovesDbVisibleTaskMetadata() throws Exception {
        try (Connection owner = admin()) {
            try {
                try (var statement = owner.createStatement()) {
                    assertEquals(1, statement.executeUpdate("""
                        UPDATE public.registry_tenant_memberships
                        SET status='REVOKED', revoked_at=now(), version=version+1
                        WHERE tenant_key='alpha' AND identity_uid=42
                        """));
                }
                try (Connection alice = login("s0_rls_alice")) {
                    readonly(alice);
                    assertEquals(1, directlyVisibleTaskCount(alice));
                    assertFalse(probe.canRead(alice,readAs(42),"alpha","red",ALICE_ALPHA));
                    assertTrue(probe.canRead(alice,readAs(42),"beta","red",ALICE_BETA));
                    alice.rollback();
                }
            } finally {
                try (var statement = owner.createStatement()) {
                    statement.executeUpdate("""
                        UPDATE public.registry_tenant_memberships
                        SET status='ACTIVE', revoked_at=NULL, version=version+1
                        WHERE tenant_key='alpha' AND identity_uid=42
                        """);
                }
            }
        }
    }
}
