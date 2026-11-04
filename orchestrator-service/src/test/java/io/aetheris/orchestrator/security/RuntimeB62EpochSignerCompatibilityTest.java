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
            stmt.execute("TRUNCATE principal_epochs, blocked_signer_kids");
        }
    }

    private static Connection reader() throws SQLException {
        var c = connect();
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
