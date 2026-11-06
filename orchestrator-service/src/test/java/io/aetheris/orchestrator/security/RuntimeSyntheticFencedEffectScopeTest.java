package io.aetheris.orchestrator.security;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review-only signed-token scope regression. No live SQL, routes, or writes.
 * Proves read-only scope cannot reach even the JDBC admission precheck.
 */
class RuntimeSyntheticFencedEffectScopeTest {
    private static final String PRINCIPAL_ISSUER = "https://identity.aetheris.example.invalid";
    private static final String WORKLOAD_ISSUER = "https://workload.aetheris.example.invalid";
    private static final String FENCE_ISSUER = "https://fencer.aetheris.example.invalid";
    private static KeyPair identityKey;
    private static KeyPair workloadKey;
    private static KeyPair fenceKey;
    private static RuntimePinnedPrincipalVerifier principalVerifier;
    private static RuntimePinnedWorkloadIdentityVerifier workloadVerifier;
    private static RuntimePinnedLocalFenceLeaseVerifier leaseVerifier;

    @BeforeAll
    static void signedDomains() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        identityKey = gen.generateKeyPair();
        workloadKey = gen.generateKeyPair();
        fenceKey = gen.generateKeyPair();
        Clock clock = Clock.systemUTC();
        principalVerifier = new RuntimePinnedPrincipalVerifier(
            pem(identityKey), "identity-key", PRINCIPAL_ISSUER, "runtime-api", clock);
        workloadVerifier = new RuntimePinnedWorkloadIdentityVerifier(
            pem(workloadKey), "workload-key", WORKLOAD_ISSUER, "runtime-workload", clock);
        leaseVerifier = new RuntimePinnedLocalFenceLeaseVerifier(
            pem(fenceKey), "fence-key", FENCE_ISSUER, "runtime-fence", clock);
    }

    private static String pem(KeyPair pair) {
        return "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(pair.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
    }

    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal(List<String> scopes) {
        Instant issued = Instant.now().minusSeconds(5);
        String jwt = Jwts.builder().header().keyId("identity-key").and()
            .subject("synthetic-owner@example.invalid")
            .issuer(PRINCIPAL_ISSUER).claim("aud", List.of("runtime-api"))
            .claim("uid", 42L).claim("role", "DEVELOPER")
            .claim("scopes", scopes).id(UUID.randomUUID().toString())
            .issuedAt(Date.from(issued))
            .expiration(Date.from(issued.plusSeconds(180)))
            .signWith(identityKey.getPrivate(), Jwts.SIG.RS256).compact();
        return principalVerifier.verifyBearerForRevocation("Bearer " + jwt);
    }

    private static RuntimePinnedWorkloadIdentityVerifier.VerifiedWorkload workload() {
        Instant issued = Instant.now().minusSeconds(5);
        String jwt = Jwts.builder().header().keyId("workload-key").and()
            .subject("review-worker").issuer(WORKLOAD_ISSUER)
            .claim("aud", List.of("runtime-workload"))
            .claim("purpose", "runtime-workload-review")
            .claim("resource", "runtime")
            .issuedAt(Date.from(issued))
            .expiration(Date.from(issued.plusSeconds(60)))
            .signWith(workloadKey.getPrivate(), Jwts.SIG.RS256).compact();
        return workloadVerifier.verifySignedWorkload(jwt);
    }

    private static RuntimePinnedLocalFenceLeaseVerifier.VerifiedLease lease() {
        Instant issued = Instant.now().minusSeconds(5);
        String jwt = Jwts.builder().header().keyId("fence-key").and()
            .issuer(FENCE_ISSUER).claim("aud", List.of("runtime-fence"))
            .claim("purpose", "runtime-local-fence-review")
            .claim("resource", "runtime")
            .claim("endpoint", "old-primary").claim("term", 2)
            .claim("workload", "review-worker")
            .issuedAt(Date.from(issued))
            .expiration(Date.from(issued.plusSeconds(60)))
            .signWith(fenceKey.getPrivate(), Jwts.SIG.RS256).compact();
        return leaseVerifier.verifySignedLease(jwt);
    }

    @Test
    void unsignedWriteScopeCannotBeInferredFromReadOnlyOrWriteOnlyToken()
            throws Exception {
        var signedWorkload = workload();
        var signedLease = lease();
        assertTrue(signedLease.isBoundTo(signedWorkload));
        var probe = new RuntimeSyntheticFencedEffectProbe();
        var calls = new AtomicInteger();
        Connection connection = (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[]{Connection.class},
            (proxy, method, args) -> {
                if ("getAutoCommit".equals(method.getName())) {
                    calls.incrementAndGet();
                    return true; // Explicitly deny SQL even for fully scoped principal.
                }
                throw new AssertionError("Unexpected JDBC call: " + method.getName());
            });

        var readOnly = principal(List.of("orchestrator:read"));
        assertTrue(readOnly.hasScope("orchestrator:read"));
        assertFalse(readOnly.hasScope("orchestrator:write"));
        assertFalse(probe.recordEffectSynthetic(connection, readOnly, "tenant-a",
            "project-a", UUID.randomUUID(), UUID.randomUUID(), signedLease, signedWorkload));
        assertEquals(0, calls.get(), "A read-only token must never reach JDBC");

        var writeOnly = principal(List.of("orchestrator:write"));
        assertFalse(probe.recordEffectSynthetic(connection, writeOnly, "tenant-a",
            "project-a", UUID.randomUUID(), UUID.randomUUID(), signedLease, signedWorkload));
        assertEquals(0, calls.get(), "A write-only scope is not object ownership proof");

        var readWrite = principal(List.of("orchestrator:read", "orchestrator:write"));
        assertTrue(readWrite.hasScope("orchestrator:read"));
        assertTrue(readWrite.hasScope("orchestrator:write"));
        assertFalse(probe.recordEffectSynthetic(connection, readWrite, "tenant-a",
            "project-a", UUID.randomUUID(), UUID.randomUUID(), signedLease, signedWorkload));
        assertEquals(1, calls.get(), "Fully scoped call must still fail on auto-commit");
    }
}
