package io.aetheris.orchestrator.security;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual two-connection PostgreSQL16 + ephemeral RS256 signature proof.
 * Runs ONLY on the dedicated disposable CI PostgreSQL database. The V1/V2
 * schema is installed only in that disposable CI test database beforehand.
 * No Spring bean, HTTP, mission or SSE activation.
 */
class RuntimeVerifiedTaskPlanningProbeTest {
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String AUDIENCE = "aetheris-orchestrator-api";
    private static final String KID = "s0-ci-planning";
    private static final UUID OLD = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ALICE = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ALICE_BETA = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID BOB = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID ALICE_NEXT = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID VIEWER = UUID.fromString("88888888-8888-4888-8888-888888888888");

    private static KeyPair signingKey;
    private static RuntimePinnedPrincipalVerifier verifier;
    private static String password;
    private final RuntimeVerifiedTaskPlanningProbe probe = new RuntimeVerifiedTaskPlanningProbe();

    @BeforeAll
    static void setup() throws Exception {
        Assumptions.assumeTrue("yes".equals(System.getenv("AETHERIS_WRITE_JDBC_MUST_RUN")),
            "Requires explicit disposable PostgreSQL16 CI job");
        assertEquals("true", System.getenv("GITHUB_ACTIONS"));
        assertEquals("yes", System.getenv("AETHERIS_WRITE_DISPOSABLE_ONLY"));
        assertEquals("127.0.0.1", System.getenv("PGHOST"));
        assertEquals("writeprobe_disposable", System.getenv("PGDATABASE"));
        assertEquals("writeprobe_ci", System.getenv("PGUSER"));
        password = System.getenv("PGPASSWORD");
        assertNotNull(password);
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        signingKey = generator.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(signingKey.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
        verifier = new RuntimePinnedPrincipalVerifier(pem, KID, ISSUER,
            AUDIENCE, Clock.systemUTC());
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
            "jdbc:postgresql://127.0.0.1:5432/writeprobe_disposable",
            "writeprobe_ci", password);
    }

    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal signed(
            KeyPair key, long uid, List<String> scopes) {
        Instant now = Instant.now();
        String jwt = Jwts.builder().header().keyId(KID).and()
            .subject("signed-synthetic-" + uid + "@example.invalid")
            .issuer(ISSUER).claim("aud", List.of(AUDIENCE))
            .claim("uid", uid).claim("role", "DEVELOPER")
            .claim("scopes", scopes)
            .issuedAt(Date.from(now.minusSeconds(10)))
            .expiration(Date.from(now.plusSeconds(300)))
            .signWith(key.getPrivate(), Jwts.SIG.RS256).compact();
        return verifier.verifyBearer("Bearer " + jwt);
    }

    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal identity(long uid) {
        return signed(signingKey, uid, List.of("orchestrator:write"));
    }

    private static void execute(String query) throws SQLException {
        try (Connection c = connection(); var statement = c.createStatement()) {
            statement.execute(query);
        }
    }

