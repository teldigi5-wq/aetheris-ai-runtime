package io.aetheris.orchestrator.security;

import io.aetheris.orchestrator.api.OwnerlessTaskMissionHttpFilter;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real Spring Boot Test web server, RANDOM_PORT actual loopback TCP and
 * production-source OwnerlessTaskMissionHttpFilter. Test-only filter wiring
 * is NOT imported by AetherisOrchestratorApplication in any runtime profile.
 *
 * This deliberately minimal @SpringBootConfiguration EXCLUDES JPA/DataSource
 * and unrelated real production controllers/workers. It verifies servlet
 * integration and ordering only, NOT the complete Aetheris application.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = RuntimePinnedSpringBootHttpIntegrationTest.IsolatedS0TestApplication.class,
    properties = {
        "server.address=127.0.0.1",
        "server.port=0",
        "spring.application.name=s0-e2-test-only",
        "management.endpoints.enabled-by-default=false"
    })
class RuntimePinnedSpringBootHttpIntegrationTest {
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String AUDIENCE = "aetheris-orchestrator-api";
    private static final String KID = "s0-springboot-loopback-only";
    private static final KeyPair TRUSTED = newRsaKey();
    private static final KeyPair ATTACKER = newRsaKey();

    // Counters catch accidental handler entry even if a filter later masks status.
    private static final AtomicInteger PROBE_DISPATCHES = new AtomicInteger();
    private static final AtomicInteger PROTECTED_DISPATCHES = new AtomicInteger();

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext context;

