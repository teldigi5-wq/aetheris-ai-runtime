package io.aetheris.orchestrator.security;

import io.aetheris.orchestrator.policy.OperationMode;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real signed RS256+PostgreSQL16 transaction proof.
 *
 * Synthetic input, disposable 127.0.0.1 database and short-lived CI-owned
 * schema only. No real principal/session authority, migration, public route,
 * task dispatcher, SSE or production write is activated.
 */
class RuntimeVerifiedTaskCreationJdbcTest {
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String AUDIENCE = "aetheris-orchestrator-api";
    private static final String KEY_ID = "owner-create-disposable-review";
    private static final String SCHEMA =
        "ownerlab_create_" + UUID.randomUUID().toString().replace("-", "");
    private static final String DB = "jdbc:postgresql://127.0.0.1:5432/ownerlab_disposable";
    private static KeyPair signer;
    private static RuntimePinnedPrincipalVerifier verifier;
    private final RuntimeVerifiedTaskCreationProbe probe =
        new RuntimeVerifiedTaskCreationProbe();

    @BeforeAll
    static void setup() throws Exception {
        Assumptions.assumeTrue(
            "yes".equals(System.getenv("AETHERIS_OWNER_CREATE_JDBC_MUST_RUN")),
            "Opt-in disposable PostgreSQL security test only");
        assertEquals("true", System.getenv("GITHUB_ACTIONS"));
        assertEquals("127.0.0.1", System.getenv("PGHOST"));
        assertEquals("ownerlab_disposable", System.getenv("PGDATABASE"));
        assertEquals("ownerlab_ci", System.getenv("PGUSER"));
        assertNotNull(System.getenv("PGPASSWORD"));
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        signer = generator.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n" +
            Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(signer.getPublic().getEncoded()) +
            "\n-----END PUBLIC KEY-----";
        verifier = new RuntimePinnedPrincipalVerifier(
            pem, KEY_ID, ISSUER, AUDIENCE, Clock.systemUTC());
        try (Connection admin = DriverManager.getConnection(
                DB, "ownerlab_ci", System.getenv("PGPASSWORD"));
             Statement stmt = admin.createStatement()) {
            stmt.execute("CREATE SCHEMA " + SCHEMA);
            stmt.execute("SET search_path TO " + SCHEMA);
            stmt.execute("""
                CREATE TABLE registry_tenants (
                  tenant_key varchar(96) PRIMARY KEY, status varchar(16) NOT NULL
                )""");
            stmt.execute("""
                CREATE TABLE registry_projects (
                  tenant_key varchar(96) NOT NULL REFERENCES registry_tenants(tenant_key),
                  project_key varchar(96) NOT NULL, status varchar(16) NOT NULL,
                  PRIMARY KEY(tenant_key,project_key)
                )""");
            stmt.execute("""
                CREATE TABLE registry_tenant_memberships (
                  tenant_key varchar(96) NOT NULL REFERENCES registry_tenants(tenant_key),
                  identity_uid bigint NOT NULL, member_role varchar(16) NOT NULL,
                  status varchar(16) NOT NULL, revoked_at timestamptz,
                  valid_until timestamptz,
                  PRIMARY KEY(tenant_key,identity_uid)
                )""");
            stmt.execute("""
                CREATE TABLE registry_project_memberships (
                  tenant_key varchar(96) NOT NULL, project_key varchar(96) NOT NULL,
                  identity_uid bigint NOT NULL, member_role varchar(16) NOT NULL,
                  status varchar(16) NOT NULL, revoked_at timestamptz,
                  valid_until timestamptz,
                  PRIMARY KEY(tenant_key,project_key,identity_uid),
                  FOREIGN KEY(tenant_key,project_key)
                    REFERENCES registry_projects(tenant_key,project_key),
                  FOREIGN KEY(tenant_key,identity_uid)
                    REFERENCES registry_tenant_memberships(tenant_key,identity_uid)
                )""");
            stmt.execute("""
                CREATE TABLE aetheris_tasks (
                  id uuid PRIMARY KEY, title varchar(160) NOT NULL,
                  command_text varchar(8000) NOT NULL, state varchar(32) NOT NULL,
                  mode varchar(32) NOT NULL, created_at timestamptz NOT NULL,
                  updated_at timestamptz NOT NULL, version bigint NOT NULL,
                  owner_identity_uid bigint, owner_tenant_key varchar(96),
                  owner_project_key varchar(96), owner_verified_at timestamptz,
                  CHECK (
                    (owner_identity_uid IS NULL AND owner_tenant_key IS NULL
                      AND owner_project_key IS NULL AND owner_verified_at IS NULL)
                    OR
                    (owner_identity_uid > 0 AND owner_tenant_key IS NOT NULL
                      AND owner_project_key IS NOT NULL AND owner_verified_at IS NOT NULL)
                  ),
                  FOREIGN KEY(owner_tenant_key,owner_project_key,owner_identity_uid)
                    REFERENCES registry_project_memberships(
                      tenant_key,project_key,identity_uid)
                )""");
            stmt.execute("""
                INSERT INTO registry_tenants VALUES
                  ('tenant-alpha','ACTIVE'),('tenant-beta','ACTIVE'),
                  ('race-tenant','ACTIVE')""");
            stmt.execute("""
                INSERT INTO registry_projects VALUES
                  ('tenant-alpha','project-red','ACTIVE'),
                  ('tenant-alpha','project-blue','ACTIVE'),
                  ('tenant-alpha','project-blocked','SUSPENDED'),
                  ('tenant-beta','project-red','ACTIVE'),
                  ('race-tenant','project-red','ACTIVE')""");
            stmt.execute("""
                INSERT INTO registry_tenant_memberships VALUES
                  ('tenant-alpha',42,'ADMIN','ACTIVE',NULL,now()+interval '1 day'),
                  ('tenant-beta',42,'MEMBER','ACTIVE',NULL,now()+interval '1 day'),
                  ('race-tenant',42,'ADMIN','ACTIVE',NULL,now()+interval '1 day'),
                  ('tenant-alpha',77,'MEMBER','ACTIVE',NULL,now()+interval '1 day'),
                  ('tenant-alpha',88,'MEMBER','REVOKED',now(),NULL),
                  ('tenant-alpha',89,'MEMBER','ACTIVE',NULL,now()-interval '1 hour'),
                  ('tenant-alpha',99,'VIEWER','ACTIVE',NULL,now()+interval '1 day')
                """);
            stmt.execute("""
                INSERT INTO registry_project_memberships VALUES
                  ('tenant-alpha','project-red',42,'ADMIN','ACTIVE',NULL,now()+interval '1 day'),
                  ('tenant-beta','project-red',42,'MEMBER','ACTIVE',NULL,now()+interval '1 day'),
                  ('race-tenant','project-red',42,'ADMIN','ACTIVE',NULL,now()+interval '1 day'),
                  ('tenant-alpha','project-blue',77,'MEMBER','ACTIVE',NULL,now()+interval '1 day'),
                  ('tenant-alpha','project-blocked',42,'ADMIN','ACTIVE',NULL,now()+interval '1 day'),
                  ('tenant-alpha','project-red',88,'MEMBER','REVOKED',now(),NULL),
                  ('tenant-alpha','project-red',89,'MEMBER','ACTIVE',NULL,now()-interval '1 hour'),
                  ('tenant-alpha','project-red',99,'VIEWER','ACTIVE',NULL,now()+interval '1 day')
                """);
        }
    }

