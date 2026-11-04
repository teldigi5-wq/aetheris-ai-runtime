package io.aetheris.orchestrator.security;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.*;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Review-only cross-repository compatibility proof. Platform B62-G1 PR #302,
 * 9f286b65af1fa18c6dfa6064ebf6afe6cdccacac, SQL blob
 * f15e76206ceae81b2a68d950ee98a0c5cdafc657: principal_epochs and
 * blocked_signer_kids, NOT a per-JTI session allowlist.
 *
 * This test is NOT a production B62 schema, API, verifier, live session
 * registry, privileged reader design or Runtime route admission.
 */
class RuntimeB62EpochSignerCompatibilityTest {
    private static final String ISS = "https://identity.aetheris.example.invalid";
    private static final String AUD = "aetheris-orchestrator-api";
    private static final String KID = "s0-b62-compatibility";
    private static String schema;
    private static String password;
    // Separate network-authenticated, SELECT-only disposable PostgreSQL role.
    // This is not a deployed Runtime service account.
    private static String readerRole;
    private static String readerPassword;
    private static KeyPair signer;
    private static KeyPair attacker;
    private static RuntimePinnedPrincipalVerifier verifier;
    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal earlier;
    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal later;
    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal other;

    @BeforeAll static void init() throws Exception {
        Assumptions.assumeTrue("yes".equals(System.getenv("AETHERIS_MEMBERSHIP_JDBC_MUST_RUN")),
            "Requires disposable GitHub PostgreSQL16 CI");
        assertEquals("true", System.getenv("GITHUB_ACTIONS"));
        assertEquals("yes", System.getenv("AETHERIS_OWNER_SCHEMA_DISPOSABLE_CI_ONLY"));
        assertEquals("127.0.0.1", System.getenv("PGHOST"));
        assertEquals("ownerlab_disposable", System.getenv("PGDATABASE"));
        assertEquals("ownerlab_ci", System.getenv("PGUSER"));
        password = System.getenv("PGPASSWORD");
        assertNotNull(password);
        assertFalse(password.isBlank());

        var keys = KeyPairGenerator.getInstance("RSA");
        keys.initialize(2048);
        signer = keys.generateKeyPair();
        attacker = keys.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(signer.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
        verifier = new RuntimePinnedPrincipalVerifier(
            pem, KID, ISS, AUD, Clock.systemUTC());

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        earlier = verify(signer, 42L, now.minusSeconds(35));
        later = verify(signer, 42L, now.minusSeconds(4));
        other = verify(signer, 77L, now.minusSeconds(35));
        schema = "ownerlab_b62_" + UUID.randomUUID().toString().replace("-", "");
        assertTrue(schema.matches("ownerlab_b62_[a-f0-9]{32}"));
        readerRole = "ownerlab_b62r_" + UUID.randomUUID().toString().replace("-", "");
        readerPassword = UUID.randomUUID().toString().replace("-", "");
        assertTrue(readerRole.matches("ownerlab_b62r_[a-f0-9]{32}"));
        try (Connection c = connect(); var ddl = c.createStatement()) {
            ddl.execute("CREATE SCHEMA " + schema);
            // Shape and cutoff inclusivity are taken from the immutable B62-G1
            // prototype, but built only inside a separate disposable schema.
            ddl.execute("""
                CREATE TABLE principal_epochs(
                  principal_id bigint PRIMARY KEY CHECK(principal_id > 0),
                  reject_issued_at_or_before_epoch bigint NOT NULL CHECK(
                      reject_issued_at_or_before_epoch > 0))
                """);
            ddl.execute("""
                CREATE TABLE blocked_signer_kids(
                  issuer text NOT NULL, kid text NOT NULL,
                  reason_code text NOT NULL DEFAULT 'compromise',
                  PRIMARY KEY(issuer,kid))
                """);
            // NOT part of Platform B62-G1. Independent synthetic session
            // authority, required to demonstrate nonrevocation != admission.
            ddl.execute("""
                CREATE TABLE synthetic_session_registration(
                  issuer text NOT NULL, kid text NOT NULL,
                  jti text NOT NULL, uid bigint NOT NULL,
                  iat_seconds bigint NOT NULL, state text NOT NULL
                    CHECK(state IN ('ACTIVE','REVOKED')),
                  PRIMARY KEY(issuer,kid,jti))
                """);
            // The PostgreSQL service owner creates one ephemeral LOGIN
            // role with no inherited object permissions. Only SELECT is
            // granted on this lab schema's three synthetic tables.
            ddl.execute("CREATE ROLE " + readerRole
                + " LOGIN PASSWORD '" + readerPassword + "'");
            ddl.execute("GRANT USAGE ON SCHEMA " + schema + " TO " + readerRole);
            ddl.execute("GRANT SELECT ON TABLE "
                + schema + ".principal_epochs, "
                + schema + ".blocked_signer_kids, "
                + schema + ".synthetic_session_registration TO " + readerRole);
        }
    }

    private static Connection connect() throws SQLException {
        var c = DriverManager.getConnection(
            "jdbc:postgresql://127.0.0.1:5432/ownerlab_disposable",
            "ownerlab_ci", password);
        if (schema != null) c.setSchema(schema);
        return c;
    }

    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal verify(
            KeyPair key, long uid, Instant issued) {
        String bearer = "Bearer " + Jwts.builder().header().keyId(KID).and()
            .subject("synthetic-" + uid + "@example.invalid")
            .issuer(ISS).claim("aud", List.of(AUD))
            .claim("uid", uid).claim("role", "DEVELOPER")
            .claim("scopes", List.of("orchestrator:read"))
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(issued))
            .expiration(Date.from(issued.plusSeconds(300)))
            .signWith(key.getPrivate(), Jwts.SIG.RS256).compact();
        return verifier.verifyBearerForRevocation(bearer);
    }