    private static KeyPair newRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception unexpected) {
            throw new IllegalStateException("Test RSA fixture unavailable", unexpected);
        }
    }

    private static RuntimePinnedPrincipalVerifier testVerifier() {
        String pem = "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(TRUSTED.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
        return new RuntimePinnedPrincipalVerifier(
            pem, KID, ISSUER, AUDIENCE, Clock.systemUTC());
    }

    /**
     * TEST-ONLY Spring Boot server. There is no @ComponentScan or import of
     * AetherisOrchestratorApplication. No customer route can be activated.
     */
    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
    })
    static class IsolatedS0TestApplication {
        @Bean
        FilterRegistrationBean<OwnerlessTaskMissionHttpFilter> ownerlessSafetyFirst() {
            var registration = new FilterRegistrationBean<>(
                new OwnerlessTaskMissionHttpFilter());
            registration.setName("s0-e2-original-task-mission-lockdown");
            registration.addUrlPatterns("/*");
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }

        @Bean
        FilterRegistrationBean<RuntimePinnedHttpBearerGate> testOnlyPinnedBearerSecond() {
            var registration = new FilterRegistrationBean<>(
                new RuntimePinnedHttpBearerGate(testVerifier()));
            registration.setName("s0-e2-test-only-jwt-probe");
            registration.addUrlPatterns("/*");
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
            return registration;
        }

        @Bean
        SyntheticS0OnlyEndpoints syntheticS0OnlyEndpoints() {
            return new SyntheticS0OnlyEndpoints();
        }
    }

    @RestController
    static final class SyntheticS0OnlyEndpoints {
        @GetMapping("/__s0/auth-probe")
        ResponseEntity<Void> authenticatedButNotAuthorizedForCustomerData() {
            PROBE_DISPATCHES.incrementAndGet();
            return ResponseEntity.noContent().build();
        }

        @RequestMapping(
            path = {
                "/api/orchestrator/tasks",
                "/api/orchestrator/tasks/**",
                "/api/orchestrator/missions",
                "/api/orchestrator/missions/**"
            },
            method = {RequestMethod.GET, RequestMethod.POST}
        )
        ResponseEntity<Void> forbiddenDispatchTrap() {
            PROTECTED_DISPATCHES.incrementAndGet();
            return ResponseEntity.ok().build();
        }
    }

    private static String sign(KeyPair key, String kid, String issuer,
                               List<String> audience, List<String> scopes,
                               long issuedSecondsAgo, long lifetimeSeconds) {
        Instant issued = Instant.now().minusSeconds(issuedSecondsAgo);
        return Jwts.builder().header().keyId(kid).and()
            .subject("synthetic-identity-42@example.invalid")
            .issuer(issuer).claim("aud", audience)
            .claim("uid", 42L).claim("role", "DEVELOPER")
            .claim("scopes", scopes)
            .issuedAt(Date.from(issued))
            .expiration(Date.from(issued.plusSeconds(lifetimeSeconds)))
            .signWith(key.getPrivate(), Jwts.SIG.RS256)
            .compact();
    }

    private static String signedRead() {
        return sign(TRUSTED,KID,ISSUER,List.of(AUDIENCE),
            List.of("orchestrator:read"),10,180);
    }

    private HttpResponse<String> request(String method, String path, String bearer,
                                         Map<String,String> headers) throws Exception {
        assertTrue(port > 0, "Real Spring Boot server must bind a TCP port");
        HttpRequest.Builder builder = HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + port + path));
        builder.method(method, "GET".equals(method)
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString("{}"));
        if (bearer != null) builder.header("Authorization",bearer);
        for (var entry : headers.entrySet()) {
            builder.header(entry.getKey(),entry.getValue());
        }
        return HttpClient.newHttpClient().send(
            builder.build(),HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        return request("GET",path,bearer,Map.of());
    }

    @Test
    void bootReallyStartedAndTestFilterWiringIsExplicitlyOrdered() {
        assertTrue(port > 0);
        var safety = context.getBean(
            "ownerlessSafetyFirst",FilterRegistrationBean.class);
        var gate = context.getBean(
            "testOnlyPinnedBearerSecond",FilterRegistrationBean.class);
        assertEquals(Ordered.HIGHEST_PRECEDENCE,safety.getOrder());
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 1,gate.getOrder());
        assertEquals(OwnerlessTaskMissionHttpFilter.class,
            safety.getFilter().getClass());
        assertEquals(RuntimePinnedHttpBearerGate.class,
            gate.getFilter().getClass());
        // Filter registration occurs only via the test configuration.
        assertFalse(context.containsBean("aetherisOrchestratorApplication"));
    }

    @Test
    void springBootSocketRejectsUnsignedWrongKeyIssuerKidAudienceAndExpiry() throws Exception {
        var missing = get("/__s0/auth-probe",null);
        assertEquals(401,missing.statusCode());
        assertEquals("Bearer realm=\"aetheris-s0-probe\"",
            missing.headers().firstValue("WWW-Authenticate").orElse(null));
        for (String bad : List.of(
            "Bearer bogus.x.y",
            "bearer " + signedRead(),
            "Bearer  " + signedRead(),
            "Bearer " + sign(ATTACKER,KID,ISSUER,List.of(AUDIENCE),
                List.of("orchestrator:read"),10,180),
            "Bearer " + sign(TRUSTED,"untrusted-kid",ISSUER,List.of(AUDIENCE),
                List.of("orchestrator:read"),10,180),
            "Bearer " + sign(TRUSTED,KID,"https://untrusted.invalid",List.of(AUDIENCE),
                List.of("orchestrator:read"),10,180),
            "Bearer " + sign(TRUSTED,KID,ISSUER,List.of("aetheris-user-api"),
                List.of("orchestrator:read"),10,180),
            "Bearer " + sign(TRUSTED,KID,ISSUER,List.of(AUDIENCE,"extra-audience"),
                List.of("orchestrator:read"),10,180),
            "Bearer " + sign(TRUSTED,KID,ISSUER,List.of(AUDIENCE),
                List.of("orchestrator:read"),600,120)
        )) {
            assertEquals(401,get("/__s0/auth-probe",bad).statusCode());
        }
    }

    @Test
    void scopeAndSpoofedHeadersCannotImpersonateAuthenticatedPrincipal() throws Exception {
        assertEquals(204,request("GET","/__s0/auth-probe","Bearer "+signedRead(),
            Map.of("X-Forwarded-Client-Cert","spiffe://spoofed",
                   "X-Aetheris-Verified-Uid","999")).statusCode());
        assertEquals(403,get("/__s0/auth-probe","Bearer "+sign(
            TRUSTED,KID,ISSUER,List.of(AUDIENCE),
            List.of("services:read"),10,180)).statusCode());
        assertEquals(401,request("GET","/__s0/auth-probe",null,
            Map.of("X-Aetheris-Owner","42",
                   "X-Original-Authorization","Bearer "+signedRead())).statusCode());
        assertTrue(PROBE_DISPATCHES.get() > 0);
    }

    @Test
    void validSignedBearerIsStillDeniedBeforeEveryTaskMissionTrapHandler()
            throws Exception {
        int initial = PROTECTED_DISPATCHES.get();
        for (String url : List.of(
            "/api/orchestrator/tasks",
            "/api/orchestrator/tasks/22222222-2222-4222-8222-222222222222",
            "/api/orchestrator/tasks/22222222-2222-4222-8222-222222222222/events/history",
            "/api/orchestrator/missions",
            "/api/orchestrator/missions/33333333-3333-4333-8333-333333333333/messages")) {
            var response = get(url,"Bearer "+signedRead());
            assertEquals(404,response.statusCode(),url);
            assertEquals("no-store",
                response.headers().firstValue("Cache-Control").orElse(null));
            assertEquals("nosniff",
                response.headers().firstValue("X-Content-Type-Options").orElse(null));
        }
        assertEquals(404,request("POST","/api/orchestrator/tasks",
            "Bearer "+signedRead(),Map.of()).statusCode());
        assertEquals(initial,PROTECTED_DISPATCHES.get(),
            "Valid Identity token must not reach any task/mission controller");
    }

    @Test
    void allOtherPathsAndVerbsRemainClosedEvenWithSignedToken() throws Exception {
        assertEquals(404,request("POST","/__s0/auth-probe",
            "Bearer "+signedRead(),Map.of()).statusCode());
        assertEquals(404,get("/__s0/auth-probe-other",
            "Bearer "+signedRead()).statusCode());
        assertEquals(404,get("/api/orchestrator/agents",
            "Bearer "+signedRead()).statusCode());
        assertEquals(404,get("/actuator/health",
            "Bearer "+signedRead()).statusCode());
    }
}