    @BeforeEach
    void seedDisposableOnly() throws Exception {
        // Intentionally destructive ONLY in the fixed CI-created disposable DB.
        execute("TRUNCATE aetheris_tasks, registry_project_memberships, "
            + "registry_tenant_memberships, registry_projects, registry_tenants CASCADE");
        execute("""
            INSERT INTO registry_tenants(tenant_key,status) VALUES
            ('alpha','ACTIVE'),('beta','ACTIVE')
            """);
        execute("""
            INSERT INTO registry_projects(tenant_key,project_key,status) VALUES
            ('alpha','red','ACTIVE'),('alpha','blue','ACTIVE'),('beta','red','ACTIVE')
            """);
        execute("""
            INSERT INTO registry_tenant_memberships
            (tenant_key,identity_uid,member_role,status,valid_until) VALUES
            ('alpha',42,'OWNER','ACTIVE',now()+interval '1 day'),
            ('beta',42,'MEMBER','ACTIVE',now()+interval '1 day'),
            ('alpha',77,'MEMBER','ACTIVE',now()+interval '1 day'),
            ('alpha',88,'VIEWER','ACTIVE',now()+interval '1 day')
            """);
        execute("""
            INSERT INTO registry_project_memberships
            (tenant_key,project_key,identity_uid,member_role,status,valid_until) VALUES
            ('alpha','red',42,'ADMIN','ACTIVE',now()+interval '1 day'),
            ('beta','red',42,'MEMBER','ACTIVE',now()+interval '1 day'),
            ('alpha','blue',77,'MEMBER','ACTIVE',now()+interval '1 day'),
            ('alpha','red',88,'VIEWER','ACTIVE',now()+interval '1 day')
            """);
        execute("""
            INSERT INTO aetheris_tasks
            (id,title,command_text,state,mode,created_at,updated_at,version,
             owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at) VALUES
            ('11111111-1111-4111-8111-111111111111','old','synthetic',
                'QUEUED','BALANCED',now(),now(),0,NULL,NULL,NULL,NULL),
            ('22222222-2222-4222-8222-222222222222','alice','synthetic',
                'QUEUED','BALANCED',now(),now(),0,42,'alpha','red',now()),
            ('33333333-3333-4333-8333-333333333333','alice-beta','synthetic',
                'QUEUED','BALANCED',now(),now(),0,42,'beta','red',now()),
            ('44444444-4444-4444-8444-444444444444','bob','synthetic',
                'QUEUED','BALANCED',now(),now(),0,77,'alpha','blue',now()),
            ('55555555-5555-4555-8555-555555555555','next','synthetic',
                'QUEUED','BALANCED',now(),now(),0,42,'alpha','red',now()),
            ('88888888-8888-4888-8888-888888888888','viewer','synthetic',
                'QUEUED','BALANCED',now(),now(),0,88,'alpha','red',now())
            """);
    }

    private static long version(UUID id) throws Exception {
        try (Connection c = connection(); var query = c.prepareStatement(
                "SELECT version FROM aetheris_tasks WHERE id=?")) {
            query.setObject(1, id);
            try (var rows = query.executeQuery()) {
                assertTrue(rows.next());
                return rows.getLong(1);
            }
        }
    }

