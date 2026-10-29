package io.aetheris.orchestrator.security;

import io.aetheris.orchestrator.AetherisOrchestratorApplication;
import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.api.AgentCatalogController;
import io.aetheris.orchestrator.api.MissionController;
import io.aetheris.orchestrator.api.OwnerlessTaskMissionHttpFilter;
import io.aetheris.orchestrator.api.TaskController;
import io.aetheris.orchestrator.task.TaskRepository;
import io.aetheris.orchestrator.task.TaskService;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FULL actual AetherisOrchestratorApplication component graph and true H2 JPA
 * startup, using Spring Boot's RANDOM_PORT socket. Unlike S0-E2 this test
 * imports real controllers, scheduled bean definitions and repositories.
 *
 * Its NON-CUSTOMER signed auth gate + probe are @TestConfiguration only.
 * No actual Runtime application filter registration is changed, so this
 * cannot certify production direct-port authentication or permit any routes.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = AetherisOrchestratorApplication.class,
    properties = {
        "server.address=127.0.0.1",
        "server.port=0",
        "spring.datasource.url=jdbc:h2:mem:s0_e3_full_application;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.task.scheduling.enabled=false",
        "aetheris.dispatcher.enabled=false",
        "aetheris.execution.workspace-write-enabled=false",
        "aetheris.execution.allowed-commands=",
        "aetheris.execution.github.allowed-repositories=",
        "aetheris.cloud-provider.enabled=false",
        "aetheris.host.simulation-only=true",
        "management.endpoints.enabled-by-default=false"
    })
