package io.aetheris.orchestrator.security;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S-B62-A review-only, real PostgreSQL 16 integration of the EXACT Java
 * RuntimeSyntheticFencedEffectProbe method. No Spring bean or HTTP route.
 *
 * Starts only inside opted-in disposable GitHub CI, with synthetic identities,
 * isolated login roles and a fresh schema that is dropped at the end.
 * DB owner is CI superuser for fixture management only; worker credentials
 * are separate real restricted PG LOGIN roles. Nothing is production ready:
 * signer/epoch absent-row revocation races and external old-primary fencing
 * remain NO_GO; successful return is transaction-pending, not committed.
 */
class RuntimeB62FullEffectJdbcTest {
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String WORKLOAD_ISSUER = "https://workload.aetheris.example.invalid";
    private static final String FENCE_ISSUER = "https://fence.aetheris.example.invalid";
    private static final UUID ALICE_TASK = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID BOB_TASK = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID OWNERLESS_TASK = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String[] TABLES = {
        "b62_local_resource_fence", "aetheris_tasks", "tenant_memberships",
        "project_memberships", "synthetic_session_registration",
        "signer_authorizations", "principal_epochs", "synthetic_guarded_task_effects"
    };
    private static final SecureRandom RANDOM = new SecureRandom();
    private static String schema, ownerPassword, workerName, workerPassword;
    private static String revokerName, revokerPassword;
    private static RuntimePinnedPrincipalVerifier principalVerifier;
    private static RuntimePinnedWorkloadIdentityVerifier workloadVerifier;
    private static RuntimePinnedLocalFenceLeaseVerifier leaseVerifier;
    private static KeyPair identityKey, workloadKey, fenceKey;
    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal alice, bob;
    private static RuntimePinnedWorkloadIdentityVerifier.VerifiedWorkload workload;
    private static RuntimePinnedLocalFenceLeaseVerifier.VerifiedLease lease;

    private static String newSecret() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String pem(KeyPair key) {
        return "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(key.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
    }

    private static Connection db(String user, String password) throws SQLException {
        Connection c = DriverManager.getConnection(
            "jdbc:postgresql://127.0.0.1:5432/b62_effect_disposable",
            user, password);
        if (schema != null) c.setSchema(schema);
        return c;
    }

    private static Connection owner() throws SQLException {
        return db("b62_effect_owner", ownerPassword);
    }

    private static Connection worker() throws SQLException {
        return db(workerName, workerPassword);
    }

    private static Connection revoker() throws SQLException {
        return db(revokerName, revokerPassword);
    }

    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal(
            long uid, List<String> scopes) {
        Instant issued = Instant.now().minusSeconds(8);
        String jwt = Jwts.builder().header().keyId("effect-identity-key").and()
            .issuer(ISSUER).subject("synthetic-" + uid + "@example.invalid")
            .claim("aud", List.of("runtime-effect-review"))
            .claim("uid", uid).claim("role", "DEVELOPER")
            .claim("scopes", scopes).id(UUID.randomUUID().toString())
            .issuedAt(Date.from(issued)).expiration(Date.from(issued.plusSeconds(180)))
            .signWith(identityKey.getPrivate(), Jwts.SIG.RS256).compact();
        return principalVerifier.verifyBearerForRevocation("Bearer " + jwt);
    }

    private static RuntimePinnedWorkloadIdentityVerifier.VerifiedWorkload signedWorkload() {
        Instant issued = Instant.now().minusSeconds(8);
        String jwt = Jwts.builder().header().keyId("effect-workload-key").and()
            .issuer(WORKLOAD_ISSUER).subject("synthetic-worker")
            .claim("aud", List.of("runtime-workload"))
            .claim("purpose", "runtime-workload-review")
            .claim("resource", "runtime")
            .issuedAt(Date.from(issued)).expiration(Date.from(issued.plusSeconds(60)))
            .signWith(workloadKey.getPrivate(), Jwts.SIG.RS256).compact();
        return workloadVerifier.verifySignedWorkload(jwt);
    }