    private static Connection connection() throws SQLException {
        Connection connection = DriverManager.getConnection(
            DB, "ownerlab_ci", System.getenv("PGPASSWORD"));
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO " + SCHEMA);
        }
        connection.setAutoCommit(false);
        return connection;
    }

    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal signed(
            long uid, String role, List<String> scopes, boolean includeJti) {
        Instant now = Instant.now();
        var builder = Jwts.builder().header().keyId(KEY_ID).and()
            .subject("synthetic-owner-" + uid + "@example.invalid")
            .issuer(ISSUER).claim("aud", List.of(AUDIENCE))
            .claim("uid", uid).claim("role", role).claim("scopes", scopes)
            .issuedAt(Date.from(now.minusSeconds(10)))
            .expiration(Date.from(now.plusSeconds(180)));
        if (includeJti) {
            builder.id("ownerlab_" + UUID.randomUUID().toString());
        }
        return verifier.verifyBearer("Bearer " +
            builder.signWith(signer.getPrivate(), Jwts.SIG.RS256).compact());
    }

    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal writer(long uid) {
        return signed(uid, "DEVELOPER", List.of("orchestrator:write"), true);
    }

    private UUID insert(Connection connection,
                        RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal,
                        String tenant, String project) throws SQLException {
        return probe.create(connection, principal, tenant, project,
            "review-only task", "Never execute: synthetic plan",
            OperationMode.PRIVATE);
    }

    private static long count(Connection c) throws SQLException {
        try (Statement s=c.createStatement();
             var r=s.executeQuery("SELECT count(*) FROM aetheris_tasks")) {
            assertTrue(r.next());
            return r.getLong(1);
        }
    }

    @Test
    void signedOwnerCanCreateOnlyInOwnActiveTenantAndProject() throws Exception {
        try (Connection c = connection()) {
            long baseline=count(c);
            UUID a=insert(c,writer(42),"tenant-alpha","project-red");
            UUID b=insert(c,writer(42),"tenant-beta","project-red");
            UUID d=insert(c,writer(77),"tenant-alpha","project-blue");
            assertNotEquals(a,b);
            assertNotEquals(b,d);
            try (PreparedStatement st=c.prepareStatement("""
                    SELECT owner_identity_uid,owner_tenant_key,owner_project_key,
                           owner_verified_at,state,mode FROM aetheris_tasks WHERE id=?
                    """)) {
                st.setObject(1,a);
                try (var row=st.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals(42L,row.getLong(1));
                    assertEquals("tenant-alpha",row.getString(2));
                    assertEquals("project-red",row.getString(3));
                    assertNotNull(row.getTimestamp(4));
                    assertEquals("QUEUED",row.getString(5));
                    assertEquals("PRIVATE",row.getString(6));
                }
            }
            assertEquals(baseline+3,count(c));
            c.rollback();
        }
    }

    @Test
    void missingCrossOwnerRevokedExpiredViewerAndSuspendedMembershipFailClosed()
            throws Exception {
        try (Connection c=connection()) {
            long original=count(c);
            assertThrows(SecurityException.class,
                () -> insert(c,writer(77),"tenant-alpha","project-red"));
            assertThrows(SecurityException.class,
                () -> insert(c,writer(42),"tenant-alpha","project-blue"));
            assertThrows(SecurityException.class,
                () -> insert(c,writer(42),"unknown","project-red"));
            assertThrows(SecurityException.class,
                () -> insert(c,writer(88),"tenant-alpha","project-red"));
            assertThrows(SecurityException.class,
                () -> insert(c,writer(89),"tenant-alpha","project-red"));
            assertThrows(SecurityException.class,
                () -> insert(c,writer(99),"tenant-alpha","project-red"));
            assertThrows(SecurityException.class,
                () -> insert(c,writer(42),"tenant-alpha","project-blocked"));
            assertEquals(original,count(c));
            c.rollback();
        }
    }

    @Test
    void principalCannotBeForgedAndSignedJtiAndWriteScopeAreMandatory()
            throws Exception {
        try (Connection c=connection()) {
            // Other tests may commit a synthetic task to prove a real
            // cross-session revocation race; deny paths must add ZERO rows
            // regardless of their independent JUnit execution order.
            long baseline = count(c);
            var onlyRead=signed(42,"DEVELOPER",List.of("orchestrator:read"),true);
            assertThrows(SecurityException.class,
                () -> insert(c,onlyRead,"tenant-alpha","project-red"));
            var missingJti=signed(42,"DEVELOPER",List.of("orchestrator:write"),false);
            assertThrows(IllegalArgumentException.class,
                () -> insert(c,missingJti,"tenant-alpha","project-red"));
            assertThrows(NullPointerException.class,
                () -> insert(c,null,"tenant-alpha","project-red"));
            var allowed=writer(42);
            assertThrows(IllegalArgumentException.class, () -> probe.create(c,
                allowed,"TENANT-INJECTED","project-red","safe title",
                "synthetic",OperationMode.PRIVATE));
            assertThrows(IllegalArgumentException.class, () -> probe.create(c,
                allowed,"tenant-alpha","project-red","safe title","bad\ncommand",
                OperationMode.PRIVATE));
            assertEquals(baseline,count(c));
            c.rollback();
        }
        KeyPairGenerator g=KeyPairGenerator.getInstance("RSA");
        g.initialize(3072);
        var attacker=g.generateKeyPair();
        Instant now=Instant.now();
        String forged=Jwts.builder().header().keyId(KEY_ID).and()
            .subject("synthetic-forgery@example.invalid").issuer(ISSUER)
            .claim("aud",List.of(AUDIENCE)).claim("uid",42)
            .claim("role","DEVELOPER")
            .claim("scopes",List.of("orchestrator:write"))
            .id("ownerlab_" + UUID.randomUUID())
            .issuedAt(Date.from(now.minusSeconds(5)))
            .expiration(Date.from(now.plusSeconds(60)))
            .signWith(attacker.getPrivate(),Jwts.SIG.RS256).compact();
        assertThrows(IllegalArgumentException.class,
            () -> verifier.verifyBearer("Bearer " + forged));
    }

    @Test
    void readOnlyAutocommitAndNonReadCommittedTransactionsAreDenied()
            throws Exception {
        var p=writer(42);
        try (Connection c=connection()) {
            c.setAutoCommit(true);
            assertThrows(IllegalStateException.class,
                () -> insert(c,p,"tenant-alpha","project-red"));
            c.setAutoCommit(false);
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            assertThrows(IllegalStateException.class,
                () -> insert(c,p,"tenant-alpha","project-red"));
            c.rollback();
        }
    }

    @Test
    void ownerCreateHoldsMembershipRowLockUntilCommitThenRevocationDenies()
            throws Exception {
        try (Connection writer=connection(); Connection revoker=connection()) {
            UUID prior=insert(writer,writer(42),"race-tenant","project-red");
            try (Statement timeout=revoker.createStatement()) {
                timeout.execute("SET LOCAL statement_timeout = '750ms'");
            }
            SQLException blocked=assertThrows(SQLException.class, () -> {
                try (PreparedStatement revoke=revoker.prepareStatement("""
                      UPDATE registry_project_memberships
                         SET status='REVOKED',revoked_at=clock_timestamp()
                       WHERE tenant_key='race-tenant'
                         AND project_key='project-red' AND identity_uid=42
                    """)) {
                    revoke.executeUpdate();
                }
            });
            assertEquals("57014",blocked.getSQLState(),
                "Concurrent revocation must wait for the owner-creation commit");
            revoker.rollback();
            writer.commit();
            try (PreparedStatement revoke=revoker.prepareStatement("""
                  UPDATE registry_project_memberships
                     SET status='REVOKED',revoked_at=clock_timestamp()
                   WHERE tenant_key='race-tenant'
                     AND project_key='project-red' AND identity_uid=42
                """)) {
                assertEquals(1,revoke.executeUpdate());
            }
            revoker.commit();
            assertThrows(SecurityException.class,
                () -> insert(writer,writer(42),"race-tenant","project-red"));
            writer.rollback();
            try (PreparedStatement find=revoker.prepareStatement(
                    "SELECT owner_identity_uid FROM aetheris_tasks WHERE id=?")) {
                find.setObject(1,prior);
                try (var rows=find.executeQuery()) {
                    assertTrue(rows.next(), "Earlier committed task remains durably bound");
                    assertEquals(42L,rows.getLong(1));
                }
            }
            revoker.rollback();
        }
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (signer==null) return;
        try (Connection admin=DriverManager.getConnection(
                DB,"ownerlab_ci",System.getenv("PGPASSWORD"));
             Statement stmt=admin.createStatement()) {
            stmt.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            try (var check=stmt.executeQuery(
                    "SELECT count(*) FROM pg_namespace WHERE nspname='" + SCHEMA + "'")) {
                assertTrue(check.next());
                assertEquals(0,check.getLong(1),"Disposable schema cleanup mandatory");
            }
        }
        System.out.println("RUNTIME_SIGNED_OWNER_TASK_WRITE_PG16_AND_SCHEMA_CLEANUP_PASS");
    }
}