    @Test
    void exactSignedOwnerAndTenantMembershipEnforceScopedPlanningOnly() throws Exception {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            assertFalse(probe.tryPlanQueuedTask(c, identity(77), "alpha","red", ALICE,0));
            assertFalse(probe.tryPlanQueuedTask(c, identity(42), "beta","red", ALICE,0));
            assertFalse(probe.tryPlanQueuedTask(c, identity(42), "alpha","blue", BOB,0));
            assertFalse(probe.tryPlanQueuedTask(c, identity(42), "alpha","red", OLD,0));
            assertFalse(probe.tryPlanQueuedTask(c, identity(42), "alpha","red", BOB,0));
            assertFalse(probe.tryPlanQueuedTask(c, identity(42), "alpha","red", null,0));
            assertFalse(probe.tryPlanQueuedTask(c, identity(42), "alpha","red", ALICE,-1));
            assertFalse(probe.tryPlanQueuedTask(c, identity(42), "ALPHA","red", ALICE,0));
            assertFalse(probe.tryPlanQueuedTask(c, identity(42), null,"red", ALICE,0));
            assertTrue(probe.tryPlanQueuedTask(c, identity(42), "alpha","red",ALICE,0));
            assertFalse(probe.tryPlanQueuedTask(c, identity(42), "alpha","red",ALICE,0));
            assertTrue(probe.tryPlanQueuedTask(c, identity(42), "beta","red",ALICE_BETA,0));
            assertTrue(probe.tryPlanQueuedTask(c, identity(77), "alpha","blue",BOB,0));
            c.rollback(); // test proves write never commits without caller decision
        }
        assertEquals(0, version(ALICE));
        assertEquals(0, version(ALICE_BETA));
        assertEquals(0, version(BOB));
    }

    @Test
    void invalidPrincipalScopeAndViewerNeverObtainWriteGrant() throws Exception {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            assertFalse(probe.tryPlanQueuedTask(c,
                signed(signingKey, 42, List.of("orchestrator:read")),
                "alpha","red",ALICE,0));
            assertFalse(probe.tryPlanQueuedTask(c, identity(88), "alpha","red",VIEWER,0));
            var attacker = KeyPairGenerator.getInstance("RSA");
            attacker.initialize(2048);
            assertThrows(IllegalArgumentException.class,
                () -> signed(attacker.generateKeyPair(), 42,
                    List.of("orchestrator:write")));
            c.rollback();
        }
        try (Connection autoCommit = connection()) {
            assertThrows(IllegalStateException.class, () ->
                probe.tryPlanQueuedTask(autoCommit, identity(42), "alpha","red",ALICE,0));
        }
        assertEquals(0, version(ALICE));
    }

    @Test
    void revocationExpirySuspensionAndOptimisticVersionFailClosed() throws Exception {
        execute("""
            UPDATE registry_project_memberships SET status='REVOKED',revoked_at=now()
            WHERE tenant_key='alpha' AND project_key='red' AND identity_uid=42
            """);
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            assertFalse(probe.tryPlanQueuedTask(c,identity(42),"alpha","red",ALICE,0));
            c.rollback();
        }
        execute("""
            UPDATE registry_project_memberships SET status='ACTIVE',revoked_at=NULL,
              valid_until=now()-interval '1 minute'
            WHERE tenant_key='alpha' AND project_key='red' AND identity_uid=42
            """);
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            assertFalse(probe.tryPlanQueuedTask(c,identity(42),"alpha","red",ALICE,0));
            c.rollback();
        }
        execute("""
            UPDATE registry_project_memberships SET valid_until=now()+interval '1 day'
            WHERE tenant_key='alpha' AND project_key='red' AND identity_uid=42
            """);
        execute("UPDATE registry_tenants SET status='SUSPENDED' WHERE tenant_key='alpha'");
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            assertFalse(probe.tryPlanQueuedTask(c,identity(42),"alpha","red",ALICE,0));
            c.rollback();
        }
        execute("UPDATE registry_tenants SET status='ACTIVE' WHERE tenant_key='alpha'");
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            assertFalse(probe.tryPlanQueuedTask(c,identity(42),"alpha","red",ALICE,99));
            assertTrue(probe.tryPlanQueuedTask(c,identity(42),"alpha","red",ALICE,0));
            c.commit();
        }
        assertEquals(1,version(ALICE));
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            assertFalse(probe.tryPlanQueuedTask(c,identity(42),"alpha","red",ALICE,1));
            c.rollback();
        }
    }

    @Test
    void concurrentRevokeWaitsForAuthenticatedPlanningTransaction() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try (Connection writer = connection()) {
            writer.setAutoCommit(false);
            assertTrue(probe.tryPlanQueuedTask(writer,identity(42),"alpha","red",ALICE,0));
            CountDownLatch started = new CountDownLatch(1);
            var revocation = executor.submit(() -> {
                try (Connection c = connection()) {
                    c.setAutoCommit(false);
                    started.countDown();
                    try (var statement = c.createStatement()) {
                        statement.execute("SET LOCAL lock_timeout = '8s'");
                        int n = statement.executeUpdate("""
                            UPDATE registry_tenant_memberships
                            SET status='REVOKED',revoked_at=now(),version=version+1
                            WHERE tenant_key='alpha' AND identity_uid=42
                            """);
                        c.commit();
                        return n;
                    }
                }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () ->
                revocation.get(350, TimeUnit.MILLISECONDS),
                "A revocation must wait for the committed authorization lock");
            writer.commit();
            assertEquals(1,revocation.get(10, TimeUnit.SECONDS));
            assertEquals(1,version(ALICE));
            try (Connection next = connection()) {
                next.setAutoCommit(false);
                assertFalse(probe.tryPlanQueuedTask(next,identity(42),
                    "alpha","red",ALICE_NEXT,0));
                next.rollback();
            }
            assertEquals(0,version(ALICE_NEXT));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void committedRevocationWinsBeforeNewTaskWrite() throws Exception {
        execute("""
            UPDATE registry_tenant_memberships
            SET status='REVOKED',revoked_at=now(),version=version+1
            WHERE tenant_key='alpha' AND identity_uid=42
            """);
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            assertFalse(probe.tryPlanQueuedTask(c, identity(42),
                "alpha","red",ALICE,0));
            assertTrue(probe.tryPlanQueuedTask(c, identity(42),
                "beta","red",ALICE_BETA,0));
            c.commit();
        }
        assertEquals(0,version(ALICE));
        assertEquals(1,version(ALICE_BETA));
    }
}
