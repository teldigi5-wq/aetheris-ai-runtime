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
    private static KeyPair fenceSigningKey;
    private static RuntimePinnedLocalFenceLeaseVerifier fenceVerifier;
    private static KeyPair workloadSigningKey;
    private static RuntimePinnedWorkloadIdentityVerifier workloadVerifier;
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
        fenceSigningKey = generator.generateKeyPair();
        fenceVerifier = new RuntimePinnedLocalFenceLeaseVerifier(
            publicKeyPem(fenceSigningKey), FENCE_KID, FENCE_ISSUER,
            FENCE_AUDIENCE, Clock.systemUTC());
        workloadSigningKey = generator.generateKeyPair();
        workloadVerifier = new RuntimePinnedWorkloadIdentityVerifier(
            publicKeyPem(workloadSigningKey), WORKLOAD_KID, WORKLOAD_ISSUER,
            WORKLOAD_AUDIENCE, Clock.systemUTC());
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

    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal signedJti(
            long uid, String jti) {
        return signedJtiWithScopes(uid, jti, List.of("orchestrator:read"));
    }

    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal signedJtiWithScopes(
            long uid, String jti, List<String> scopes) {
        Instant issued = Instant.now().minusSeconds(10);
        String token = Jwts.builder().header().keyId(KEY_ID).and()
            .subject("synthetic-owner-" + uid + "@example.invalid")
            .issuer(ISSUER).claim("aud", List.of(AUDIENCE))
            .claim("uid", uid).claim("role", "DEVELOPER")
            .claim("scopes", scopes)
            .id(jti).issuedAt(Date.from(issued))
            .expiration(Date.from(issued.plusSeconds(180)))
            .signWith(signingKey.getPrivate(), Jwts.SIG.RS256).compact();
        return verifier.verifyBearerForRevocation("Bearer " + token);
    }

    private static final String REVIEW_LOCAL_ENDPOINT = "b62-review-promoted";
    private static final long REVIEW_LOCAL_TERM = 7L;
    private static final String FENCE_ISSUER = "https://fence.aetheris.example.invalid";
    private static final String FENCE_AUDIENCE = "aetheris-runtime-fence-review";
    private static final String FENCE_KID = "b62-review-fence-key";
    private static final String REVIEW_WORKLOAD_A = "runtime-review-worker-a";
    private static final String REVIEW_WORKLOAD_B = "runtime-review-worker-b";
    private static final String WORKLOAD_ISSUER = "https://workload.aetheris.example.invalid";
    private static final String WORKLOAD_AUDIENCE = "aetheris-runtime-workload-review";
    private static final String WORKLOAD_KID = "b62-review-workload-key";

    private static String publicKeyPem(KeyPair key) {
        return "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(key.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
    }

    private static String signedFenceJwt(KeyPair signer, String endpoint,
            long term, String issuer, String audience, String kid,
            String purpose, String resource, Instant issued, Instant expires) {
        return signedFenceJwtBoundTo(signer, endpoint, term, issuer,
            audience, kid, purpose, resource, issued, expires, REVIEW_WORKLOAD_A);
    }

    private static String signedFenceJwtBoundTo(KeyPair signer, String endpoint,
            long term, String issuer, String audience, String kid,
            String purpose, String resource, Instant issued, Instant expires,
            String workloadSubject) {
        return Jwts.builder().header().keyId(kid).and()
            .subject("synthetic-resource-leader").issuer(issuer)
            .claim("aud", List.of(audience))
            .claim("purpose", purpose).claim("resource", resource)
            .claim("endpoint", endpoint).claim("term", term)
            .claim("workload", workloadSubject)
            .issuedAt(Date.from(issued)).expiration(Date.from(expires))
            .signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static String signedWorkloadJwt(KeyPair signer, String subject,
            String issuer, String audience, String kid, String purpose,
            String resource, Instant issued, Instant expires) {
        return Jwts.builder().header().keyId(kid).and()
            .subject(subject).issuer(issuer).claim("aud", List.of(audience))
            .claim("purpose", purpose).claim("resource", resource)
            .issuedAt(Date.from(issued)).expiration(Date.from(expires))
            .signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static RuntimePinnedWorkloadIdentityVerifier.VerifiedWorkload
            signedWorkload(String subject) {
        Instant now = Instant.now();
        return workloadVerifier.verifySignedWorkload(signedWorkloadJwt(
            workloadSigningKey, subject, WORKLOAD_ISSUER, WORKLOAD_AUDIENCE,
            WORKLOAD_KID, "runtime-workload-review", "runtime",
            now.minusSeconds(5), now.plusSeconds(55)));
    }

    private static RuntimePinnedLocalFenceLeaseVerifier.VerifiedLease signedFence(
            String endpoint, long term) {
        Instant now = Instant.now();
        return fenceVerifier.verifySignedLease(signedFenceJwt(
            fenceSigningKey, endpoint, term, FENCE_ISSUER, FENCE_AUDIENCE,
            FENCE_KID, "runtime-local-fence-review", "runtime",
            now.minusSeconds(5), now.plusSeconds(55)));
    }

    private static boolean fenced(
            RuntimeVerifiedTaskSessionReadProbe combined, Connection c,
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal signed,
            String tenant, String project, UUID id) {
        return combined.canReadSynthetic(c, signed, tenant, project, id,
            signedFence(REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM),
            signedWorkload(REVIEW_WORKLOAD_A));
    }

    @Test
    void independentSignedWorkloadRejectsForgeryScopeAndMismatchedLease()
            throws Exception {
        Instant now = Instant.now();
        Instant issued = now.minusSeconds(5);
        Instant expires = now.plusSeconds(55);
        String workloadA = signedWorkloadJwt(workloadSigningKey,
            REVIEW_WORKLOAD_A, WORKLOAD_ISSUER, WORKLOAD_AUDIENCE,
            WORKLOAD_KID, "runtime-workload-review", "runtime", issued, expires);
        var verifiedA = workloadVerifier.verifySignedWorkload(workloadA);
        var verifiedB = signedWorkload(REVIEW_WORKLOAD_B);
        assertEquals(REVIEW_WORKLOAD_A, verifiedA.subject());
        assertTrue(verifiedA.isFresh());
        assertTrue(verifiedB.isFresh());
        assertEquals(0,
            RuntimePinnedWorkloadIdentityVerifier.VerifiedWorkload.class
                .getConstructors().length);
        String validLeaseJwt = signedFenceJwt(fenceSigningKey,
            REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM, FENCE_ISSUER,
            FENCE_AUDIENCE, FENCE_KID, "runtime-local-fence-review",
            "runtime", issued, expires);
        var leaseA = fenceVerifier.verifySignedLease(validLeaseJwt);
        assertTrue(leaseA.isBoundTo(verifiedA));
        assertFalse(leaseA.isBoundTo(verifiedB),
            "Two valid signed objects MUST match the identical workload");
        assertFalse(leaseA.isBoundTo(null));

        String otherLeaseJwt = signedFenceJwtBoundTo(fenceSigningKey,
            REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM, FENCE_ISSUER,
            FENCE_AUDIENCE, FENCE_KID, "runtime-local-fence-review",
            "runtime", issued, expires, REVIEW_WORKLOAD_B);
        var leaseB = fenceVerifier.verifySignedLease(otherLeaseJwt);
        assertFalse(leaseB.isBoundTo(verifiedA));
        assertTrue(leaseB.isBoundTo(verifiedB));

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var attacker = generator.generateKeyPair();
        for (KeyPair wrongKey : List.of(attacker, fenceSigningKey)) {
            assertThrows(IllegalArgumentException.class,
                () -> workloadVerifier.verifySignedWorkload(signedWorkloadJwt(
                    wrongKey, REVIEW_WORKLOAD_A,
                    WORKLOAD_ISSUER, WORKLOAD_AUDIENCE, WORKLOAD_KID,
                    "runtime-workload-review", "runtime", issued, expires)),
                "Signing a workload token with an attacker or fence key DENIES");
        }

        for (String[] wrong : new String[][]{
                {"https://attacker.example.invalid", WORKLOAD_AUDIENCE,
                    WORKLOAD_KID, "runtime-workload-review", "runtime"},
                {WORKLOAD_ISSUER, "wrong-audience",
                    WORKLOAD_KID, "runtime-workload-review", "runtime"},
                {WORKLOAD_ISSUER, WORKLOAD_AUDIENCE,
                    "wrong-key-id", "runtime-workload-review", "runtime"},
                {WORKLOAD_ISSUER, WORKLOAD_AUDIENCE,
                    WORKLOAD_KID, "wrong-purpose", "runtime"},
                {WORKLOAD_ISSUER, WORKLOAD_AUDIENCE,
                    WORKLOAD_KID, "runtime-workload-review", "other-resource"}}) {
            assertThrows(IllegalArgumentException.class,
                () -> workloadVerifier.verifySignedWorkload(signedWorkloadJwt(
                    workloadSigningKey, REVIEW_WORKLOAD_A,
                    wrong[0], wrong[1], wrong[2], wrong[3], wrong[4],
                    issued, expires)));
        }
        for (String badSubject : List.of("../escape", "UPPER-SUBJECT")) {
            assertThrows(IllegalArgumentException.class,
                () -> workloadVerifier.verifySignedWorkload(signedWorkloadJwt(
                    workloadSigningKey, badSubject,
                    WORKLOAD_ISSUER, WORKLOAD_AUDIENCE, WORKLOAD_KID,
                    "runtime-workload-review", "runtime", issued, expires)));
        }
        assertThrows(IllegalArgumentException.class,
            () -> workloadVerifier.verifySignedWorkload("Bearer " + workloadA));
        assertThrows(IllegalArgumentException.class,
            () -> workloadVerifier.verifySignedWorkload(signedWorkloadJwt(
                workloadSigningKey, REVIEW_WORKLOAD_A,
                WORKLOAD_ISSUER, WORKLOAD_AUDIENCE, WORKLOAD_KID,
                "runtime-workload-review", "runtime",
                now.minusSeconds(5), now.plusSeconds(120))));
        assertThrows(IllegalArgumentException.class,
            () -> workloadVerifier.verifySignedWorkload(signedWorkloadJwt(
                workloadSigningKey, REVIEW_WORKLOAD_A,
                WORKLOAD_ISSUER, WORKLOAD_AUDIENCE, WORKLOAD_KID,
                "runtime-workload-review", "runtime",
                now.minusSeconds(30), now.minusSeconds(5))));
        assertThrows(IllegalArgumentException.class,
            () -> workloadVerifier.verifySignedWorkload(signedWorkloadJwt(
                workloadSigningKey, REVIEW_WORKLOAD_A,
                WORKLOAD_ISSUER, WORKLOAD_AUDIENCE, WORKLOAD_KID,
                "runtime-workload-review", "runtime",
                now.plusSeconds(5), now.plusSeconds(50))));

        AtomicReference<Instant> trustedNow = new AtomicReference<>(now);
        Clock advancing = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) {
                return Clock.fixed(trustedNow.get(), zone);
            }
            @Override public Instant instant() { return trustedNow.get(); }
        };
        var verifierWithClock = new RuntimePinnedWorkloadIdentityVerifier(
            publicKeyPem(workloadSigningKey), WORKLOAD_KID, WORKLOAD_ISSUER,
            WORKLOAD_AUDIENCE, advancing);
        var retained = verifierWithClock.verifySignedWorkload(workloadA);
        assertTrue(leaseA.isBoundTo(retained));
        trustedNow.set(now.plusSeconds(60));
        assertFalse(retained.isFresh());
        assertFalse(leaseA.isBoundTo(retained),
            "A retained workload assertion must DENY after signed expiry");
    }

    @Test
    void pinnedFenceAuthorityRejectsForgeryClaimsAndRetainedExpiry()
            throws Exception {
        Instant now = Instant.now();
        Instant issued = now.minusSeconds(5);
        Instant expires = now.plusSeconds(55);
        String valid = signedFenceJwt(fenceSigningKey, REVIEW_LOCAL_ENDPOINT,
            REVIEW_LOCAL_TERM, FENCE_ISSUER, FENCE_AUDIENCE, FENCE_KID,
            "runtime-local-fence-review", "runtime", issued, expires);
        var verified = fenceVerifier.verifySignedLease(valid);
        assertTrue(verified.isFresh());
        assertEquals(REVIEW_LOCAL_ENDPOINT, verified.endpoint());
        assertEquals(REVIEW_LOCAL_TERM, verified.term());
        assertEquals(0,
            RuntimePinnedLocalFenceLeaseVerifier.VerifiedLease.class
                .getConstructors().length,
            "No public constructor can forge a verified lease type");

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair untrusted = generator.generateKeyPair();
        assertThrows(IllegalArgumentException.class,
            () -> fenceVerifier.verifySignedLease(signedFenceJwt(
                untrusted, REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM,
                FENCE_ISSUER, FENCE_AUDIENCE, FENCE_KID,
                "runtime-local-fence-review", "runtime", issued, expires)),
            "Signed lease from a different RSA key must not authenticate");

        for (String[] wrong : new String[][]{
                {"https://attacker.example.invalid", FENCE_AUDIENCE, FENCE_KID,
                    "runtime-local-fence-review", "runtime"},
                {FENCE_ISSUER, "wrong-target", FENCE_KID,
                    "runtime-local-fence-review", "runtime"},
                {FENCE_ISSUER, FENCE_AUDIENCE, "untrusted-key-id",
                    "runtime-local-fence-review", "runtime"},
                {FENCE_ISSUER, FENCE_AUDIENCE, FENCE_KID,
                    "different-purpose", "runtime"},
                {FENCE_ISSUER, FENCE_AUDIENCE, FENCE_KID,
                    "runtime-local-fence-review", "other-resource"}}) {
            String invalid = signedFenceJwt(fenceSigningKey,
                REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM,
                wrong[0], wrong[1], wrong[2], wrong[3], wrong[4],
                issued, expires);
            assertThrows(IllegalArgumentException.class,
                () -> fenceVerifier.verifySignedLease(invalid),
                "Issuer, audience, kid, purpose and resource are all pinned");
        }
        for (String invalidEndpoint : List.of("UPPER-ENDPOINT", "../escape")) {
            assertThrows(IllegalArgumentException.class,
                () -> fenceVerifier.verifySignedLease(signedFenceJwt(
                    fenceSigningKey, invalidEndpoint, REVIEW_LOCAL_TERM,
                    FENCE_ISSUER, FENCE_AUDIENCE, FENCE_KID,
                    "runtime-local-fence-review", "runtime", issued, expires)));
        }
        assertThrows(IllegalArgumentException.class,
            () -> signedFence(REVIEW_LOCAL_ENDPOINT, 0));
        assertThrows(IllegalArgumentException.class,
            () -> fenceVerifier.verifySignedLease(signedFenceJwt(
                fenceSigningKey, REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM,
                FENCE_ISSUER, FENCE_AUDIENCE, FENCE_KID,
                "runtime-local-fence-review", "runtime",
                now.minusSeconds(5), now.plusSeconds(120))),
            "A 125-second lease must exceed the 90-second ceiling");
        assertThrows(IllegalArgumentException.class,
            () -> fenceVerifier.verifySignedLease(signedFenceJwt(
                fenceSigningKey, REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM,
                FENCE_ISSUER, FENCE_AUDIENCE, FENCE_KID,
                "runtime-local-fence-review", "runtime",
                now.minusSeconds(25), now.minusSeconds(5))),
            "An expired signed lease must never validate");
        assertThrows(IllegalArgumentException.class,
            () -> fenceVerifier.verifySignedLease(signedFenceJwt(
                fenceSigningKey, REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM,
                FENCE_ISSUER, FENCE_AUDIENCE, FENCE_KID,
                "runtime-local-fence-review", "runtime",
                now.plusSeconds(20), now.plusSeconds(50))),
            "Signed issuance in the future must not validate");
        assertThrows(IllegalArgumentException.class,
            () -> fenceVerifier.verifySignedLease("Bearer " + valid));

        AtomicReference<Instant> trustedNow = new AtomicReference<>(now);
        Clock advancing = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) {
                return Clock.fixed(trustedNow.get(), zone);
            }
            @Override public Instant instant() { return trustedNow.get(); }
        };
        var retainedVerifier = new RuntimePinnedLocalFenceLeaseVerifier(
            publicKeyPem(fenceSigningKey), FENCE_KID, FENCE_ISSUER,
            FENCE_AUDIENCE, advancing);
        var retained = retainedVerifier.verifySignedLease(valid);
        assertTrue(retained.isFresh());
        trustedNow.set(now.plusSeconds(60));
        assertFalse(retained.isFresh(),
            "Previously verified leases cannot be retained beyond expiry");
    }

    @Test
    void signedJtiTaskOwnerSessionAndEmergencyRevocationMustAgreeAtomically()
            throws Exception {
        // Three synthetic session/B62 tables exist only inside this
        // disposable JDBC connection and this rollback-only SAVEPOINT.
        Savepoint lab = connection.setSavepoint("joint_session_owner_lab");
        var combined = new RuntimeVerifiedTaskSessionReadProbe();
        try {
            try (var setup = connection.createStatement()) {
                setup.execute("""
                    CREATE TEMP TABLE synthetic_session_registration(
                      issuer text NOT NULL, kid text NOT NULL,
                      jti text NOT NULL, uid bigint NOT NULL,
                      iat_seconds bigint NOT NULL,
                      state text NOT NULL CHECK(state IN ('ACTIVE','REVOKED')),
                      PRIMARY KEY(issuer,kid,jti))
                    """);
                setup.execute("""
                    CREATE TEMP TABLE blocked_signer_kids(
                      issuer text NOT NULL, kid text NOT NULL,
                      PRIMARY KEY(issuer,kid))
                    """);
                setup.execute("""
                    CREATE TEMP TABLE principal_epochs(
                      principal_id bigint PRIMARY KEY,
                      reject_issued_at_or_before_epoch bigint NOT NULL)
                    """);
                setup.execute("""
                    CREATE TEMP TABLE b62_local_resource_fence(
                      resource_key text PRIMARY KEY,
                      endpoint text NOT NULL,
                      term bigint NOT NULL CHECK(term>0),
                      state text NOT NULL CHECK(state IN ('ACTIVE','FENCED')),
                      expires_at timestamptz NOT NULL)
                    """);
                setup.execute("""
                    CREATE TEMP TABLE synthetic_guarded_task_effects(
                      effect_id uuid PRIMARY KEY,
                      task_id uuid NOT NULL,
                      fence_term bigint NOT NULL CHECK(fence_term>0),
                      workload_subject text NOT NULL)
                    """);
            }

            // The test-only effect requires the already-defined write scope.
            // A second, separately registered JTI retains read-only rights.
            var alice = signedJtiWithScopes(42, UUID.randomUUID().toString(),
                List.of("orchestrator:read", "orchestrator:write"));
            var readOnlyAlice = signedJti(42, UUID.randomUUID().toString());
            var anotherAliceSession = signedJti(42, UUID.randomUUID().toString());
            var bob = signedJti(77, UUID.randomUUID().toString());
            var signed = alice.requireRevocationFacts();
            long iat = signed.signedIssuedAt().getEpochSecond();

            // Neither signed ownership nor absence of emergency blocks
            // substitutes for an explicitly registered active session.
            assertTrue(probe.canRead(connection, alice,
                "tenant-alpha", "project-red", ALICE_ALPHA));
            assertFalse(fenced(combined, connection, alice,
                "tenant-alpha", "project-red", ALICE_ALPHA));
            try (var register = connection.prepareStatement("""
                    INSERT INTO synthetic_session_registration
                      (issuer,kid,jti,uid,iat_seconds,state)
                    VALUES (?,?,?,?,?,'ACTIVE')
                    """)) {
                register.setString(1, signed.verifiedIssuer());
                register.setString(2, signed.verifiedKeyId());
                register.setString(3, signed.signedTokenId());
                register.setLong(4, signed.signedIdentityUid());
                register.setLong(5, iat);
                assertEquals(1, register.executeUpdate());
            }
            var readOnlyFacts = readOnlyAlice.requireRevocationFacts();
            try (var registerReadOnly = connection.prepareStatement("""
                    INSERT INTO synthetic_session_registration
                      (issuer,kid,jti,uid,iat_seconds,state)
                    VALUES (?,?,?,?,?,'ACTIVE')
                    """)) {
                registerReadOnly.setString(1, readOnlyFacts.verifiedIssuer());
                registerReadOnly.setString(2, readOnlyFacts.verifiedKeyId());
                registerReadOnly.setString(3, readOnlyFacts.signedTokenId());
                registerReadOnly.setLong(4, readOnlyFacts.signedIdentityUid());
                registerReadOnly.setLong(5, readOnlyFacts.signedIssuedAt().getEpochSecond());
                assertEquals(1, registerReadOnly.executeUpdate());
            }
            assertFalse(fenced(combined, connection, alice,
                "tenant-alpha", "project-red", ALICE_ALPHA),
                "An ACTIVE JTI and owner are insufficient without a resource fence");
            try (var local = connection.prepareStatement("""
                    INSERT INTO b62_local_resource_fence
                      (resource_key,endpoint,term,state,expires_at)
                    VALUES ('runtime',?,?,'ACTIVE',clock_timestamp()+interval '30 minutes')
                    """)) {
                local.setString(1, REVIEW_LOCAL_ENDPOINT);
                local.setLong(2, REVIEW_LOCAL_TERM);
                assertEquals(1, local.executeUpdate());
            }
            assertTrue(fenced(combined, connection, alice,
                "tenant-alpha", "project-red", ALICE_ALPHA));
            var guardedEffects = new RuntimeSyntheticFencedEffectProbe();
            // This is an ACTIVE signed owner/JTI with matching task and fence.
            // Its only missing capability is orchestrator:write.
            assertTrue(fenced(combined, connection, readOnlyAlice,
                "tenant-alpha", "project-red", ALICE_ALPHA),
                "Read-only signed JTI must still read its own task");
            assertFalse(guardedEffects.recordEffectSynthetic(connection, readOnlyAlice,
                "tenant-alpha", "project-red", ALICE_ALPHA, UUID.randomUUID(),
                signedFence(REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM),
                signedWorkload(REVIEW_WORKLOAD_A)),
                "Read-only signed JTI must never insert a protected effect");
            try (var noReadEffect = connection.createStatement().executeQuery(
                    "SELECT count(*) FROM synthetic_guarded_task_effects")) {
                assertTrue(noReadEffect.next());
                assertEquals(0L, noReadEffect.getLong(1),
                    "Read-only owner must produce zero synthetic effects");
            }
            var firstEffect = UUID.randomUUID();
            assertTrue(guardedEffects.recordEffectSynthetic(connection, alice,
                "tenant-alpha", "project-red", ALICE_ALPHA, firstEffect,
                signedFence(REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM),
                signedWorkload(REVIEW_WORKLOAD_A)),
                "Signed owner/session/workload and locked local fence must"
                    + " conditionally INSERT a disposable effect");
            assertFalse(guardedEffects.recordEffectSynthetic(connection, alice,
                "tenant-alpha", "project-red", ALICE_ALPHA, firstEffect,
                signedFence(REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM),
                signedWorkload(REVIEW_WORKLOAD_A)),
                "Duplicate effect_id must not repeat a pending mutation");
            assertFalse(guardedEffects.recordEffectSynthetic(connection, bob,
                "tenant-alpha", "project-red", ALICE_ALPHA, UUID.randomUUID(),
                signedFence(REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM),
                signedWorkload(REVIEW_WORKLOAD_A)),
                "A second valid signed UID cannot mutate another owner's task");
            assertFalse(guardedEffects.recordEffectSynthetic(connection,
                anotherAliceSession, "tenant-alpha", "project-red",
                ALICE_ALPHA, UUID.randomUUID(),
                signedFence(REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM),
                signedWorkload(REVIEW_WORKLOAD_A)),
                "Unregistered signed JTI cannot execute an effect");
            assertFalse(guardedEffects.recordEffectSynthetic(connection, alice,
                "tenant-alpha", "project-red", ALICE_ALPHA, UUID.randomUUID(),
                signedFence(REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM),
                signedWorkload(REVIEW_WORKLOAD_B)),
                "Different correctly signed workload must DENY effects");
            assertFalse(guardedEffects.recordEffectSynthetic(connection, alice,
                "tenant-alpha", "project-red", ALICE_ALPHA, UUID.randomUUID(),
                signedFence(REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM - 1),
                signedWorkload(REVIEW_WORKLOAD_A)),
                "Signed stale term cannot mutate resource with newer local term");
            assertFalse(guardedEffects.recordEffectSynthetic(connection, alice,
                "tenant-alpha", "project-red", LEGACY, UUID.randomUUID(),
                signedFence(REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM),
                signedWorkload(REVIEW_WORKLOAD_A)),
                "Legacy ownerless task must never produce protected effects");
            assertFalse(guardedEffects.recordEffectSynthetic(connection, alice,
                "tenant-alpha", "project-red", ALICE_ALPHA, UUID.randomUUID(),
                null, signedWorkload(REVIEW_WORKLOAD_A)),
                "Missing verified lease cannot execute an effect");
            try (var rows = connection.createStatement().executeQuery("""
                    SELECT task_id,fence_term,workload_subject
                    FROM synthetic_guarded_task_effects
                    """)) {
                assertTrue(rows.next());
                assertEquals(ALICE_ALPHA, rows.getObject(1, UUID.class));
                assertEquals(REVIEW_LOCAL_TERM, rows.getLong(2));
                assertEquals(REVIEW_WORKLOAD_A, rows.getString(3));
                assertFalse(rows.next(), "Exactly one disposable effect is pending");
            }
            assertFalse(combined.canReadSynthetic(connection, alice,
                "tenant-alpha", "project-red", ALICE_ALPHA,
                signedFence(REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM),
                signedWorkload(REVIEW_WORKLOAD_B)),
                "Two valid signatures with different workload subjects must DENY");
            assertFalse(combined.canReadSynthetic(connection, alice,
                "tenant-alpha", "project-red", ALICE_ALPHA,
                signedFence(REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM), null),
                "Missing independently signed workload identity must DENY");
            assertFalse(fenced(combined, connection, alice,
                "tenant-alpha", "project-red", LEGACY));
            assertFalse(fenced(combined, connection, alice,
                "tenant-alpha", "project-blue", BOB_ALPHA));
            assertFalse(fenced(combined, connection, alice,
                "tenant-beta", "project-red", ALICE_ALPHA));
            assertFalse(fenced(combined, connection, bob,
                "tenant-alpha", "project-red", ALICE_ALPHA));
            assertFalse(fenced(combined, connection, anotherAliceSession,
                "tenant-alpha", "project-red", ALICE_ALPHA),
                "An unregistered signed JTI must not borrow another valid JTI");
            assertFalse(fenced(combined, connection, alice,
                "invalid' OR true --", "project-red", ALICE_ALPHA));
            assertFalse(fenced(combined, connection,
                signed(42, List.of("orchestrator:read")),
                "tenant-alpha", "project-red", ALICE_ALPHA),
                "A signed token with no JTI cannot supply active-session proof");
            assertFalse(combined.canReadSynthetic(connection, alice,
                "tenant-alpha", "project-red", ALICE_ALPHA,
                signedFence("b62-review-other", REVIEW_LOCAL_TERM),
                signedWorkload(REVIEW_WORKLOAD_A)),
                "A different pinned endpoint must not match the local resource");
            assertFalse(combined.canReadSynthetic(connection, alice,
                "tenant-alpha", "project-red", ALICE_ALPHA,
                signedFence(REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM - 1),
                signedWorkload(REVIEW_WORKLOAD_A)),
                "Obsolete term must not match a current local fence");
            assertThrows(IllegalArgumentException.class,
                () -> signedFence(REVIEW_LOCAL_ENDPOINT, 0),
                "Nonpositive signed term must be rejected at signature proof");
            assertFalse(combined.canReadSynthetic(connection, alice,
                "tenant-alpha", "project-red", ALICE_ALPHA,
                null, signedWorkload(REVIEW_WORKLOAD_A)),
                "Missing signed authority must fail closed");
            try (var change = connection.createStatement()) {
                assertEquals(1, change.executeUpdate(
                    "UPDATE b62_local_resource_fence SET state='FENCED'"));
                assertFalse(fenced(combined, connection, alice,
                    "tenant-alpha", "project-red", ALICE_ALPHA),
                    "Explicitly fenced resource must deny every positive JTI");
                assertFalse(guardedEffects.recordEffectSynthetic(connection, alice,
                    "tenant-alpha", "project-red", ALICE_ALPHA, UUID.randomUUID(),
                    signedFence(REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM),
                    signedWorkload(REVIEW_WORKLOAD_A)),
                    "An explicitly fenced row cannot admit actual local writes");
                assertEquals(1, change.executeUpdate(
                    "UPDATE b62_local_resource_fence SET state='ACTIVE',"
                    + " expires_at=clock_timestamp()-interval '1 minute'"));
                assertFalse(fenced(combined, connection, alice,
                    "tenant-alpha", "project-red", ALICE_ALPHA),
                    "Expired local lease must deny even without JTI revocation");
                assertFalse(guardedEffects.recordEffectSynthetic(connection, alice,
                    "tenant-alpha", "project-red", ALICE_ALPHA, UUID.randomUUID(),
                    signedFence(REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM),
                    signedWorkload(REVIEW_WORKLOAD_A)),
                    "Expired local resource lease must not insert effects");
                assertEquals(1, change.executeUpdate(
                    "UPDATE b62_local_resource_fence SET "
                    + "expires_at=clock_timestamp()+interval '30 minutes',"
                    + "endpoint='b62-review-stale', term=6"));
                assertFalse(fenced(combined, connection, alice,
                    "tenant-alpha", "project-red", ALICE_ALPHA),
                    "A new expected term cannot accept a stale local row");
                assertTrue(combined.canReadSynthetic(connection, alice,
                    "tenant-alpha", "project-red", ALICE_ALPHA,
                    signedFence("b62-review-stale", 6),
                    signedWorkload(REVIEW_WORKLOAD_A)),
                    "NEGATIVE COUNTEREXAMPLE: an authentic signed stale lease"
                    + " still matches its LOCAL row, not global leadership");
                assertTrue(guardedEffects.recordEffectSynthetic(connection, alice,
                    "tenant-alpha", "project-red", ALICE_ALPHA, UUID.randomUUID(),
                    signedFence("b62-review-stale", 6),
                    signedWorkload(REVIEW_WORKLOAD_A)),
                    "UNSAFE COUNTEREXAMPLE: an authentic stale leader can still"
                        + " insert an effect into its own stale local resource");
                assertEquals(1, change.executeUpdate(
                    "UPDATE b62_local_resource_fence SET "
                    + "endpoint='b62-review-promoted', term=7"));
            }
            assertTrue(fenced(combined, connection, alice,
                "tenant-alpha", "project-red", ALICE_ALPHA));

            try (var block = connection.prepareStatement(
                    "INSERT INTO blocked_signer_kids VALUES (?,?)")) {
                block.setString(1, signed.verifiedIssuer());
                block.setString(2, signed.verifiedKeyId());
                assertEquals(1, block.executeUpdate());
            }
            assertFalse(fenced(combined, connection, alice,
                "tenant-alpha", "project-red", ALICE_ALPHA),
                "Emergency signer block must override active session");
            try (var unBlock = connection.createStatement()) {
                assertEquals(1, unBlock.executeUpdate(
                    "DELETE FROM blocked_signer_kids"));
            }

            try (var cutoff = connection.prepareStatement(
                    "INSERT INTO principal_epochs VALUES (?,?)")) {
                cutoff.setLong(1, signed.signedIdentityUid());
                cutoff.setLong(2, iat);
                assertEquals(1, cutoff.executeUpdate());
            }
            assertFalse(fenced(combined, connection, alice,
                "tenant-alpha", "project-red", ALICE_ALPHA),
                "Signed issue second equal to emergency cutoff must DENY");
            try (var olderCutoff = connection.prepareStatement(
                    "UPDATE principal_epochs SET reject_issued_at_or_before_epoch=?")) {
                olderCutoff.setLong(1, iat - 1);
                assertEquals(1, olderCutoff.executeUpdate());
            }
            assertTrue(fenced(combined, connection, alice,
                "tenant-alpha", "project-red", ALICE_ALPHA),
                "Only issuance strictly after cutoff can pass");
            try (var revoke = connection.createStatement()) {
                assertEquals(1, revoke.executeUpdate(
                    "UPDATE synthetic_session_registration SET state='REVOKED'"));
            }
            assertFalse(fenced(combined, connection, alice,
                "tenant-alpha", "project-red", ALICE_ALPHA),
                "Individually revoked JTI must deny despite eligible owner");
            assertFalse(guardedEffects.recordEffectSynthetic(connection, alice,
                "tenant-alpha", "project-red", ALICE_ALPHA, UUID.randomUUID(),
                signedFence(REVIEW_LOCAL_ENDPOINT, REVIEW_LOCAL_TERM),
                signedWorkload(REVIEW_WORKLOAD_A)),
                "Revoked session cannot insert a protected effect");
            try (var rows = connection.createStatement().executeQuery(
                    "SELECT count(*) FROM synthetic_guarded_task_effects")) {
                assertTrue(rows.next());
                assertEquals(2L, rows.getLong(1),
                    "One authorized effect + one deliberately unsafe stale effect");
            }

            try (Connection auto = DriverManager.getConnection(
                    "jdbc:postgresql://127.0.0.1:5432/ownerlab_disposable",
                    "ownerlab_ci", System.getenv("PGPASSWORD"))) {
                assertTrue(auto.getAutoCommit());
                assertFalse(fenced(combined, auto, alice,
                    "tenant-alpha", "project-red", ALICE_ALPHA),
                    "Implicit autocommit cannot pass composite admission");
            }
        } finally {
            connection.rollback(lab);
        }
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