    private static RuntimePinnedLocalFenceLeaseVerifier.VerifiedLease signedLease() {
        Instant issued = Instant.now().minusSeconds(8);
        String jwt = Jwts.builder().header().keyId("effect-fence-key").and()
            .issuer(FENCE_ISSUER).claim("aud", List.of("runtime-fence"))
            .claim("purpose", "runtime-local-fence-review")
            .claim("resource", "runtime").claim("endpoint", "synthetic-primary")
            .claim("term", 2).claim("workload", "synthetic-worker")
            .issuedAt(Date.from(issued)).expiration(Date.from(issued.plusSeconds(60)))
            .signWith(fenceKey.getPrivate(), Jwts.SIG.RS256).compact();
        return leaseVerifier.verifySignedLease(jwt);
    }

    @BeforeAll
    static void onlyDisposableCI() throws Exception {
        assertEquals("yes", System.getenv("AETHERIS_B62_FULL_EFFECT_JDBC_MUST_RUN"));
        assertEquals("true", System.getenv("GITHUB_ACTIONS"));
        assertEquals("yes", System.getenv("AETHERIS_B62_EFFECT_DISPOSABLE_CI_ONLY"));
        assertEquals("127.0.0.1", System.getenv("PGHOST"));
        assertEquals("b62_effect_disposable", System.getenv("PGDATABASE"));
        assertEquals("b62_effect_owner", System.getenv("PGUSER"));
        ownerPassword = System.getenv("PGPASSWORD");
        assertNotNull(ownerPassword);
        assertFalse(ownerPassword.isBlank());

        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        identityKey = generator.generateKeyPair();
        workloadKey = generator.generateKeyPair();
        fenceKey = generator.generateKeyPair();
        Clock clock = Clock.systemUTC();
        principalVerifier = new RuntimePinnedPrincipalVerifier(
            pem(identityKey), "effect-identity-key", ISSUER, "runtime-effect-review", clock);
        workloadVerifier = new RuntimePinnedWorkloadIdentityVerifier(
            pem(workloadKey), "effect-workload-key", WORKLOAD_ISSUER, "runtime-workload", clock);
        leaseVerifier = new RuntimePinnedLocalFenceLeaseVerifier(
            pem(fenceKey), "effect-fence-key", FENCE_ISSUER, "runtime-fence", clock);
        String nonce = UUID.randomUUID().toString().replace("-", "");
        assertTrue(nonce.matches("[0-9a-f]{32}"));
        schema = "b62_effect_" + nonce;
        workerName = "b62_worker_" + nonce.substring(0, 20);
        revokerName = "b62_revoker_" + nonce.substring(0, 20);
        workerPassword = newSecret();
        revokerPassword = newSecret();
        assertTrue(schema.matches("b62_effect_[0-9a-f]{32}"));
        assertTrue(workerName.matches("b62_worker_[0-9a-f]{20}"));
        assertTrue(revokerName.matches("b62_revoker_[0-9a-f]{20}"));
        try (Connection admin = owner(); Statement s = admin.createStatement()) {
            s.execute("CREATE ROLE " + workerName + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '" + workerPassword + "'");
            s.execute("CREATE ROLE " + revokerName + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '" + revokerPassword + "'");
            s.execute("CREATE SCHEMA " + schema);
            s.execute("SET search_path TO " + schema + ",pg_catalog");
            s.execute("CREATE TABLE b62_local_resource_fence(resource_key text PRIMARY KEY, endpoint text NOT NULL, term bigint NOT NULL, state text NOT NULL, expires_at timestamptz NOT NULL, lock_guard smallint NOT NULL DEFAULT 0 CHECK(lock_guard=0))");
            s.execute("CREATE TABLE aetheris_tasks(id uuid PRIMARY KEY, owner_identity_uid bigint, owner_tenant_key text, owner_project_key text, owner_verified_at timestamptz, lock_guard smallint NOT NULL DEFAULT 0 CHECK(lock_guard=0))");
            s.execute("CREATE TABLE tenant_memberships(tenant_key text, identity_uid bigint, status text, revoked_at timestamptz, valid_until timestamptz, lock_guard smallint NOT NULL DEFAULT 0 CHECK(lock_guard=0), PRIMARY KEY(tenant_key,identity_uid))");
            s.execute("CREATE TABLE project_memberships(tenant_key text, project_key text, identity_uid bigint, status text, revoked_at timestamptz, valid_until timestamptz, lock_guard smallint NOT NULL DEFAULT 0 CHECK(lock_guard=0), PRIMARY KEY(tenant_key,project_key,identity_uid))");
            s.execute("CREATE TABLE synthetic_session_registration(issuer text, kid text, jti text, uid bigint, iat_seconds bigint, state text, lock_guard smallint NOT NULL DEFAULT 0 CHECK(lock_guard=0), PRIMARY KEY(issuer,kid,jti))");
            s.execute("CREATE TABLE signer_authorizations(issuer text,kid text,state text NOT NULL CHECK(state IN ('ACTIVE','REVOKED')),lock_guard smallint NOT NULL DEFAULT 0 CHECK(lock_guard=0),PRIMARY KEY(issuer,kid))");
            s.execute("CREATE TABLE principal_epochs(principal_id bigint PRIMARY KEY,reject_issued_at_or_before_epoch bigint NOT NULL CHECK(reject_issued_at_or_before_epoch>=0),lock_guard smallint NOT NULL DEFAULT 0 CHECK(lock_guard=0))");
            s.execute("CREATE TABLE synthetic_guarded_task_effects(effect_id uuid PRIMARY KEY,task_id uuid NOT NULL,fence_term bigint NOT NULL,workload_subject text NOT NULL)");
            // CI-only monotonic write policy. Deliberately does not
            // prevent a privileged PostgreSQL owner disabling triggers.
            s.execute("CREATE FUNCTION b62_deny_signer_reopen() RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog AS $ BEGIN IF OLD.state='REVOKED' AND NEW.state<>'REVOKED' THEN RAISE EXCEPTION 'B62_SIGNER_REOPEN_FORBIDDEN' USING ERRCODE='23514'; END IF; RETURN NEW; END $");
            s.execute("CREATE TRIGGER b62_signer_monotonic BEFORE UPDATE OF state ON signer_authorizations FOR EACH ROW EXECUTE FUNCTION b62_deny_signer_reopen()");
            s.execute("CREATE FUNCTION b62_deny_epoch_rollback() RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog AS $ BEGIN IF NEW.reject_issued_at_or_before_epoch<OLD.reject_issued_at_or_before_epoch THEN RAISE EXCEPTION 'B62_EPOCH_ROLLBACK_FORBIDDEN' USING ERRCODE='23514'; END IF; RETURN NEW; END $");
            s.execute("CREATE TRIGGER b62_epoch_monotonic BEFORE UPDATE OF reject_issued_at_or_before_epoch ON principal_epochs FOR EACH ROW EXECUTE FUNCTION b62_deny_epoch_rollback()");
            s.execute("REVOKE ALL ON FUNCTION b62_deny_signer_reopen() FROM PUBLIC");
            s.execute("REVOKE ALL ON FUNCTION b62_deny_epoch_rollback() FROM PUBLIC");
            s.execute("REVOKE CREATE ON SCHEMA " + schema + " FROM PUBLIC");
            s.execute("GRANT USAGE ON SCHEMA " + schema + " TO " + workerName + "," + revokerName);
            s.execute("GRANT SELECT ON ALL TABLES IN SCHEMA " + schema + " TO " + workerName);
            s.execute("GRANT UPDATE(lock_guard) ON b62_local_resource_fence,aetheris_tasks,tenant_memberships,project_memberships,synthetic_session_registration,signer_authorizations,principal_epochs TO " + workerName);
            s.execute("GRANT INSERT ON synthetic_guarded_task_effects TO " + workerName);
            s.execute("GRANT SELECT ON synthetic_session_registration TO " + revokerName);
            s.execute("GRANT UPDATE(state) ON synthetic_session_registration,signer_authorizations TO " + revokerName);
            s.execute("GRANT UPDATE(reject_issued_at_or_before_epoch) ON principal_epochs TO " + revokerName);
            s.execute("GRANT SELECT ON signer_authorizations,principal_epochs TO " + revokerName);
        }
    }

    private static void register(Connection ownerConn,
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal) throws SQLException {
        var facts = principal.requireRevocationFacts();
        try (PreparedStatement p = ownerConn.prepareStatement(
            "INSERT INTO synthetic_session_registration(issuer,kid,jti,uid,iat_seconds,state) VALUES (?,?,?,?,?,'ACTIVE')")) {
            p.setString(1, facts.verifiedIssuer());
            p.setString(2, facts.verifiedKeyId());
            p.setString(3, facts.signedTokenId());
            p.setLong(4, facts.signedIdentityUid());
            p.setLong(5, facts.signedIssuedAt().getEpochSecond());
            assertEquals(1, p.executeUpdate());
        }
    }

    @BeforeEach
    void resetSyntheticRows() throws Exception {
        alice = principal(42L, List.of("orchestrator:read", "orchestrator:write"));
        bob = principal(77L, List.of("orchestrator:read", "orchestrator:write"));
        workload = signedWorkload();
        lease = signedLease();
        assertTrue(lease.isBoundTo(workload));
        try (Connection admin = owner(); Statement s = admin.createStatement()) {
            s.execute("TRUNCATE synthetic_guarded_task_effects,synthetic_session_registration,project_memberships,tenant_memberships,aetheris_tasks,b62_local_resource_fence,signer_authorizations,principal_epochs");
            s.execute("INSERT INTO b62_local_resource_fence VALUES ('runtime','synthetic-primary',2,'ACTIVE',clock_timestamp()+interval '1 day',0)");
            s.execute("INSERT INTO aetheris_tasks(id,owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at) VALUES ('" + ALICE_TASK + "',42,'tenant-a','project-a',clock_timestamp()),('" + BOB_TASK + "',77,'tenant-b','project-b',clock_timestamp()),('" + OWNERLESS_TASK + "',NULL,NULL,NULL,NULL)");
            s.execute("INSERT INTO tenant_memberships VALUES ('tenant-a',42,'ACTIVE',NULL,clock_timestamp()+interval '1 day',0),('tenant-b',77,'ACTIVE',NULL,clock_timestamp()+interval '1 day',0)");
            s.execute("INSERT INTO project_memberships VALUES ('tenant-a','project-a',42,'ACTIVE',NULL,clock_timestamp()+interval '1 day',0),('tenant-b','project-b',77,'ACTIVE',NULL,clock_timestamp()+interval '1 day',0)");
            s.execute("INSERT INTO signer_authorizations VALUES ('" + ISSUER + "','effect-identity-key','ACTIVE',0)");
            s.execute("INSERT INTO principal_epochs VALUES (42,0,0),(77,0,0)");
            register(admin, alice);
            register(admin, bob);
        }
    }

    private static boolean effect(Connection c,
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal person, String tenant,
            String project, UUID task, UUID effectId) throws SQLException {
        return new RuntimeSyntheticFencedEffectProbe().recordEffectSynthetic(
            c, person, tenant, project, task, effectId, lease, workload);
    }

    private static long countEffects() throws SQLException {
        try (Connection c = owner();
             Statement s = c.createStatement();
             var rows = s.executeQuery("SELECT COUNT(*) FROM synthetic_guarded_task_effects")) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    @Test
    void realSignedAuthorizedEffectIdempotencyAndRollback() throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection c = worker()) {
            assertEquals(workerName, c.getMetaData().getUserName());
            c.setAutoCommit(false);
            assertTrue(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, id));
            assertEquals(0L, countEffects(), "Uncommitted effect must not be durable");
            c.rollback();
            assertEquals(0L, countEffects(), "ROLLBACK must discard effect");
            assertTrue(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, id));
            c.commit();
            assertEquals(1L, countEffects());
            assertFalse(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, id));
            c.rollback();
            assertEquals(1L, countEffects(), "Duplicate effect ID must never insert twice");
        }
    }

    @Test
    void actualSqlRefusesCrossOwnerWrongTenantOwnerlessAndRevokedMembership() throws Exception {
        try (Connection c = worker()) {
            c.setAutoCommit(false);
            assertFalse(effect(c, alice, "tenant-b", "project-b", BOB_TASK, UUID.randomUUID()));
            assertFalse(effect(c, alice, "tenant-b", "project-a", ALICE_TASK, UUID.randomUUID()));
            assertFalse(effect(c, alice, "tenant-a", "project-b", ALICE_TASK, UUID.randomUUID()));
            assertFalse(effect(c, alice, "tenant-a", "project-a", OWNERLESS_TASK, UUID.randomUUID()));
            c.rollback();
        }
        try (Connection admin = owner(); Statement s = admin.createStatement()) {
            s.execute("UPDATE project_memberships SET status='REVOKED',revoked_at=clock_timestamp() WHERE identity_uid=42");
        }
        try (Connection c = worker()) {
            c.setAutoCommit(false);
            assertFalse(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, UUID.randomUUID()));
            assertTrue(effect(c, bob, "tenant-b", "project-b", BOB_TASK, UUID.randomUUID()));
            c.rollback();
        }
        assertEquals(0L, countEffects(), "No denied or rolled-back effects should remain");
    }

    @Test
    void committedSignerRevocationDeniesActualSql() throws Exception {
        try (Connection admin = owner(); Statement sql = admin.createStatement()) {
            assertEquals(1, sql.executeUpdate(
                "UPDATE signer_authorizations SET state='REVOKED' WHERE kid='effect-identity-key'"));
        }
        try (Connection executing = worker()) {
            executing.setAutoCommit(false);
            assertFalse(effect(executing, alice, "tenant-a", "project-a", ALICE_TASK, UUID.randomUUID()));
            executing.rollback();
        }
        assertEquals(0, countEffects());
    }

    @Test
    void committedPrincipalEpochCutoffDeniesActualSql() throws Exception {
        try (Connection admin = owner(); Statement sql = admin.createStatement()) {
            assertEquals(1, sql.executeUpdate(
                "UPDATE principal_epochs SET reject_issued_at_or_before_epoch=9999999999 WHERE principal_id=42"));
        }
        try (Connection executing = worker()) {
            executing.setAutoCommit(false);
            assertFalse(effect(executing, alice, "tenant-a", "project-a", ALICE_TASK, UUID.randomUUID()));
            assertTrue(effect(executing, bob, "tenant-b", "project-b", BOB_TASK, UUID.randomUUID()));
            executing.rollback();
        }
        assertEquals(0, countEffects());
    }

    @Test
    void localFenceClosureDeniesActualSql() throws Exception {
        try (Connection admin = owner(); Statement sql = admin.createStatement()) {
            sql.execute("UPDATE b62_local_resource_fence SET state='CLOSED'");
        }
        try (Connection executing = worker()) {
            executing.setAutoCommit(false);
            assertFalse(effect(executing, alice, "tenant-a", "project-a", ALICE_TASK, UUID.randomUUID()));
            executing.rollback();
        }
        assertEquals(0, countEffects());
    }

    @Test
    void restrictedRevokerCannotRestoreRevokedSigner() throws Exception {
        try (Connection revoking = revoker(); Statement sql = revoking.createStatement()) {
            assertEquals(1, sql.executeUpdate(
                "UPDATE signer_authorizations SET state='REVOKED' WHERE kid='effect-identity-key'"));
            SQLException denied = assertThrows(SQLException.class, () ->
                sql.executeUpdate(
                    "UPDATE signer_authorizations SET state='ACTIVE' WHERE kid='effect-identity-key'"));
            assertEquals("23514", denied.getSQLState());
        }
        try (Connection admin = owner(); Statement sql = admin.createStatement();
             var row = sql.executeQuery("SELECT state FROM signer_authorizations WHERE kid='effect-identity-key'")) {
            assertTrue(row.next());
            assertEquals("REVOKED", row.getString(1));
        }
    }

    @Test
    void restrictedRevokerCannotLowerPrincipalEpochCutoff() throws Exception {
        try (Connection revoking = revoker(); Statement sql = revoking.createStatement()) {
            assertEquals(1, sql.executeUpdate(
                "UPDATE principal_epochs SET reject_issued_at_or_before_epoch=9999999999 WHERE principal_id=42"));
            SQLException denied = assertThrows(SQLException.class, () ->
                sql.executeUpdate(
                    "UPDATE principal_epochs SET reject_issued_at_or_before_epoch=0 WHERE principal_id=42"));
            assertEquals("23514", denied.getSQLState());
        }
        try (Connection admin = owner(); Statement sql = admin.createStatement();
             var row = sql.executeQuery(
                 "SELECT reject_issued_at_or_before_epoch FROM principal_epochs WHERE principal_id=42")) {
            assertTrue(row.next());
            assertEquals(9999999999L, row.getLong(1));
        }
    }

    @Test
    void onlyRestrictedWorkerCanRecordAndAutoCommitIsDenied() throws Exception {
        try (Connection c = worker()) {
            assertTrue(c.getAutoCommit());
            assertFalse(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, UUID.randomUUID()));
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () ->
                    s.executeUpdate("UPDATE tenant_memberships SET status='REVOKED' WHERE identity_uid=42"));
                assertEquals("42501", ex.getSQLState(), "Effect role must not own membership DML");
            }
            c.rollback();
        }
        try (Connection c = revoker()) {
            SQLException ex = assertThrows(SQLException.class, () ->
                c.createStatement().executeUpdate("INSERT INTO synthetic_guarded_task_effects VALUES ('" +
                    UUID.randomUUID() + "','" + ALICE_TASK + "',2,'synthetic-worker')"));
            assertEquals("42501", ex.getSQLState(), "Revoker must not write synthetic effects");
        }
    }

    @Test
    void realSessionRowLockOrdersEffectBeforeConcurrentRevocationCommit() throws Exception {
        var facts = alice.requireRevocationFacts();
        try (Connection executing = worker(); Connection revocation = revoker()) {
            executing.setAutoCommit(false);
            assertTrue(effect(executing, alice, "tenant-a", "project-a", ALICE_TASK, UUID.randomUUID()));
            revocation.setAutoCommit(false);
            try (Statement s = revocation.createStatement()) {
                s.execute("SET LOCAL lock_timeout='350ms'");
                SQLException race = assertThrows(SQLException.class, () ->
                    s.executeUpdate("UPDATE synthetic_session_registration SET state='REVOKED' WHERE jti='" +
                        facts.signedTokenId() + "'"));
                assertEquals("55P03", race.getSQLState(),
                    "Revocation must wait behind in-flight effect's selected session row lock");
            }
            revocation.rollback();
            executing.commit();
            assertEquals(1L, countEffects());
            try (Statement s = revocation.createStatement()) {
                assertEquals(1, s.executeUpdate(
                    "UPDATE synthetic_session_registration SET state='REVOKED' WHERE jti='" +
                    facts.signedTokenId() + "'"));
            }
            revocation.commit();
        }
        try (Connection c = worker()) {
            c.setAutoCommit(false);
            assertFalse(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, UUID.randomUUID()));
            c.rollback();
        }
        assertEquals(1L, countEffects());
    }


    @Test
    void signerRevocationCannotCommitBeforePreviouslyAuthorizedEffect() throws Exception {
        try (Connection executing = worker(); Connection revoking = revoker()) {
            executing.setAutoCommit(false);
            assertTrue(effect(executing, alice, "tenant-a", "project-a", ALICE_TASK, UUID.randomUUID()));
            revoking.setAutoCommit(false);
            try (Statement sql = revoking.createStatement()) {
                sql.execute("SET LOCAL lock_timeout='350ms'");
                SQLException blocked = assertThrows(SQLException.class, () ->
                    sql.executeUpdate("UPDATE signer_authorizations SET state='REVOKED' WHERE kid='effect-identity-key'"));
                assertEquals("55P03", blocked.getSQLState());
            }
            revoking.rollback();
            executing.commit();
            try (Statement sql = revoking.createStatement()) {
                assertEquals(1, sql.executeUpdate("UPDATE signer_authorizations SET state='REVOKED' WHERE kid='effect-identity-key'"));
            }
            revoking.commit();
        }
        assertEquals(1L, countEffects());
        try (Connection c = worker()) {
            c.setAutoCommit(false);
            assertFalse(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, UUID.randomUUID()));
            assertFalse(effect(c, bob, "tenant-b", "project-b", BOB_TASK, UUID.randomUUID()));
            c.rollback();
        }
    }

    @Test
    void epochRevocationCannotCommitBeforePreviouslyAuthorizedEffect() throws Exception {
        long cutoff = alice.requireRevocationFacts().signedIssuedAt().getEpochSecond();
        try (Connection executing = worker(); Connection revoking = revoker()) {
            executing.setAutoCommit(false);
            assertTrue(effect(executing, alice, "tenant-a", "project-a", ALICE_TASK, UUID.randomUUID()));
            revoking.setAutoCommit(false);
            try (Statement sql = revoking.createStatement()) {
                sql.execute("SET LOCAL lock_timeout='350ms'");
                SQLException blocked = assertThrows(SQLException.class, () ->
                    sql.executeUpdate("UPDATE principal_epochs SET reject_issued_at_or_before_epoch=" + cutoff + " WHERE principal_id=42"));
                assertEquals("55P03", blocked.getSQLState());
            }
            revoking.rollback();
            executing.commit();
            try (Statement sql = revoking.createStatement()) {
                assertEquals(1, sql.executeUpdate(
                    "UPDATE principal_epochs SET reject_issued_at_or_before_epoch=" + cutoff + " WHERE principal_id=42"));
            }
            revoking.commit();
        }
        assertEquals(1L, countEffects());
        try (Connection c = worker()) {
            c.setAutoCommit(false);
            assertFalse(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, UUID.randomUUID()));
            assertTrue(effect(c, bob, "tenant-b", "project-b", BOB_TASK, UUID.randomUUID()));
            c.rollback();
        }
    }

    @Test
    void missingSignerOrPrincipalAuthorityRowFailsClosed() throws Exception {
        try (Connection owner = owner(); Statement sql = owner.createStatement()) {
            assertEquals(1, sql.executeUpdate("DELETE FROM signer_authorizations WHERE kid='effect-identity-key'"));
        }
        try (Connection c = worker()) {
            c.setAutoCommit(false);
            assertFalse(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, UUID.randomUUID()));
            c.rollback();
        }
        try (Connection owner = owner(); Statement sql = owner.createStatement()) {
            sql.execute("INSERT INTO signer_authorizations VALUES ('" + ISSUER + "','effect-identity-key','ACTIVE',0)");
            assertEquals(1, sql.executeUpdate("DELETE FROM principal_epochs WHERE principal_id=42"));
        }
        try (Connection c = worker()) {
            c.setAutoCommit(false);
            assertFalse(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, UUID.randomUUID()));
            assertTrue(effect(c, bob, "tenant-b", "project-b", BOB_TASK, UUID.randomUUID()));
            c.rollback();
        }
        assertEquals(0, countEffects());
    }

    @Test
    void restrictedWriterCannotChangeAuthorityAndRevokerCannotEraseGuardRows() throws Exception {
        try (Connection c = worker(); Statement sql = c.createStatement()) {
            SQLException e = assertThrows(SQLException.class, () ->
                sql.executeUpdate("UPDATE signer_authorizations SET state='REVOKED' WHERE kid='effect-identity-key'"));
            assertEquals("42501", e.getSQLState());
        }
        try (Connection c = revoker(); Statement sql = c.createStatement()) {
            SQLException e = assertThrows(SQLException.class, () ->
                sql.executeUpdate("DELETE FROM principal_epochs WHERE principal_id=42"));
            assertEquals("42501", e.getSQLState());
        }
    }

    @AfterAll
    static void dropDisposableFixtureAndAssertCleanup() throws Exception {
        if (schema == null) return;
        assertTrue(schema.matches("b62_effect_[0-9a-f]{32}"));
        try (Connection c = owner(); Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            s.execute("DROP ROLE IF EXISTS " + workerName);
            s.execute("DROP ROLE IF EXISTS " + revokerName);
            try (PreparedStatement verify = c.prepareStatement(
                    "SELECT COUNT(*) FROM pg_namespace WHERE nspname=?")) {
                verify.setString(1, schema);
                try (var rows = verify.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(0L, rows.getLong(1));
                }
            }
        }
    }
}
