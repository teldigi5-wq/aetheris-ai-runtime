package io.aetheris.orchestrator.security;

import io.aetheris.orchestrator.AetherisOrchestratorApplication;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static io.aetheris.orchestrator.security.RuntimeRouteAdmissionPolicy.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Genuine full Runtime controller inventory with a deny-only production bean.
 * This is an access-policy *review scaffold*, not an endpoint permission grant
 * or a production HTTP identity filter. Prior real TCP lockdown tests still run.
 */
@SpringBootTest(
    classes = AetherisOrchestratorApplication.class,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:s0_e6b_review;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class RuntimeRouteAdmissionPolicyTest {
    @Autowired RuntimeRouteAdmissionPolicy policy;

    @Autowired org.springframework.context.ApplicationContext application;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapping;

    @Test
    void realFullContextRegistersPolicyButDoesNotAddAnyAdmissionFilter() {
        assertSame(policy,application.getBean(RuntimeRouteAdmissionPolicy.class));
        assertNotNull(application.getBean(RuntimeUncertifiedApiHttpFilter.class));
        assertNotNull(application.getBean(
            io.aetheris.orchestrator.api.OwnerlessTaskMissionHttpFilter.class));
        assertFalse(application.containsBean("onlyTestSyntheticGate"));
    }

    @Test
    void everyRealMvcRouteStaysQuarantinedWithAndWithoutPrincipal() {
        int entries = 0;
        for (var mappingEntry : mapping.getHandlerMethods().entrySet()) {
            String bean = mappingEntry.getValue().getBeanType().getName();
            if (bean.startsWith("org.springframework.boot.autoconfigure.web.servlet.error.")) {
                continue;
            }
            Set<String> routes = mappingEntry.getKey().getPathPatternsCondition() != null
                ? mappingEntry.getKey().getPathPatternsCondition().getPatternValues()
                : mappingEntry.getKey().getPatternsCondition().getPatterns();
            for (String route : routes) {
                var result = policy.assess(null,"GET",route);
                assertEquals(Verdict.QUARANTINED,result.verdict(),route);
                assertNotEquals(RouteGroup.UNREVIEWED,result.routeGroup(),route);
                assertTrue(result.prerequisites().contains(MissingEvidence.VERIFIED_USER));
                assertTrue(result.prerequisites().contains(MissingEvidence.LIVE_SESSION));
                assertTrue(result.prerequisites().contains(MissingEvidence.TRUSTED_WORKLOAD));
                entries++;
            }
        }
        assertTrue(entries >= 300,
            "Must classify actual full MVC controller mappings, not a mock fixture");
        System.out.println("AETHERIS_S0_E6B_MAPPED_ROUTE_DECISIONS="+entries);
    }

    @Test
    void validRealSignedPrincipalAndWriteScopesNeverGrantOwnership() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var key=generator.generateKeyPair();
        String pem="-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64,new byte[]{'\n'})
                .encodeToString(key.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
        String issuer="https://identity.aetheris.example.invalid";
        String kid="e6b-test-only-key";
        String audience="aetheris-orchestrator-api";
        Instant now=Instant.now();
        String token=Jwts.builder().header().keyId(kid).and()
            .issuer(issuer).subject("synthetic-e6b@example.invalid")
            .claim("aud",List.of(audience)).claim("uid",42L)
            .claim("role","ADMIN")
            .claim("scopes",List.of("orchestrator:read","orchestrator:write"))
            .issuedAt(Date.from(now.minusSeconds(10)))
            .expiration(Date.from(now.plusSeconds(120)))
            .signWith(key.getPrivate(),Jwts.SIG.RS256).compact();
        var principal=new RuntimePinnedPrincipalVerifier(
            pem,kid,issuer,audience,Clock.systemUTC())
            .verifyBearer("Bearer "+token);
        assertEquals(42,principal.identityUid());
        assertTrue(principal.hasScope("orchestrator:write"));

        for (String path:List.of(
            "/api/orchestrator/tasks",
            "/api/orchestrator/tasks/00000000-0000-4000-8000-000000000001/events",
            "/api/orchestrator/missions",
            "/api/orchestrator/live/events",
            "/api/orchestrator/connectors/connections/123/oauth/credential",
            "/api/orchestrator/models/execute",
            "/actuator/health",
            "/unreviewed/future/endpoint")) {
            for (String method:List.of("GET","POST","DELETE","PATCH","ANY")) {
                var result=policy.assess(principal,method,path);
                assertEquals(Verdict.QUARANTINED,result.verdict());
                assertTrue(result.prerequisites().contains(MissingEvidence.LIVE_SESSION));
                assertTrue(result.prerequisites().contains(MissingEvidence.TRUSTED_WORKLOAD));
            }
        }
        var task=policy.assess(principal,"GET","/api/orchestrator/tasks");
        assertEquals(RouteGroup.TASK_MISSION,task.routeGroup());
        assertTrue(task.prerequisites().contains(MissingEvidence.DURABLE_OBJECT_OWNER));
        assertTrue(task.prerequisites().contains(MissingEvidence.TENANT_MEMBERSHIP));
        assertFalse(task.prerequisites().contains(MissingEvidence.VERIFIED_USER));
    }

    @Test
    void highImpactCategoriesRequireSeparateEvidenceWithoutAnyGrantVerdict() {
        var routes=java.util.Map.of(
            "/api/orchestrator/tasks",RouteGroup.TASK_MISSION,
            "/api/orchestrator/missions",RouteGroup.TASK_MISSION,
            "/api/orchestrator/live/events",RouteGroup.EVENT_REPLAY,
            "/api/orchestrator/connectors/connections",RouteGroup.CONNECTOR_SECRET,
            "/api/orchestrator/models/execute",RouteGroup.EXECUTION_CONTROL,
            "/actuator/health",RouteGroup.OPERATOR_MONITORING,
            "/api/orchestrator/agents",RouteGroup.OTHER_API,
            "/other/unmapped",RouteGroup.UNREVIEWED
        );
        for(var entry:routes.entrySet()) {
            assertEquals(entry.getValue(),RuntimeRouteAdmissionPolicy.groupFor(entry.getKey()));
            var result=policy.assess(null,"POST",entry.getKey());
            assertEquals(Verdict.QUARANTINED,result.verdict());
            assertFalse(result.prerequisites().isEmpty());
            assertThrows(UnsupportedOperationException.class,
                () -> result.prerequisites().clear());
        }
        assertEquals(RouteGroup.UNREVIEWED,
            RuntimeRouteAdmissionPolicy.groupFor("/apiary"));
        assertEquals(RouteGroup.UNREVIEWED,
            RuntimeRouteAdmissionPolicy.groupFor("/actuators"));
    }
}
