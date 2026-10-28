package io.aetheris.orchestrator.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual signed RSA/HS tokens, not mocked JWT fields or spoofable headers.
 * Does NOT call or open the intentionally locked-down HTTP task APIs.
 */
class RuntimePinnedPrincipalVerifierTest {
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String AUDIENCE = "aetheris-orchestrator-api";
    private static final String KID = "runtime-ci-key-1";
    private static KeyPair identityKey;
    private static KeyPair unrelatedKey;
    private static RuntimePinnedPrincipalVerifier verifier;

    @BeforeAll
    static void keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        identityKey = generator.generateKeyPair();
        unrelatedKey = generator.generateKeyPair();
        verifier = new RuntimePinnedPrincipalVerifier(
            pem(identityKey), KID, ISSUER, AUDIENCE, Clock.systemUTC());
    }

    private static String pem(KeyPair pair) {
        return "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(pair.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
    }

    private static String issue(KeyPair key, String kid, String issuer,
                                List<String> audiences, Object uid,
                                String role, Object scopes, long issuedOffset,
                                long durationSeconds) {
        Instant issued = Instant.now().plusSeconds(issuedOffset);
        return Jwts.builder().header().keyId(kid).and()
            .subject("alice@my.sliit.lk")
            .issuer(issuer)
            .claim("aud", audiences)
            .claim("uid", uid)
            .claim("role", role)
            .claim("scopes", scopes)
            .issuedAt(Date.from(issued))
            .expiration(Date.from(issued.plusSeconds(durationSeconds)))
            .signWith(key.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static String valid() {
        return issue(identityKey, KID, ISSUER, List.of(AUDIENCE), 42,
            "DEVELOPER", List.of("orchestrator:read", "orchestrator:write"),
            -10, 300);
    }

    private static void denied(String jwt) {
        assertThrows(IllegalArgumentException.class,
            () -> verifier.verifyBearer("Bearer " + jwt));
    }

    @Test
    void signedIdentityPrincipalIsReadOnlyScopedObjectIdentityNotOwnership() {
        var principal = verifier.verifyBearer("Bearer " + valid());
        assertEquals(42L, principal.identityUid());
        assertEquals("alice@my.sliit.lk", principal.subject());
        assertEquals("DEVELOPER", principal.role());
        assertTrue(principal.hasScope("orchestrator:read"));
        assertTrue(principal.hasScope("orchestrator:write"));
        assertFalse(principal.hasScope("identity:write"));
        assertThrows(UnsupportedOperationException.class,
            () -> principal.scopes().add("identity:write"));
    }

    @Test
    void rejectsForgedSignatureWrongKidWrongIssuerAndConfusedAudiences() {
        denied(issue(unrelatedKey, KID, ISSUER, List.of(AUDIENCE), 42,
            "DEVELOPER", List.of("orchestrator:read"), -10, 300));
        denied(issue(identityKey, "spoofed-kid", ISSUER, List.of(AUDIENCE), 42,
            "DEVELOPER", List.of("orchestrator:read"), -10, 300));
        denied(issue(identityKey, KID, "https://untrusted.invalid", List.of(AUDIENCE), 42,
            "DEVELOPER", List.of("orchestrator:read"), -10, 300));
        denied(issue(identityKey, KID, ISSUER, List.of("aetheris-user-api"), 42,
            "DEVELOPER", List.of("orchestrator:read"), -10, 300));
        denied(issue(identityKey, KID, ISSUER,
            List.of(AUDIENCE, "aetheris-user-api"), 42,
            "DEVELOPER", List.of("orchestrator:read"), -10, 300));
        denied(issue(identityKey, KID, ISSUER, List.of(), 42,
            "DEVELOPER", List.of("orchestrator:read"), -10, 300));
    }

    @Test
    void rejectsRoleEscalationScopeSmugglingAndInvalidIdentityNumbers() {
        denied(issue(identityKey, KID, ISSUER, List.of(AUDIENCE), 1,
            "API_CONSUMER", List.of("orchestrator:write"), -10, 300));
        denied(issue(identityKey, KID, ISSUER, List.of(AUDIENCE), 1,
            "ADMIN", List.of("orchestrator:read", "unrecognized:admin"), -10, 300));
        denied(issue(identityKey, KID, ISSUER, List.of(AUDIENCE), 1,
            "SUPERUSER", List.of("orchestrator:read"), -10, 300));
        denied(issue(identityKey, KID, ISSUER, List.of(AUDIENCE), 0,
            "ADMIN", List.of("orchestrator:read"), -10, 300));
        denied(issue(identityKey, KID, ISSUER, List.of(AUDIENCE), "42",
            "ADMIN", List.of("orchestrator:read"), -10, 300));
        denied(issue(identityKey, KID, ISSUER, List.of(AUDIENCE), 42,
            "ADMIN", List.of("orchestrator:read", "orchestrator:read"), -10, 300));
        denied(issue(identityKey, KID, ISSUER, List.of(AUDIENCE), 42,
            "ADMIN", List.of("orchestrator:read", 1), -10, 300));
        denied(issue(identityKey, KID, ISSUER, List.of(AUDIENCE), 42,
            "ADMIN", List.of(), -10, 300));
    }

    @Test
    void rejectsExpiredFutureIssuedOrUnreasonablyLongTokens() {
        denied(issue(identityKey, KID, ISSUER, List.of(AUDIENCE), 42,
            "DEVELOPER", List.of("orchestrator:read"), -600, 60));
        denied(issue(identityKey, KID, ISSUER, List.of(AUDIENCE), 42,
            "DEVELOPER", List.of("orchestrator:read"), 180, 300));
        denied(issue(identityKey, KID, ISSUER, List.of(AUDIENCE), 42,
            "DEVELOPER", List.of("orchestrator:read"), -10, 90000));
        denied(issue(identityKey, KID, ISSUER, List.of(AUDIENCE), 42,
            "DEVELOPER", List.of("orchestrator:read"), -10, 0));
    }

    @Test
    void refusesMalformedBearerAndHmacInsteadOfRs256() {
        assertThrows(IllegalArgumentException.class,
            () -> verifier.verifyBearer(null));
        for (String input : List.of("", "Bearer", "bearer " + valid(),
                "Bearer " + valid() + " ", "Bearer  " + valid(),
                "Token " + valid(), "Bearer aaa.bbb.ccc")) {
            assertThrows(IllegalArgumentException.class, () -> verifier.verifyBearer(input));
        }
        String hmac = Jwts.builder().subject("alice@my.sliit.lk")
            .claim("uid", 42).claim("role", "ADMIN")
            .claim("scopes", List.of("orchestrator:write"))
            .issuer(ISSUER).claim("aud", List.of(AUDIENCE))
            .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(120)))
            .signWith(Keys.hmacShaKeyFor(
                "this-is-a-32-byte-or-longer-test-only-secret".getBytes(StandardCharsets.UTF_8)),
                Jwts.SIG.HS256).compact();
        denied(hmac);
    }

    @Test
    void neverAcceptsMissingWeakOrUnpinnedOperatorKeys() throws Exception {
        for (String kid : List.of("", "bad kid", "https://untrusted.invalid/kid")) {
            assertThrows(IllegalArgumentException.class,
                () -> new RuntimePinnedPrincipalVerifier(
                    pem(identityKey), kid, ISSUER, AUDIENCE, Clock.systemUTC()));
        }
        assertThrows(IllegalArgumentException.class,
            () -> new RuntimePinnedPrincipalVerifier(
                "not a PEM", KID, ISSUER, AUDIENCE, Clock.systemUTC()));
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        var weak = generator.generateKeyPair();
        assertThrows(IllegalArgumentException.class,
            () -> new RuntimePinnedPrincipalVerifier(
                pem(weak), KID, ISSUER, AUDIENCE, Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class,
            () -> new RuntimePinnedPrincipalVerifier(
                pem(identityKey), KID, "", AUDIENCE, Clock.systemUTC()));
    }
}