    @BeforeEach void reset() throws Exception {
        try (Connection c = connect(); var stmt = c.createStatement()) {
            stmt.execute("TRUNCATE principal_epochs, blocked_signer_kids,"
                + " synthetic_session_registration");
        }
    }

    private static Connection reader() throws SQLException {
        // Different real PostgreSQL login over TCP, NOT JDBC readOnly
        // masquerading as least privilege on the ownerlab_ci superuser.
        var c = DriverManager.getConnection(
            "jdbc:postgresql://127.0.0.1:5432/ownerlab_disposable",
            readerRole, readerPassword);
        c.setSchema(schema);
        c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        c.setReadOnly(true);
        c.setAutoCommit(false);
        return c;
    }

    // Mirrors B62-G1's POST-signature emergency cutoffs only. A positive
    // Boolean is NOT a session-valid or resource-authorized ALLOW verdict.
    private static boolean notEmergencyBlocked(Connection c,
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal) {
        try {
            if (c == null || !c.isReadOnly() || c.getAutoCommit()) return false;
            var signed = principal.requireRevocationFacts();
            long iat = signed.signedIssuedAt().getEpochSecond();
            if (iat <= 0 || signed.signedIdentityUid() <= 0
                    || signed.verifiedIssuer().length() > 120
                    || signed.verifiedKeyId().length() > 64) return false;
            try (PreparedStatement q = c.prepareStatement("""
                SELECT current_setting('transaction_isolation')='read committed'
                AND NOT EXISTS (SELECT 1 FROM blocked_signer_kids
                  WHERE issuer=? AND kid=?)
                AND NOT EXISTS (SELECT 1 FROM principal_epochs
                  WHERE principal_id=?
                  AND ? <= reject_issued_at_or_before_epoch)
                """)) {
                q.setString(1, signed.verifiedIssuer());
                q.setString(2, signed.verifiedKeyId());
                q.setLong(3, signed.signedIdentityUid());
                q.setLong(4, iat);
                try (var row = q.executeQuery()) {
                    return row.next() && row.getBoolean(1);
                }
            }
        } catch (SQLException | RuntimeException failure) {
            return false;
        }
    }

    private static long monotonicCutoff(long uid, long at) throws Exception {
        try (Connection c = connect();
             PreparedStatement up = c.prepareStatement("""
               INSERT INTO principal_epochs AS existing(
                  principal_id,reject_issued_at_or_before_epoch) VALUES (?,?)
               ON CONFLICT(principal_id) DO UPDATE
               SET reject_issued_at_or_before_epoch=GREATEST(
                 existing.reject_issued_at_or_before_epoch,
                 EXCLUDED.reject_issued_at_or_before_epoch)
               RETURNING reject_issued_at_or_before_epoch
               """)) {
            up.setLong(1, uid);
            up.setLong(2, at);
            try (var row = up.executeQuery()) {
                assertTrue(row.next());
                return row.getLong(1);
            }
        }
    }

