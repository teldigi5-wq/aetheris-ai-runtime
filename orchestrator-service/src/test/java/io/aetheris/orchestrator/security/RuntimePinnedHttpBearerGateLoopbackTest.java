package io.aetheris.orchestrator.security;

import io.aetheris.orchestrator.api.OwnerlessTaskMissionHttpFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * Real loopback TCP + embedded Tomcat servlet filters (not MockMvc).
 * Fixed synthetic route exists ONLY in this test. Original production
 * OwnerlessTaskMissionHttpFilter runs FIRST, including valid signed bearers.
 */
class RuntimePinnedHttpBearerGateLoopbackTest {
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String AUDIENCE = "aetheris-orchestrator-api";
    private static final String KID = "s0-loopback-rs256";
    private static final AtomicInteger PROBE_DISPATCHES = new AtomicInteger();
    private static final AtomicInteger PROTECTED_DISPATCHES = new AtomicInteger();

    private static KeyPair signingKey;
    private static KeyPair unrelatedKey;
    private static Tomcat tomcat;
    private static HttpClient http;
    private static String origin;

    @BeforeAll
    static void startActualLoopbackTomcat() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        signingKey = generator.generateKeyPair();
        unrelatedKey = generator.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(signingKey.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
        var verifier = new RuntimePinnedPrincipalVerifier(
            pem, KID, ISSUER, AUDIENCE, Clock.systemUTC());

        tomcat = new Tomcat();
        tomcat.setBaseDir(Files.createTempDirectory("s0-loopback-tomcat-").toString());
        tomcat.setHostname("127.0.0.1");
        tomcat.setPort(0);
        tomcat.getConnector();
        Context context = tomcat.addContext("", Files.createTempDirectory("s0-probe-webroot-").toString());

        HttpServlet harmlessProbe = new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
                PROBE_DISPATCHES.incrementAndGet();
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
        };
        Tomcat.addServlet(context, "s0-probe", harmlessProbe);
        context.addServletMappingDecoded("/__s0/auth-probe", "s0-probe");

