package io.aetheris.orchestrator.security;

import io.aetheris.orchestrator.api.OwnerlessTaskMissionHttpFilter;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real TCP/embedded-Tomcat HTTP boundary check on 127.0.0.1, never an
 * internet-facing deployment. The ONLY bearer-protected route is test-only.
 * Session authority is an in-memory TEST STUB; this does NOT certify actual
 * durable session revocation, HTTPS, mTLS, or authenticated Task/Mission APIs.
 */
@SpringBootTest(
    classes=RuntimeDirectPortBearerSocketTest.IsolatedApp.class,
    webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={
        "server.address=127.0.0.1",
        "server.port=0",
        "server.servlet.context-path=/runtime",
        "management.endpoints.enabled-by-default=false",
        "spring.task.scheduling.enabled=false"
    })
class RuntimeDirectPortBearerSocketTest {
    private static final String ISSUER="https://identity.aetheris.example.invalid";
    private static final String AUDIENCE="aetheris-orchestrator-api";
    private static final String KID="runtime-socket-review-key";
    private static final String PROBE="/__runtime_auth_probe";
    private static final KeyPair KEY=makeKey();
    private static final KeyPair OTHER_KEY=makeKey();
    private static final HttpClient CLIENT=HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(3))
        .build();

    @LocalServerPort int port;
    @Autowired TestOnlySessions sessions;

    private static KeyPair makeKey() {
        try {
            var generator=KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            return generator.generateKeyPair();
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static String pem(KeyPair pair) {
        return "-----BEGIN PUBLIC KEY-----\n" +
            Base64.getMimeEncoder(64,new byte[]{'\n'})
                .encodeToString(pair.getPublic().getEncoded()) +
            "\n-----END PUBLIC KEY-----";
    }

    private record SignedToken(String bearer, String jti, long uid) {}

    private static SignedToken issue(KeyPair signer,String kid,String issuer,
                                     String audience,long uid,List<String> scopes,
                                     long startsAgoSeconds,long lastsSeconds,
                                     boolean includeJti) {
        String jti="socket_review_"+UUID.randomUUID();
        Instant issued=Instant.now().minusSeconds(startsAgoSeconds);
        var builder=Jwts.builder().header().keyId(kid).and()
            .subject("synthetic-"+uid+"@example.invalid").issuer(issuer)
            .claim("aud",List.of(audience)).claim("uid",uid)
            .claim("role","DEVELOPER").claim("scopes",scopes)
            .issuedAt(Date.from(issued))
            .expiration(Date.from(issued.plusSeconds(lastsSeconds)));
        if(includeJti) builder.id(jti);
        return new SignedToken("Bearer "+
            builder.signWith(signer.getPrivate(),Jwts.SIG.RS256).compact(),
            jti,uid);
    }

    private static SignedToken valid(long uid) {
        return issue(KEY,KID,ISSUER,AUDIENCE,uid,
            List.of("orchestrator:read"),10,180,true);
    }

    static final class TestOnlySessions
        implements RuntimeSignedIdentityHttpFilter.SessionAuthority {
        private final ConcurrentHashMap<String,Long> allowed=new ConcurrentHashMap<>();
        private final AtomicBoolean down=new AtomicBoolean(false);
        void allow(SignedToken token) { allowed.put(token.jti(),token.uid()); }
        void revoke(SignedToken token) { allowed.remove(token.jti()); }
        @Override
        public boolean isActive(
                RuntimePinnedPrincipalVerifier.VerifiedRevocationFacts signed) {
            if(down.get()) throw new IllegalStateException("TEST_ONLY_REGISTRY_DOWN");
            return ISSUER.equals(signed.verifiedIssuer())
                && KID.equals(signed.verifiedKeyId())
                && signed.signedIdentityUid() ==
                    allowed.getOrDefault(signed.signedTokenId(),-1L);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude={
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
    })
    @Import({ OwnerlessTaskMissionHttpFilter.class, ProbeRoutes.class })
    static class IsolatedApp {
        @Bean TestOnlySessions disposableSessionAuthority() {
            return new TestOnlySessions();
        }
        @Bean FilterRegistrationBean<RuntimeSignedIdentityHttpFilter>
                testOnlyFilter(TestOnlySessions active) {
            var verifier=new RuntimePinnedPrincipalVerifier(
                pem(KEY),KID,ISSUER,AUDIENCE,Clock.systemUTC());
            var registration=new FilterRegistrationBean<
                RuntimeSignedIdentityHttpFilter>();
            registration.setFilter(new RuntimeSignedIdentityHttpFilter(
                verifier,active,"orchestrator:read"));
            // This EXACT URL is not a product endpoint. No task/mission
            // filter registration, production config or component annotation.
            registration.addUrlPatterns(PROBE);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE+20);
            return registration;
        }
    }

    @RestController
    static class ProbeRoutes {
        @GetMapping(PROBE)
        String testOnly(HttpServletRequest request) {
            Object checked=request.getAttribute(
                RuntimeSignedIdentityHttpFilter.VERIFIED_PRINCIPAL_ATTRIBUTE);
            if(!(checked instanceof RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal)
                    || !principal.isFresh()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return "TEST_ONLY_VERIFIED_UID_"+principal.identityUid();
        }

        @GetMapping("/api/orchestrator/tasks")
        String stillQuarantined() {
            return "SHOULD_NEVER_REACH_TASK_CONTROLLER";
        }
    }

    private HttpResponse<String> get(String path,Map<String,String> headers)
            throws Exception {
        HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create(
            "http://127.0.0.1:"+port+"/runtime"+path))
            .timeout(Duration.ofSeconds(8)).GET();
        headers.forEach(builder::header);
        return CLIENT.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }

    private void assertDenied(String path,Map<String,String> headers,int expected)
            throws Exception {
        var response=get(path,headers);
        assertEquals(expected,response.statusCode(),path);
        assertEquals("no-store",
            response.headers().firstValue("Cache-Control").orElse(null));
        assertEquals("nosniff",
            response.headers().firstValue("X-Content-Type-Options").orElse(null));
        assertFalse(response.body().contains("TEST_ONLY_VERIFIED_UID_"));
        assertFalse(response.body().contains("SHOULD_NEVER_REACH_TASK_CONTROLLER"));
        assertFalse(response.body().contains("TEST_ONLY_REGISTRY_DOWN"));
    }

    @Test
    void twoRealSignedPrincipalsPassDirectPortOnlyWithIndependentActiveSession()
            throws Exception {
        var alice=valid(42);var bob=valid(77);
        sessions.allow(alice);sessions.allow(bob);
        try {
            var one=get(PROBE,Map.of("Authorization",alice.bearer()));
            var two=get(PROBE,Map.of("Authorization",bob.bearer()));
            assertEquals(200,one.statusCode());
            assertEquals(200,two.statusCode());
            assertEquals("TEST_ONLY_VERIFIED_UID_42",one.body());
            assertEquals("TEST_ONLY_VERIFIED_UID_77",two.body());
            sessions.revoke(alice);
            assertDenied(PROBE,Map.of("Authorization",alice.bearer()),401);
            assertEquals(200,get(PROBE,Map.of("Authorization",bob.bearer())).statusCode());
        } finally {
            sessions.revoke(alice);sessions.revoke(bob);
        }
    }

    @Test
    void invalidMissingExpiredMismatchedOrUnregisteredJwtIsDenied()
            throws Exception {
        assertDenied(PROBE,Map.of(),401);
        assertDenied(PROBE,Map.of("Authorization","Bearer forged.invalid.token"),401);
        var unregistered=valid(100); // Valid signature alone is NOT enough.
        assertDenied(PROBE,Map.of("Authorization",unregistered.bearer()),401);
        for(var jwt:List.of(
                issue(OTHER_KEY,KID,ISSUER,AUDIENCE,42,
                    List.of("orchestrator:read"),10,180,true),
                issue(KEY,"other-kid",ISSUER,AUDIENCE,42,
                    List.of("orchestrator:read"),10,180,true),
                issue(KEY,KID,"https://wrong.invalid",AUDIENCE,42,
                    List.of("orchestrator:read"),10,180,true),
                issue(KEY,KID,ISSUER,"other-service",42,
                    List.of("orchestrator:read"),10,180,true),
                issue(KEY,KID,ISSUER,AUDIENCE,42,
                    List.of("orchestrator:read"),600,60,true),
                issue(KEY,KID,ISSUER,AUDIENCE,42,
                    List.of("orchestrator:read"),10,180,false)
                )) {
            assertDenied(PROBE,Map.of("Authorization",jwt.bearer()),401);
        }
    }

    @Test
    void directPortRejectsForgedHeadersAndWrongSignedScope() throws Exception {
        var alice=valid(42);
        var writeOnly=issue(KEY,KID,ISSUER,AUDIENCE,42,
            List.of("orchestrator:write"),10,180,true);
        sessions.allow(alice);sessions.allow(writeOnly);
        try {
            assertDenied(PROBE,Map.of(
                "Authorization",alice.bearer(),
                "X-Aetheris-Owner","root"),401);
            assertDenied(PROBE,Map.of(
                "Authorization",alice.bearer(),
                "X-Forwarded-User","admin"),401);
            assertDenied(PROBE,Map.of(
                "Authorization",writeOnly.bearer()),403);
            // A direct request cannot bypass the existing Task API
            // quarantine even with a good, registered signed token.
            assertDenied("/api/orchestrator/tasks",
                Map.of("Authorization",alice.bearer()),404);
        } finally {
            sessions.revoke(alice);sessions.revoke(writeOnly);
        }
    }

    @Test
    void unavailableAuthorityAndMissingSessionCheckFailClosed() throws Exception {
        var alice=valid(42);sessions.allow(alice);
        try {
            sessions.down.set(true);
            assertDenied(PROBE,Map.of("Authorization",alice.bearer()),503);
        } finally {
            sessions.down.set(false);
            sessions.revoke(alice);
        }
        var verifier=new RuntimePinnedPrincipalVerifier(
            pem(KEY),KID,ISSUER,AUDIENCE,Clock.systemUTC());
        var noRegistry=new RuntimeSignedIdentityHttpFilter(
            verifier,"orchestrator:read");
        assertNotNull(noRegistry);
        // The no-registries constructor deliberately uses DENY ALL.
        org.springframework.mock.web.MockHttpServletRequest request=
            new org.springframework.mock.web.MockHttpServletRequest("GET",PROBE);
        request.addHeader("Authorization",alice.bearer());
        var response=new org.springframework.mock.web.MockHttpServletResponse();
        var reached=new AtomicBoolean(false);
        noRegistry.doFilter(request,response,(req,res)->reached.set(true));
        assertEquals(401,response.getStatus());
        assertFalse(reached.get());
    }

    @Test
    void duplicateAuthorizationHeadersAreNotAccepted() throws Exception {
        var alice=valid(42);sessions.allow(alice);
        try {
            HttpRequest request=HttpRequest.newBuilder(URI.create(
                "http://127.0.0.1:"+port+"/runtime"+PROBE))
                .timeout(Duration.ofSeconds(8)).GET()
                .header("Authorization",alice.bearer())
                .header("Authorization",alice.bearer()).build();
            var result=CLIENT.send(request,HttpResponse.BodyHandlers.ofString());
            assertEquals(401,result.statusCode());
        } finally {
            sessions.revoke(alice);
        }
    }
}