    @Test void independentlyAuthenticatedReaderCannotWriteOrAlterSchema()
            throws Exception {
        // The read service identity is an actual PostgreSQL LOGIN with
        // SELECT-only privileges. Test forbidden mutations WITHOUT setting
        // JDBC readOnly, so permission denials come from the server grants.
        try (Connection probe = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:5432/ownerlab_disposable",
                readerRole, readerPassword)) {
            probe.setSchema(schema);
            assertFalse(probe.isReadOnly());
            try (PreparedStatement query = probe.prepareStatement("""
                    SELECT current_user, session_user,
                      has_schema_privilege(current_user, ?, 'USAGE'),
                      has_schema_privilege(current_user, ?, 'CREATE'),
                      has_table_privilege(current_user, ?, 'SELECT'),
                      has_table_privilege(current_user, ?, 'INSERT'),
                      has_table_privilege(current_user, ?, 'UPDATE'),
                      has_table_privilege(current_user, ?, 'DELETE')
                    """)) {
                query.setString(1, schema);
                query.setString(2, schema);
                // Exactly six JDBC placeholders: two schema flags and
                // four table privileges. Bind each table independently so
                // no grant assertion is accidentally checked on the owner.
                query.setString(3, schema + ".principal_epochs");
                query.setString(4, schema + ".principal_epochs");
                query.setString(5, schema + ".blocked_signer_kids");
                query.setString(6, schema + ".synthetic_session_registration");
                try (var row = query.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals(readerRole, row.getString(1));
                    assertEquals(readerRole, row.getString(2));
                    assertTrue(row.getBoolean(3), "Reader must see review-only schema");
                    assertFalse(row.getBoolean(4), "Reader must not CREATE schema objects");
                    assertTrue(row.getBoolean(5), "Reader must SELECT emergency cutoffs");
                    assertFalse(row.getBoolean(6), "Reader must not insert cutoffs");
                    assertFalse(row.getBoolean(7), "Reader must not update signer blocks");
                    assertFalse(row.getBoolean(8), "Reader must not delete sessions");
                }
            }
            for (String deniedSql : List.of(
                    "INSERT INTO principal_epochs VALUES (9001,9001)",
                    "UPDATE blocked_signer_kids SET reason_code='tamper'",
                    "DELETE FROM synthetic_session_registration",
                    "CREATE TABLE forbidden_writer_table(id bigint)")) {
                try (var denied = probe.createStatement()) {
                    SQLException error = assertThrows(SQLException.class,
                        () -> denied.executeUpdate(deniedSql));
                    assertEquals("42501", error.getSQLState(),
                        "PostgreSQL must deny write/DDL by actual role grants");
                }
            }
        }
        try (Connection live = reader()) {
            assertTrue(live.isReadOnly());
            assertFalse(syntheticComposedDecision(live, earlier),
                "Unregistered signed session stays denied via restricted reader");
            assertTrue(notEmergencyBlocked(live, earlier),
                "SELECT-only reader can evaluate B62 predicate");
            live.rollback();
        }
    }

    @Test void inclusiveIatCutoffIsMonotonicAndUidIsolated() throws Exception {
        long a = earlier.requireRevocationFacts().signedIssuedAt().getEpochSecond();
        long b = later.requireRevocationFacts().signedIssuedAt().getEpochSecond();
        assertTrue(b > a);
        try (Connection c = reader()) {
            assertTrue(notEmergencyBlocked(c, earlier));
            assertTrue(notEmergencyBlocked(c, later));
            assertEquals(a, monotonicCutoff(42, a));
            assertFalse(notEmergencyBlocked(c, earlier));
            assertTrue(notEmergencyBlocked(c, later));
            assertTrue(notEmergencyBlocked(c, other));
            assertEquals(a, monotonicCutoff(42, a - 1));
            assertEquals(b, monotonicCutoff(42, b));
            assertFalse(notEmergencyBlocked(c, later));
            assertTrue(notEmergencyBlocked(c, other));
            c.rollback();
        }
    }

    @Test void signedJtiNeverOverridesBlockedSigner() throws Exception {
        try (Connection c = reader()) {
            assertTrue(notEmergencyBlocked(c, earlier));
            // Platform B62-G1 has no JTI allowlist: this is only absence
            // of an emergency block, not independent session authorization.
            assertNotEquals(earlier.requireRevocationFacts().signedTokenId(),
                later.requireRevocationFacts().signedTokenId());
            try (Connection writer = connect();
                 PreparedStatement q = writer.prepareStatement("""
                   INSERT INTO blocked_signer_kids(issuer,kid,reason_code)
                   VALUES (?,?,'compromise')
                   ON CONFLICT(issuer,kid) DO NOTHING
                   """)) {
                q.setString(1, ISS);
                q.setString(2, KID);
                assertEquals(1, q.executeUpdate());
            }
            assertFalse(notEmergencyBlocked(c, earlier));
            assertFalse(notEmergencyBlocked(c, later));
            assertFalse(notEmergencyBlocked(c, other));
            assertThrows(IllegalArgumentException.class,
                () -> verify(attacker, 42L, Instant.now().minusSeconds(15)));
            c.rollback();
        }
    }


    /**
     * Review-only composition of TWO independent synthetic authorities:
     * (1) exact signed-jti session registration/activation, and (2) the
     * genuine B62-G1 emergency epoch and signer-block predicates.
     * One READ COMMITTED PostgreSQL statement is critical: a successful
     * emergency NOT-blocked result alone is NEVER an admission decision.
     * This is NOT a deployed Identity session store or Runtime API filter.
     */
    private static boolean syntheticComposedDecision(Connection c,
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal) {
        try {
            if (c == null || !c.isReadOnly() || c.getAutoCommit()) return false;
            var verified = principal.requireRevocationFacts();
            long issued = verified.signedIssuedAt().getEpochSecond();
            if (issued <= 0 || verified.signedIdentityUid() <= 0
                    || verified.verifiedIssuer().length() > 120
                    || verified.verifiedKeyId().length() > 64) return false;
            try (PreparedStatement sql = c.prepareStatement("""
                SELECT current_setting('transaction_isolation')='read committed'
                  AND EXISTS (
                    SELECT 1 FROM synthetic_session_registration active
                    WHERE active.issuer=? AND active.kid=? AND active.jti=?
                      AND active.uid=? AND active.iat_seconds=?
                      AND active.state='ACTIVE')
                  AND NOT EXISTS (
                    SELECT 1 FROM blocked_signer_kids
                    WHERE issuer=? AND kid=?)
                  AND NOT EXISTS (
                    SELECT 1 FROM principal_epochs
                    WHERE principal_id=?
                      AND ? <= reject_issued_at_or_before_epoch)
                """)) {
                sql.setString(1, verified.verifiedIssuer());
                sql.setString(2, verified.verifiedKeyId());
                sql.setString(3, verified.signedTokenId());
                sql.setLong(4, verified.signedIdentityUid());
                sql.setLong(5, issued);
                sql.setString(6, verified.verifiedIssuer());
                sql.setString(7, verified.verifiedKeyId());
                sql.setLong(8, verified.signedIdentityUid());
                sql.setLong(9, issued);
                try (var rows = sql.executeQuery()) {
                    return rows.next() && rows.getBoolean(1);
                }
            }
        } catch (RuntimeException | SQLException unavailableOrInvalid) {
            return false;
        }
    }

    private static void registerSyntheticSession(
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal)
            throws Exception {
        var f = principal.requireRevocationFacts();
        try (Connection writer = connect();
             PreparedStatement insert = writer.prepareStatement("""
                 INSERT INTO synthetic_session_registration
                 (issuer,kid,jti,uid,iat_seconds,state)
                 VALUES (?,?,?,?,?,'ACTIVE')
                 """)) {
            insert.setString(1, f.verifiedIssuer());
            insert.setString(2, f.verifiedKeyId());
            insert.setString(3, f.signedTokenId());
            insert.setLong(4, f.signedIdentityUid());
            insert.setLong(5, f.signedIssuedAt().getEpochSecond());
            assertEquals(1, insert.executeUpdate());
        }
    }

    @Test void emergencyNonrevocationNeverSubstitutesForActiveSignedSession()
            throws Exception {
        try (Connection read = reader()) {
            // A valid RSA signature with no emergency block still has NO
            // verified active session. This must never authorize a resource.
            assertTrue(notEmergencyBlocked(read, earlier));
            assertFalse(syntheticComposedDecision(read, earlier));
            assertFalse(syntheticComposedDecision(read, later));
            assertFalse(syntheticComposedDecision(read, other));

            registerSyntheticSession(earlier);
            registerSyntheticSession(other);
            assertTrue(syntheticComposedDecision(read, earlier));
            assertFalse(syntheticComposedDecision(read, later),
                "Same user, different signed JTI is not registered");
            assertTrue(syntheticComposedDecision(read, other));

            // Separate committed writer revokes the registered JTI. The
            // B62-only NOT-blocked function remains TRUE: it cannot
            // independently enforce a session logout.
            try (Connection writer = connect();
                 PreparedStatement revoke = writer.prepareStatement("""
                     UPDATE synthetic_session_registration
                     SET state='REVOKED'
                     WHERE issuer=? AND kid=? AND jti=?
                     """)) {
                var f = earlier.requireRevocationFacts();
                revoke.setString(1, f.verifiedIssuer());
                revoke.setString(2, f.verifiedKeyId());
                revoke.setString(3, f.signedTokenId());
                assertEquals(1, revoke.executeUpdate());
            }
            assertTrue(notEmergencyBlocked(read, earlier),
                "B62 emergency blocks do not register individual JTI logout");
            assertFalse(syntheticComposedDecision(read, earlier));
            assertTrue(syntheticComposedDecision(read, other));

            registerSyntheticSession(later);
            assertTrue(syntheticComposedDecision(read, later));
            long cutoff = later.requireRevocationFacts()
                .signedIssuedAt().getEpochSecond();
            assertEquals(cutoff, monotonicCutoff(42L, cutoff));
            assertFalse(syntheticComposedDecision(read, later),
                "B62 cutoff at exact signed issue second must deny");
            assertTrue(syntheticComposedDecision(read, other));

            // Independent signer compromise denies even actively registered
            // unrelated sessions; no signed JTI is an override.
            try (Connection writer = connect();
                 PreparedStatement block = writer.prepareStatement("""
                     INSERT INTO blocked_signer_kids(issuer,kid,reason_code)
                     VALUES (?,?,'compromise')
                     """)) {
                block.setString(1, ISS);
                block.setString(2, KID);
                assertEquals(1, block.executeUpdate());
            }
            assertFalse(syntheticComposedDecision(read, later));
            assertFalse(syntheticComposedDecision(read, other));
            read.rollback();
        }
        try (Connection unavailable = reader()) {
            unavailable.close();
            assertFalse(syntheticComposedDecision(unavailable, other));
        }
    }

    @Test void commitBoundarySnapshotAndConnectionFailureDeny() throws Exception {
        try (Connection c = reader(); Connection writer = connect()) {
            writer.setAutoCommit(false);
            assertTrue(notEmergencyBlocked(c, earlier));
            try (PreparedStatement revoke = writer.prepareStatement(
                    "INSERT INTO principal_epochs VALUES (?,?)")) {
                revoke.setLong(1, 42L);
                revoke.setLong(2, earlier.requireRevocationFacts()
                    .signedIssuedAt().getEpochSecond());
                assertEquals(1, revoke.executeUpdate());
            }
            assertTrue(notEmergencyBlocked(c, earlier),
                "An uncommitted transaction is not a published revocation");
            writer.commit();
            assertFalse(notEmergencyBlocked(c, earlier),
                "Next READ COMMITTED statement must observe the revocation");
            assertTrue(notEmergencyBlocked(c, other));
            c.rollback();
        }
        try (Connection snapshot = connect()) {
            snapshot.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            snapshot.setReadOnly(true);
            snapshot.setAutoCommit(false);
            assertFalse(notEmergencyBlocked(snapshot, other));
            snapshot.rollback();
        }
        try (Connection closed = reader()) {
            closed.close();
            assertFalse(notEmergencyBlocked(closed, other));
        }
    }

    @AfterAll static void cleanUp() throws Exception {
        if (schema == null) return;
        assertTrue(schema.matches("ownerlab_b62_[a-f0-9]{32}"));
        try (Connection c = connect(); var drop = c.createStatement()) {
            drop.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            // Schema drop removes grants; role then has no remaining objects.
            assertTrue(readerRole.matches("ownerlab_b62r_[a-f0-9]{32}"));
            drop.execute("DROP ROLE " + readerRole);
            try (PreparedStatement roleCheck = c.prepareStatement(
                    "SELECT COUNT(*) FROM pg_roles WHERE rolname=?")) {
                roleCheck.setString(1, readerRole);
                try (var rows = roleCheck.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(0L, rows.getLong(1));
                }
            }
            try (PreparedStatement query = c.prepareStatement(
                    "SELECT COUNT(*) FROM pg_namespace WHERE nspname=?")) {
                query.setString(1, schema);
                try (var rows = query.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(0L, rows.getLong(1));
                }
            }
        }
    }
}
