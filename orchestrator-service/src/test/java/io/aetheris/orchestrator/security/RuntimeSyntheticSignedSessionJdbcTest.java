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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REVIEW-ONLY disposable PostgreSQL16 simulation of a future session
 * revocation contract. This is NOT the authoritative B62 store, its schema,
 * a Spring bean, a production permission decision, or live HTTP/SSE admission.
 * All values are synthetic; the CI database owner is not a least-privilege
 * session reader. No production task, tenant or identity data is accessed.
 */
class RuntimeSyntheticSignedSessionJdbcTest {
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String AUDIENCE = "aetheris-orchestrator-api";
    private static final String KID = "s0-synthetic-session-jdbc";
    private static String schema;
    private static String password;
    private static KeyPair signer;
    private static KeyPair untrustedSigner;
    private static RuntimePinnedPrincipalVerifier verifier;
    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal alice;
    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal bob;

    @BeforeAll
    static void createDisposableSchema() throws Exception {
        Assumptions.assumeTrue("yes".equals(System.getenv("AETHERIS_MEMBERSHIP_JDBC_MUST_RUN")),
            "Requires explicitly opted-in disposable PostgreSQL16 GitHub CI");
        assertEquals("true", System.getenv("GITHUB_ACTIONS"));
        assertEquals("yes", System.getenv("AETHERIS_OWNER_SCHEMA_DISPOSABLE_CI_ONLY"));
        assertEquals("127.0.0.1", System.getenv("PGHOST"));
        assertEquals("ownerlab_disposable", System.getenv("PGDATABASE"));
        assertEquals("ownerlab_ci", System.getenv("PGUSER"));
        password = System.getenv("PGPASSWORD");
        assertNotNull(password);
        assertFalse(password.isBlank());

        KeyPairGenerator keys = KeyPairGenerator.getInstance("RSA");
        keys.initialize(2048);
        signer = keys.generateKeyPair();
        untrustedSigner = keys.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(signer.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
        verifier = new RuntimePinnedPrincipalVerifier(
            pem, KID, ISSUER, AUDIENCE, Clock.systemUTC());

        alice = verifier.verifyBearerForRevocation(
            token(signer, 42L, UUID.randomUUID().toString()));
        bob = verifier.verifyBearerForRevocation(
            token(signer, 77L, UUID.randomUUID().toString()));

        // Identifier is generated locally and restricted to lowercase hex.
        schema = "ownerlab_jti_" + UUID.randomUUID().toString().replace("-", "");
        assertTrue(schema.matches("ownerlab_jti_[a-f0-9]{32}"));
        try (Connection c = database(); var sql = c.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            sql.execute("""
                CREATE TABLE synthetic_signers (
                    issuer text NOT NULL, kid text NOT NULL,
                    enabled boolean NOT NULL,
                    PRIMARY KEY (issuer, kid)
                )
                """);
            sql.execute("""
                CREATE TABLE synthetic_epochs (
                    issuer text NOT NULL, uid bigint NOT NULL,
                    min_iat_seconds bigint NOT NULL,
                    PRIMARY KEY (issuer, uid)
                )
                """);
            sql.execute("""
                CREATE TABLE synthetic_sessions (
                    issuer text NOT NULL, kid text NOT NULL,
                    jti text NOT NULL, uid bigint NOT NULL,
                    iat_seconds bigint NOT NULL, status text NOT NULL,
                    revoked_at timestamptz,
                    PRIMARY KEY (issuer, kid, jti),
                    CHECK ((status='ACTIVE' AND revoked_at IS NULL) OR
                           (status='REVOKED' AND revoked_at IS NOT NULL))
                )
                """);
        }
    }

    private static Connection database() throws SQLException {
        Connection c = DriverManager.getConnection(
            "jdbc:postgresql://127.0.0.1:5432/ownerlab_disposable",
            "ownerlab_ci", password);
        if (schema != null) c.setSchema(schema);
        return c;
    }

    private static String token(KeyPair key, long uid, String jti) {
        Instant issued = Instant.now().minusSeconds(10);
        return "Bearer " + Jwts.builder().header().keyId(KID).and()
            .subject("synthetic-" + uid + "@example.invalid")
            .issuer(ISSUER).claim("aud", List.of(AUDIENCE))
            .claim("uid", uid).claim("role", "DEVELOPER")
            .claim("scopes", List.of("orchestrator:read"))
            .id(jti)
            .issuedAt(Date.from(issued))
            .expiration(Date.from(issued.plusSeconds(300)))
            .signWith(key.getPrivate(), Jwts.SIG.RS256).compact();
    }

    @BeforeEach
    void seedUnrelatedSyntheticAuthorities() throws Exception {
        try (Connection c = database(); var sql = c.createStatement()) {
            sql.execute("DELETE FROM synthetic_sessions");
            sql.execute("DELETE FROM synthetic_epochs");
            sql.execute("DELETE FROM synthetic_signers");
            try (PreparedStatement signerRow = c.prepareStatement(
                    "INSERT INTO synthetic_signers VALUES (?,?,true)")) {
                signerRow.setString(1, ISSUER);
                signerRow.setString(2, KID);
                assertEquals(1, signerRow.executeUpdate());
            }
            for (var principal : List.of(alice, bob)) {
                var f = principal.requireRevocationFacts();
                try (PreparedStatement epoch = c.prepareStatement(
                        "INSERT INTO synthetic_epochs VALUES (?,?,?)")) {
                    epoch.setString(1, f.verifiedIssuer());
                    epoch.setLong(2, f.signedIdentityUid());
                    epoch.setLong(3, f.signedIssuedAt().getEpochSecond() - 1);
                    assertEquals(1, epoch.executeUpdate());
                }
                try (PreparedStatement session = c.prepareStatement(
                        "INSERT INTO synthetic_sessions VALUES (?,?,?,?,?,'ACTIVE',NULL)")) {
                    session.setString(1, f.verifiedIssuer());
                    session.setString(2, f.verifiedKeyId());
                    session.setString(3, f.signedTokenId());
                    session.setLong(4, f.signedIdentityUid());
                    session.setLong(5, f.signedIssuedAt().getEpochSecond());
                    assertEquals(1, session.executeUpdate());
                }
            }
        }
    }

    private static Connection readTransaction() throws SQLException {
        Connection c = database();
        c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        c.setReadOnly(true);
        c.setAutoCommit(false);
        return c;
    }

    /**
     * Synthetic boolean-only reference decision. No allow decision may be
     * inferred for actual runtime APIs. SQL and signed facts are read again
     * for each call. Failed/missing authorities always return false.
     */
    private static boolean syntheticDecision(Connection connection,
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal) {
        try {
            if (connection == null || !connection.isReadOnly()
                    || connection.getAutoCommit()) return false;
            var f = principal.requireRevocationFacts();
            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT EXISTS (
                        SELECT 1 FROM synthetic_sessions s
                        JOIN synthetic_signers signer
                          ON signer.issuer=s.issuer AND signer.kid=s.kid
                        JOIN synthetic_epochs epoch
                          ON epoch.issuer=s.issuer AND epoch.uid=s.uid
                        WHERE current_setting('transaction_isolation') = 'read committed'
                          AND s.issuer=? AND s.kid=? AND s.jti=?
                          AND s.uid=? AND s.iat_seconds=?
                          AND s.status='ACTIVE' AND s.revoked_at IS NULL
                          AND signer.enabled
                          AND s.iat_seconds >= epoch.min_iat_seconds
                    )
                    """)) {
                query.setString(1, f.verifiedIssuer());
                query.setString(2, f.verifiedKeyId());
                query.setString(3, f.signedTokenId());
                query.setLong(4, f.signedIdentityUid());
                query.setLong(5, f.signedIssuedAt().getEpochSecond());
                try (var rows = query.executeQuery()) {
                    if (!rows.next()) return false;
                    return rows.getBoolean(1);
                }
            }
        } catch (RuntimeException | SQLException unavailableOrInvalid) {
            // No exception details, token identifiers or SQL leave this method.
            return false;
        }
    }

    private static void change(String sql) throws SQLException {
        try (Connection owner = database(); var statement = owner.createStatement()) {
            assertEquals(1, statement.executeUpdate(sql));
        }
    }

    @Test
    void signedJtiMustMatchSessionUidIssuerKidAndIssueTime() throws Exception {
        try (Connection read = readTransaction()) {
            assertTrue(syntheticDecision(read, alice));
            assertTrue(syntheticDecision(read, bob));
            var unseen = verifier.verifyBearerForRevocation(
                token(signer, 42L, UUID.randomUUID().toString()));
            assertFalse(syntheticDecision(read, unseen),
                "An unregistered genuine signed JTI must fail closed");
            var reused = verifier.verifyBearerForRevocation(
                token(signer, 77L, alice.requireRevocationFacts().signedTokenId()));
            assertFalse(syntheticDecision(read, reused),
                "Reusing another UID's JTI must never reuse its session row");
            assertThrows(IllegalArgumentException.class,
                () -> verifier.verifyBearerForRevocation(
                    token(untrustedSigner, 42L,
                        alice.requireRevocationFacts().signedTokenId())));
            read.rollback();
        }
    }

    @Test
    void sessionRevocationSignerBlockAndPrincipalEpochDenyWithoutCrossUserGrant()
            throws Exception {
        try (Connection read = readTransaction()) {
            assertTrue(syntheticDecision(read, alice));
            change("UPDATE synthetic_sessions SET status='REVOKED',"
                + " revoked_at=clock_timestamp() WHERE uid=42");
            assertFalse(syntheticDecision(read, alice));
            assertTrue(syntheticDecision(read, bob));

            change("UPDATE synthetic_signers SET enabled=false");
            assertFalse(syntheticDecision(read, alice));
            assertFalse(syntheticDecision(read, bob));

            change("UPDATE synthetic_signers SET enabled=true");
            assertTrue(syntheticDecision(read, bob));
            long aliceIssued = alice.requireRevocationFacts()
                .signedIssuedAt().getEpochSecond();
            try (Connection writer = database();
                 PreparedStatement changeEpoch = writer.prepareStatement(
                    "UPDATE synthetic_epochs SET min_iat_seconds=? WHERE uid=42")) {
                changeEpoch.setLong(1, aliceIssued + 1);
                assertEquals(1, changeEpoch.executeUpdate());
            }
            assertFalse(syntheticDecision(read, alice));
            assertTrue(syntheticDecision(read, bob));
            read.rollback();
        }
    }

    @Test
    void committedRevocationIsNextStatementVisibleAndStoreFailureDenies()
            throws Exception {
        try (Connection reader = readTransaction();
             Connection writer = database()) {
            writer.setAutoCommit(false);
            assertTrue(syntheticDecision(reader, alice));
            try (var sql = writer.createStatement()) {
                assertEquals(1, sql.executeUpdate(
                    "UPDATE synthetic_sessions SET status='REVOKED',"
                    + " revoked_at=clock_timestamp() WHERE uid=42"));
            }
            // READ COMMITTED sees the old committed state until writer COMMIT.
            assertTrue(syntheticDecision(reader, alice));
            writer.commit();
            assertFalse(syntheticDecision(reader, alice),
                "Next independent statement must observe committed revocation");
            assertTrue(syntheticDecision(reader, bob));
            reader.rollback();
        }
        try (Connection closed = readTransaction()) {
            closed.close();
            assertFalse(syntheticDecision(closed, alice),
                "Unavailable/stale authority must never produce an allow verdict");
        }
        try (Connection snapshot = database()) {
            snapshot.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            snapshot.setReadOnly(true);
            snapshot.setAutoCommit(false);
            assertFalse(syntheticDecision(snapshot, bob),
                "Long-lived snapshot isolation is not an acceptable authority read");
            snapshot.rollback();
        }
    }

    @AfterAll
    static void removeDisposableSchemaAndVerifyAbsence() throws Exception {
        if (schema == null) return;
        assertTrue(schema.matches("ownerlab_jti_[a-f0-9]{32}"));
        try (Connection c = database(); var statement = c.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            try (PreparedStatement check = c.prepareStatement(
                    "SELECT COUNT(*) FROM pg_namespace WHERE nspname=?")) {
                check.setString(1, schema);
                try (var rows = check.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(0L, rows.getLong(1),
                        "Synthetic signed-session schema must never survive CI");
                }
            }
        }
    }
}