        HttpServlet forbiddenTrap = new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) {
                PROTECTED_DISPATCHES.incrementAndGet();
                resp.setStatus(HttpServletResponse.SC_OK);
            }
        };
        Tomcat.addServlet(context, "forbidden-trap", forbiddenTrap);
        context.addServletMappingDecoded("/api/orchestrator/tasks/*", "forbidden-trap");
        context.addServletMappingDecoded("/api/orchestrator/missions/*", "forbidden-trap");

        // Tests real servlet dispatch order and "deny BEFORE controller"
        // using exactly the existing production ownerless safety filter.
        register(context, "ownerless-deny", new OwnerlessTaskMissionHttpFilter());
        register(context, "pinned-probe-only", new RuntimePinnedHttpBearerGate(verifier));
        tomcat.start();
        int port = tomcat.getConnector().getLocalPort();
        assertTrue(port > 0, "Tomcat must bind an ephemeral loopback TCP port");
        origin = "http://127.0.0.1:" + port;
        http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    private static void register(Context context, String name, jakarta.servlet.Filter filter) {
        FilterDef def = new FilterDef();
        def.setFilterName(name);
        def.setFilter(filter);
        context.addFilterDef(def);
        FilterMap mapping = new FilterMap();
        mapping.setFilterName(name);
        mapping.addURLPattern("/*");
        mapping.setDispatcher(DispatcherType.REQUEST.name());
        context.addFilterMap(mapping);
    }

    @AfterAll
    static void stopTomcat() throws Exception {
        if (tomcat != null) {
            try { tomcat.stop(); } finally { tomcat.destroy(); }
        }
    }

    private static String token(KeyPair signer, String kid, String issuer,
                                List<String> audience, List<String> scopes,
                                long issuedSecondsAgo, long lifetimeSeconds) {
        Instant issued = Instant.now().minusSeconds(issuedSecondsAgo);
        return Jwts.builder().header().keyId(kid).and()
            .subject("synthetic-test-identity@example.invalid")
            .issuer(issuer).claim("aud", audience)
            .claim("uid", 42L).claim("role", "DEVELOPER")
            .claim("scopes", scopes)
            .issuedAt(Date.from(issued))
            .expiration(Date.from(issued.plusSeconds(lifetimeSeconds)))
            .signWith(signer.getPrivate(), Jwts.SIG.RS256)
            .compact();
    }

    private static String valid() {
        return token(signingKey, KID, ISSUER, List.of(AUDIENCE),
            List.of("orchestrator:read"), 10, 300);
    }

    private static HttpResponse<String> send(String method, String path,
                                            String bearer, Map<String,String> extra)
            throws Exception {
        var b = HttpRequest.newBuilder(URI.create(origin + path));
        b.method(method, method.equals("GET")
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString("{}"));
        if (bearer != null) b.header("Authorization", bearer);
        for (var entry : extra.entrySet()) b.header(entry.getKey(),entry.getValue());
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> get(String path, String bearer) throws Exception {
        return send("GET",path,bearer,Map.of());
    }

    @Test
    void realSocketRequiresVerifierPinnedRs256IssuerAudienceAndKid() throws Exception {
        var missing = get("/__s0/auth-probe",null);
        assertEquals(401,missing.statusCode());
        assertEquals("Bearer realm=\"aetheris-s0-probe\"",
            missing.headers().firstValue("WWW-Authenticate").orElse(null));

        for (String invalid : new String[]{
            "Bearer non.jwt.value", "bearer " + valid(),
            "Bearer  " + valid(),
            "Bearer " + token(unrelatedKey,KID,ISSUER,List.of(AUDIENCE),
                List.of("orchestrator:read"),10,300),
            "Bearer " + token(signingKey,"other-kid",ISSUER,List.of(AUDIENCE),
                List.of("orchestrator:read"),10,300),
            "Bearer " + token(signingKey,KID,"https://evil.invalid",List.of(AUDIENCE),
                List.of("orchestrator:read"),10,300),
            "Bearer " + token(signingKey,KID,ISSUER,List.of("aetheris-user-api"),
                List.of("orchestrator:read"),10,300),
            "Bearer " + token(signingKey,KID,ISSUER,List.of(AUDIENCE,"other-audience"),
                List.of("orchestrator:read"),10,300),
            "Bearer " + token(signingKey,KID,ISSUER,List.of(AUDIENCE),
                List.of("orchestrator:read"),600,120)
        }) {
            HttpResponse<String> result = get("/__s0/auth-probe",invalid);
            assertEquals(401,result.statusCode(), "Invalid bearer must be rejected");
        }
        assertEquals(0,PROTECTED_DISPATCHES.get());
    }

    @Test
    void realSocketEnforcesScopeWithoutTrustingSpoofedOwnerHeaders() throws Exception {
        String signed = "Bearer " + valid();
        var allowed = send("GET","/__s0/auth-probe",signed,Map.of(
            "X-Aetheris-Owner","attacker",
            "X-Forwarded-Client-Cert","fake-spiffe",
            "X-Aetheris-Verified-Uid","999"));
        assertEquals(204,allowed.statusCode());
        assertEquals("no-store",allowed.headers().firstValue("Cache-Control").orElse(null));
        assertTrue(allowed.body().isEmpty());

        String wrongScope = "Bearer " + token(signingKey,KID,ISSUER,List.of(AUDIENCE),
            List.of("services:read"),10,300);
        assertEquals(403,get("/__s0/auth-probe",wrongScope).statusCode());
        assertEquals(404,send("POST","/__s0/auth-probe",signed,Map.of()).statusCode());
        assertEquals(404,get("/__s0/auth-probe-extra",signed).statusCode());
        assertEquals(404,get("/__s0/auth-probe;param=foo",signed).statusCode());
        assertTrue(PROBE_DISPATCHES.get() >= 1);
    }

    @Test
    void authenticSignedBearerNeverUnlocksTaskMissionOrHistoryOverTcp() throws Exception {
        int before = PROTECTED_DISPATCHES.get();
        for (String path : List.of(
                "/api/orchestrator/tasks",
                "/api/orchestrator/tasks/22222222-2222-4222-8222-222222222222",
                "/api/orchestrator/tasks/22222222-2222-4222-8222-222222222222/events/history",
                "/api/orchestrator/missions",
                "/api/orchestrator/missions/22222222-2222-4222-8222-222222222222/messages")) {
            var rejected = send("GET",path,"Bearer " + valid(),
                Map.of("X-Aetheris-Owner","42"));
            assertEquals(404,rejected.statusCode(),path);
            assertEquals("no-store",rejected.headers().firstValue("Cache-Control").orElse(null));
            assertEquals("nosniff",rejected.headers().firstValue("X-Content-Type-Options").orElse(null));
        }
        assertEquals(404,send("POST","/api/orchestrator/tasks",
            "Bearer " + valid(),Map.of()).statusCode());
        assertEquals(before,PROTECTED_DISPATCHES.get(),
            "No protected controller/handler may be dispatched, even with a signed bearer");
    }

    @Test
    void noUnsignedIdentityOrLoginHeaderCanReachProbe() throws Exception {
        for (Map<String,String> forged : List.of(
            Map.of("X-Aetheris-User-Id","42"),
            Map.of("X-Forwarded-Client-Cert","spiffe://trusted"),
            Map.of("X-Verified-Principal","admin"),
            Map.of("X-Original-Authorization","Bearer "+ valid()))) {
            var rejected=send("GET","/__s0/auth-probe",null,forged);
            assertEquals(401,rejected.statusCode());
        }
    }
}
