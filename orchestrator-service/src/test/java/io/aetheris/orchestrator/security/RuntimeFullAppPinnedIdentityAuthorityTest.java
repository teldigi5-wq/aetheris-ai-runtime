package io.aetheris.orchestrator.security;

import io.aetheris.orchestrator.AetherisOrchestratorApplication;
import io.aetheris.orchestrator.api.OwnerlessTaskMissionHttpFilter;
import io.aetheris.orchestrator.task.TaskRepository;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Genuine Aetheris Spring Boot context and H2 JPA. The new identity
 * verification authority is created by normal component scan from a pinned
 * synthetic public key; unlike E2/E3 there is NO @TestConfiguration injection.
 *
 * Critically, even a valid Identity JWT MUST NOT unlock any /api or Task route.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = AetherisOrchestratorApplication.class,
    properties = {
        "server.address=127.0.0.1",
        "server.port=0",
        "spring.datasource.url=jdbc:h2:mem:s0_e6a_identity_authority;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.task.scheduling.enabled=false",
        "aetheris.dispatcher.enabled=false",
        "aetheris.syntra.owner-runtime.enabled=false",
        "aetheris.cloud-provider.enabled=false",
        "aetheris.execution.workspace-write-enabled=false",
        "aetheris.execution.allowed-commands=",
        "aetheris.execution.github.allowed-repositories=",
        "aetheris.host.simulation-only=true"
    }
)
class RuntimeFullAppPinnedIdentityAuthorityTest {
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String AUDIENCE = "aetheris-orchestrator-api";
    private static final String KID = "s0-e6a-pinned-key";
    private static final KeyPair TRUSTED = generateRsa();
    private static final KeyPair FORGED = generateRsa();
    private static final String PEM = "-----BEGIN PUBLIC KEY-----\n"
        + Base64.getMimeEncoder(64,new byte[]{'\n'})
            .encodeToString(TRUSTED.getPublic().getEncoded())
        + "\n-----END PUBLIC KEY-----";
    private static final String PEM_B64 = Base64.getEncoder()
        .encodeToString(PEM.getBytes(StandardCharsets.US_ASCII));
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER).build();

    @DynamicPropertySource
    static void pinnedFixture(DynamicPropertyRegistry properties) {
        properties.add("aetheris.runtime.identity.public-key-pem-base64", () -> PEM_B64);
        properties.add("aetheris.runtime.identity.key-id", () -> KID);
        properties.add("aetheris.runtime.identity.issuer", () -> ISSUER);
        properties.add("aetheris.runtime.identity.audience", () -> AUDIENCE);
    }

    @Autowired
    RuntimePinnedIdentityAuthority authority;

    @Autowired
    org.springframework.context.ApplicationContext app;

    @Autowired
    TaskRepository tasks;

    @LocalServerPort
    int port;

    private static KeyPair generateRsa() {
        try {
            var g=KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Test-only RSA fixture unavailable", e);
        }
    }

    private static String jwt(KeyPair signer, String kid, String issuer,
                              List<String> audience, String role,
                              List<String> scopes, long age, long lifespan) {
        Instant issued=Instant.now().minusSeconds(age);
        return Jwts.builder().header().keyId(kid).and()
            .issuer(issuer).subject("synthetic-authority-test@example.invalid")
            .claim("aud",audience)
            .claim("uid",42L).claim("role",role).claim("scopes",scopes)
            .issuedAt(Date.from(issued))
            .expiration(Date.from(issued.plusSeconds(lifespan)))
            .signWith(signer.getPrivate(),Jwts.SIG.RS256).compact();
    }

    private static String good() {
        return "Bearer " + jwt(TRUSTED,KID,ISSUER,List.of(AUDIENCE),
            "DEVELOPER",List.of("orchestrator:read"),10,180);
    }

    private HttpResponse<String> get(String path,String bearer) throws Exception {
        assertTrue(port > 0,"actual Spring Boot socket required");
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
            .GET();
        if(bearer!=null) request.header("Authorization",bearer);
        request.header("X-Aetheris-Owner","spoofed-owner");
        request.header("X-Forwarded-Client-Cert","fake-trust-header");
        return HTTP.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void realFullRuntimeCreatesOnlyPinnedTrustAndBothProductionQuarantineFilters() {
        assertTrue(authority.isConfigured());
        assertSame(authority, app.getBean(RuntimePinnedIdentityAuthority.class));
        assertNotNull(app.getBean(OwnerlessTaskMissionHttpFilter.class));
        assertNotNull(app.getBean(RuntimeUncertifiedApiHttpFilter.class));
        assertNotNull(app.getBean(TaskRepository.class));
        assertFalse(app.containsBean("onlyTestSyntheticGate"));
        assertFalse(app.containsBean("testOnlyPinnedBearerSecond"));
    }

    @Test
    void operatorPinnedSignatureIssuerKidAndAudienceCreateSealedPrincipal() {
        var identity=authority.requireVerifiedPrincipal(good());
        assertEquals(42L, identity.identityUid());
        assertEquals("DEVELOPER",identity.role());
        assertEquals("synthetic-authority-test@example.invalid",identity.subject());
        assertTrue(identity.hasScope("orchestrator:read"));
        assertFalse(identity.hasScope("orchestrator:write"));
    }

    @Test
    void forgedWrongIssuerAudienceKeyIdExpiredAndRoleEscalationFailClosed() {
        for(String invalid:List.of(
            "Bearer broken.jwt.header",
            "bearer "+good().substring(7),
            "Bearer "+jwt(FORGED,KID,ISSUER,List.of(AUDIENCE),"DEVELOPER",
                List.of("orchestrator:read"),10,180),
            "Bearer "+jwt(TRUSTED,"wrong-kid",ISSUER,List.of(AUDIENCE),"DEVELOPER",
                List.of("orchestrator:read"),10,180),
            "Bearer "+jwt(TRUSTED,KID,"https://untrusted.invalid",
                List.of(AUDIENCE),"DEVELOPER",List.of("orchestrator:read"),10,180),
            "Bearer "+jwt(TRUSTED,KID,ISSUER,List.of("aetheris-user-api"),
                "DEVELOPER",List.of("orchestrator:read"),10,180),
            "Bearer "+jwt(TRUSTED,KID,ISSUER,List.of(AUDIENCE,"extra-audience"),
                "DEVELOPER",List.of("orchestrator:read"),10,180),
            "Bearer "+jwt(TRUSTED,KID,ISSUER,List.of(AUDIENCE),"DEVELOPER",
                List.of("orchestrator:read"),600,120),
            "Bearer "+jwt(TRUSTED,KID,ISSUER,List.of(AUDIENCE),"API_CONSUMER",
                List.of("orchestrator:write"),10,180)
        )) {
            assertThrows(IllegalArgumentException.class,
                () -> authority.requireVerifiedPrincipal(invalid));
        }
        assertThrows(IllegalArgumentException.class,
            () -> authority.requireVerifiedPrincipal(null));
    }

    @Test
    void missingOrIncompleteKeyPropertiesNeverYieldUnauthenticatedAuthority() {
        var absent=new RuntimePinnedIdentityAuthority(new MockEnvironment());
        assertFalse(absent.isConfigured());
        assertThrows(IllegalStateException.class,
            () -> absent.requireVerifiedPrincipal(good()));
        var partial=new MockEnvironment()
            .withProperty("aetheris.runtime.identity.key-id",KID)
            .withProperty("aetheris.runtime.identity.issuer",ISSUER);
        assertThrows(IllegalStateException.class,
            () -> new RuntimePinnedIdentityAuthority(partial));
        var invalidBase64=new MockEnvironment()
            .withProperty("aetheris.runtime.identity.public-key-pem-base64","garbage%%%")
            .withProperty("aetheris.runtime.identity.key-id",KID)
            .withProperty("aetheris.runtime.identity.issuer",ISSUER)
            .withProperty("aetheris.runtime.identity.audience",AUDIENCE);
        assertThrows(IllegalStateException.class,
            () -> new RuntimePinnedIdentityAuthority(invalidBase64));
        var invalidKid=new MockEnvironment()
            .withProperty("aetheris.runtime.identity.public-key-pem-base64",PEM_B64)
            .withProperty("aetheris.runtime.identity.key-id","invalid key id!")
            .withProperty("aetheris.runtime.identity.issuer",ISSUER)
            .withProperty("aetheris.runtime.identity.audience",AUDIENCE);
        assertThrows(IllegalStateException.class,
            () -> new RuntimePinnedIdentityAuthority(invalidKid));
    }

    @Test
    void independentlySignedValidBearerCannotOpenActualApiOrTasksOverTcp() throws Exception {
        long before=tasks.count();
        for(String path:List.of(
            "/api/orchestrator/agents",
            "/api/orchestrator/overview",
            "/api/orchestrator/connectors",
            "/api/orchestrator/models/execute",
            "/api/orchestrator/tasks",
            "/api/orchestrator/tasks/22222222-2222-4222-8222-222222222222/events",
            "/api/orchestrator/missions",
            "/actuator/health")) {
            for(String bearer:new String[]{good(),"Bearer forged",null}) {
                var response=get(path,bearer);
                assertEquals(404,response.statusCode(),path);
                assertEquals("no-store",
                    response.headers().firstValue("Cache-Control").orElse(null),path);
                assertEquals("nosniff",
                    response.headers().firstValue("X-Content-Type-Options").orElse(null),path);
            }
        }
        assertEquals(before,tasks.count());
    }
}