@Import(RuntimeFullAppHttpContainmentTest.OnlyInThisJunitContext.class)
class RuntimeFullAppHttpContainmentTest {
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String AUDIENCE = "aetheris-orchestrator-api";
    private static final String KID = "s0-e3-fullapp-test-only";
    private static final KeyPair TRUSTED = rsa();
    private static final KeyPair UNRELATED = rsa();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER).build();

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext app;

    @Autowired
    RequestMappingHandlerMapping handlers;

    @Autowired
    DataSource datasource;

    private static KeyPair rsa() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Synthetic test RSA unavailable", ex);
        }
    }

    private static RuntimePinnedPrincipalVerifier syntheticVerifier() {
        String pem = "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(TRUSTED.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
        return new RuntimePinnedPrincipalVerifier(
            pem, KID, ISSUER, AUDIENCE, Clock.systemUTC());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OnlyInThisJunitContext {
        /**
         * Default prod @Component OwnerlessTaskMissionHttpFilter is already
         * registered at Ordered.HIGHEST_PRECEDENCE. The TEST-only filter
         * follows it, and denies ALL application paths other than one
         * ephemeral, non-customer probe route declared below.
         */
        @Bean
        FilterRegistrationBean<RuntimePinnedHttpBearerGate> onlyTestSyntheticGate() {
            var filter = new FilterRegistrationBean<>(
                new RuntimePinnedHttpBearerGate(syntheticVerifier()));
            filter.setName("s0-e3-test-only-http-probe");
            filter.addUrlPatterns("/*");
            filter.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
            return filter;
        }

        @Bean
        OnlySyntheticProbe syntheticProbe() {
            return new OnlySyntheticProbe();
        }
    }

    @RestController
    static class OnlySyntheticProbe {
        @GetMapping("/__s0/auth-probe")
        ResponseEntity<Void> noCustomerData(HttpServletRequest request) {
            // The bearer gate already verified the token. There is no
            // object read, task authorization, runtime action, or identity
            // returned to client.
            return ResponseEntity.noContent().build();
        }
    }

    private static String token(KeyPair key, String kid, String issuer,
                                List<String> audience, List<String> scopes,
                                long ageSeconds, long durationSeconds) {
        Instant issued = Instant.now().minusSeconds(ageSeconds);
        return Jwts.builder().header().keyId(kid).and()
            .subject("s0-e3-synthetic@example.invalid")
            .issuer(issuer).claim("aud",audience)
            .claim("uid",42L).claim("role","DEVELOPER")
            .claim("scopes",scopes)
            .issuedAt(Date.from(issued))
            .expiration(Date.from(issued.plusSeconds(durationSeconds)))
            .signWith(key.getPrivate(),Jwts.SIG.RS256).compact();
    }

    private static String good() {
        return "Bearer " + token(TRUSTED,KID,ISSUER,List.of(AUDIENCE),
            List.of("orchestrator:read"),10,180);
    }

    private HttpResponse<String> request(String method, String path, String bearer,
                                         Map<String,String> headers) throws Exception {
        assertTrue(port > 0);
        var builder = HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + port + path));
        builder.method(method, "GET".equals(method)
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString("{}"));
        if (bearer != null) builder.header("Authorization",bearer);
        for (var entry : headers.entrySet())
            builder.header(entry.getKey(),entry.getValue());
        return HTTP.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        return request("GET",path,bearer,Map.of());
    }

    @Test
    void actualFullApplicationBootsAndRealControllerJpaGraphIsLoaded() throws Exception {
        assertNotNull(app.getBean(AetherisOrchestratorApplication.class));
        assertNotNull(app.getBean(TaskController.class));
        assertNotNull(app.getBean(MissionController.class));
        assertNotNull(app.getBean(AgentCatalogController.class));
        assertNotNull(app.getBean(TaskService.class));
        assertNotNull(app.getBean(AgentCatalogService.class));
        assertNotNull(app.getBean(TaskRepository.class));
        assertNotNull(app.getBean(OwnerlessTaskMissionHttpFilter.class));
        Set<Class<?>> controllers=handlers.getHandlerMethods().values().stream()
            .map(h -> h.getBeanType()).collect(Collectors.toSet());
        assertTrue(controllers.contains(TaskController.class));
        assertTrue(controllers.contains(MissionController.class));
        assertTrue(controllers.contains(AgentCatalogController.class));
        assertTrue(controllers.size() >= 30,
            "Full real application must load at least 30 controller types");
        try (var connection = datasource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:"),
                "No external PostgreSQL or production database permitted");
        }
        var onlyInTest = app.getBean(
            "onlyTestSyntheticGate",FilterRegistrationBean.class);
        assertEquals(Ordered.HIGHEST_PRECEDENCE+1,onlyInTest.getOrder());
        assertEquals(RuntimePinnedHttpBearerGate.class,onlyInTest.getFilter().getClass());
        assertEquals(Ordered.HIGHEST_PRECEDENCE,
            OwnerlessTaskMissionHttpFilter.class
                .getAnnotation(org.springframework.core.annotation.Order.class).value());
    }

    @Test
    void realFullAppRejectsForgedAndUnscopedBearerOnTestOnlyProbe() throws Exception {
        var missing = get("/__s0/auth-probe",null);
        assertEquals(401,missing.statusCode());
        assertEquals("Bearer realm=\"aetheris-s0-probe\"",
            missing.headers().firstValue("WWW-Authenticate").orElse(null));
        for (String rejected : List.of(
            "Bearer malformed.jwt.parts",
            "Bearer " + token(UNRELATED,KID,ISSUER,List.of(AUDIENCE),
                List.of("orchestrator:read"),10,180),
            "Bearer " + token(TRUSTED,"wrong-kid",ISSUER,List.of(AUDIENCE),
                List.of("orchestrator:read"),10,180),
            "Bearer " + token(TRUSTED,KID,"https://untrusted.invalid",List.of(AUDIENCE),
                List.of("orchestrator:read"),10,180),
            "Bearer " + token(TRUSTED,KID,ISSUER,List.of("aetheris-user-api"),
                List.of("orchestrator:read"),10,180),
            "Bearer " + token(TRUSTED,KID,ISSUER,List.of(AUDIENCE),
                List.of("orchestrator:read"),600,120)
        )) {
            assertEquals(401,get("/__s0/auth-probe",rejected).statusCode());
        }
        assertEquals(403,get("/__s0/auth-probe","Bearer " + token(
            TRUSTED,KID,ISSUER,List.of(AUDIENCE),List.of("services:read"),10,180))
            .statusCode());
        var permitted=request("GET","/__s0/auth-probe",good(),Map.of(
            "X-Aetheris-Verified-Uid","999",
            "X-Forwarded-Client-Cert","spiffe://fake"));
        assertEquals(204,permitted.statusCode());
        assertTrue(permitted.body().isEmpty());
    }

    @Test
    void realTaskMissionHttpControllersRemainDeniedWithValidJwt() throws Exception {
        for (String path : List.of(
            "/api/orchestrator/tasks",
            "/api/orchestrator/tasks/22222222-2222-4222-8222-222222222222",
            "/api/orchestrator/tasks/22222222-2222-4222-8222-222222222222/events/history",
            "/api/orchestrator/tasks/22222222-2222-4222-8222-222222222222/events",
            "/api/orchestrator/missions",
            "/api/orchestrator/missions/33333333-3333-4333-8333-333333333333/messages"
        )) {
            var response = get(path,good());
            assertEquals(404,response.statusCode(),path);
            assertEquals("no-store",response.headers().firstValue("Cache-Control").orElse(null));
            assertEquals("nosniff",response.headers().firstValue("X-Content-Type-Options").orElse(null));
        }
        assertEquals(404,request("POST","/api/orchestrator/tasks",good(),
            Map.of("X-Aetheris-Owner","42")).statusCode());
        assertEquals(404,request("POST","/api/orchestrator/missions",good(),
            Map.of("X-Verified-Principal","admin")).statusCode());
    }

    @Test
    void testOnlyPortGateDeniesGenuineUnreviewedControllerEndpoints() throws Exception {
        // Crucial: these endpoints exist in the ACTUAL application mapping.
        // The full real application is started, but a test-only gate prevents
        // real data reads or actions; this is NOT proof of prod registration.
        for (String path : List.of(
            "/api/orchestrator/agents",
            "/api/orchestrator/overview",
            "/api/orchestrator/models/execute",
            "/api/orchestrator/divisions/executive/agents",
            "/actuator/health"
        )) {
            assertEquals(404,get(path,null).statusCode(),path);
            assertEquals(404,get(path,good()).statusCode(),path);
        }
        assertEquals(404,request("POST","/api/orchestrator/models/execute",
            good(),Map.of()).statusCode());
        assertEquals(404,request("POST","/__s0/auth-probe",good(),Map.of()).statusCode());
        assertEquals(404,get("/__s0/auth-probe-other",good()).statusCode());
    }

    @Test
    void arbitraryCallerHeadersCannotBecomeSignedPrincipal() throws Exception {
        for (Map<String,String> forged : List.of(
            Map.of("X-Aetheris-Owner","42"),
            Map.of("X-Forwarded-Client-Cert","spiffe://production"),
            Map.of("X-Aetheris-Verified-Uid","42"),
            Map.of("X-Original-Authorization",good())
        )) {
            assertEquals(401,request("GET","/__s0/auth-probe",null,forged).statusCode());
        }
    }
}
