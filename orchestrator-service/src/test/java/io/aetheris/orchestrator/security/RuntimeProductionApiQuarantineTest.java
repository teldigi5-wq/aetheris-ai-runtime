package io.aetheris.orchestrator.security;

import io.aetheris.orchestrator.AetherisOrchestratorApplication;
import io.aetheris.orchestrator.api.AgentCatalogController;
import io.aetheris.orchestrator.api.MissionController;
import io.aetheris.orchestrator.api.OwnerlessTaskMissionHttpFilter;
import io.aetheris.orchestrator.api.TaskController;
import io.aetheris.orchestrator.task.TaskRepository;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S0-E4 starts the genuine full AetherisOrchestratorApplication with its
 * *actual production-registered* HTTP quarantine component, not a test-only
 * registration. Real localhost TCP, real MVC controllers, H2 JPA in memory.
 *
 * Deny-only is the intended behavior: no bearer can unlock an uncertified
 * application API. This DOES NOT replace a real resource server or authorize
 * customer data; restoring any API needs a separately approved release.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = AetherisOrchestratorApplication.class,
    properties = {
        "server.address=127.0.0.1",
        "server.port=0",
        "spring.datasource.url=jdbc:h2:mem:s0_e4_real_api_quarantine;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
        "aetheris.host.simulation-only=true"
    })
class RuntimeProductionApiQuarantineTest {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER).build();

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext app;

    @Autowired
    ServletContext servletContext;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlers;

    @Autowired
    TaskRepository tasks;

    private HttpResponse<String> call(String method, String path, String bearer)
            throws Exception {
        assertTrue(port > 0);
        var builder = HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + port + path));
        builder.method(method, "GET".equals(method) || "HEAD".equals(method)
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString("{}"));
        if (bearer != null) builder.header("Authorization", bearer);
        builder.header("X-Forwarded-Client-Cert", "spiffe://spoofed");
        builder.header("X-Aetheris-Owner", "arbitrary-admin");
        return HTTP.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }

    private void denied(String method,String path,String bearer) throws Exception {
        HttpResponse<String> result = call(method,path,bearer);
        assertEquals(404,result.statusCode(),method+" "+path);
        assertEquals("no-store",result.headers()
            .firstValue("Cache-Control").orElse(null),path);
        assertEquals("nosniff",result.headers()
            .firstValue("X-Content-Type-Options").orElse(null),path);
    }

    @Test
    void fullRealSpringRuntimeRegistersActualProductionQuarantineAndTaskFilters() {
        assertNotNull(app.getBean(AetherisOrchestratorApplication.class));
        assertNotNull(app.getBean(AgentCatalogController.class));
        assertNotNull(app.getBean(TaskController.class));
        assertNotNull(app.getBean(MissionController.class));
        var taskGate=app.getBean(OwnerlessTaskMissionHttpFilter.class);
        var runtimeGate=app.getBean(RuntimeUncertifiedApiHttpFilter.class);
        assertNotNull(taskGate);
        assertNotNull(runtimeGate);
        assertEquals(Ordered.HIGHEST_PRECEDENCE,
            taskGate.getClass().getAnnotation(
                org.springframework.core.annotation.Order.class).value());
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 1,
            runtimeGate.getClass().getAnnotation(
                org.springframework.core.annotation.Order.class).value());
        Set<String> registered=servletContext.getFilterRegistrations().values()
            .stream().map(j -> j.getClassName()).collect(Collectors.toSet());
        assertTrue(registered.contains(RuntimeUncertifiedApiHttpFilter.class.getName()),
            "Actual servlet filter must auto-register in unmodified full application");
        assertTrue(registered.contains(OwnerlessTaskMissionHttpFilter.class.getName()),
            "Original task/mission containment must remain registered");
        assertFalse(app.containsBean("onlyTestSyntheticGate"),
            "Never accept a test-only auth gate as production HTTP security");
        assertTrue(handlers.getHandlerMethods().size() >= 30);
    }

    @Test
    void routeInventoryDoesNotLeaveNewBusinessControllersOutsideQuarantine() {
        var uncovered = new ArrayList<String>();
        int realControllers = 0;
        for (var entry:handlers.getHandlerMethods().entrySet()) {
            String bean = entry.getValue().getBeanType().getName();
            // Spring Boot error dispatch is not a user-facing business API.
            if (bean.startsWith("org.springframework.boot.autoconfigure.web.servlet.error.")) {
                continue;
            }
            realControllers++;
            var condition = entry.getKey().getPathPatternsCondition();
            Set<String> paths = condition != null
                ? condition.getPatternValues()
                : entry.getKey().getPatternsCondition().getPatterns();
            for (String path:paths) {
                if (!(path.startsWith("/api/") || path.startsWith("/actuator/"))) {
                    uncovered.add(bean+" "+path);
                }
            }
        }
        assertTrue(realControllers >= 30,
            "Actual full controller inventory must be substantial, not synthetic");
        assertTrue(uncovered.isEmpty(),
            "Full-context route inventory found endpoints beyond API quarantine: "+uncovered);
    }

    @Test
    void allRealTaskAndMissionRoutesRemainEarly404EvenWithForgedBearer()
            throws Exception {
        long initial=tasks.count();
        for (String path:List.of(
            "/api/orchestrator/tasks",
            "/api/orchestrator/tasks/22222222-2222-4222-8222-222222222222",
            "/api/orchestrator/tasks/22222222-2222-4222-8222-222222222222/events",
            "/api/orchestrator/tasks/22222222-2222-4222-8222-222222222222/events/history",
            "/api/orchestrator/missions",
            "/api/orchestrator/missions/33333333-3333-4333-8333-333333333333/messages"
        )) {
            denied("GET",path,"Bearer forged.superadmin.jwt");
            denied("GET",path,null);
        }
        denied("POST","/api/orchestrator/tasks","Bearer forged");
        denied("POST","/api/orchestrator/missions",null);
        assertEquals(initial,tasks.count(),"Deny filter must prevent task writes");
    }

    @Test
    void otherActualControllerSurfacesAreQuarantinedWithoutBearerSwitch()
            throws Exception {
        long initial=tasks.count();
        for (String path:List.of(
            "/api/orchestrator/agents",
            "/api/orchestrator/overview",
            "/api/orchestrator/divisions/executive/agents",
            "/api/orchestrator/live/events",
            "/api/orchestrator/connectors",
            "/api/orchestrator/models/execute",
            "/api/orchestrator/hosts/protocol/receipts",
            "/api/orchestrator/stage15/services",
            "/actuator/health",
            "/actuator/prometheus"
        )) {
            denied("GET",path,null);
            denied("GET",path,"Bearer fabricated");
        }
        denied("POST","/api/orchestrator/models/execute","Bearer fabricated");
        denied("POST","/api/orchestrator/connectors/connections","Bearer fabricated");
        denied("HEAD","/api/orchestrator/agents","Bearer fabricated");
        assertEquals(initial,tasks.count());
    }

    @Test
    void segmentBoundaryDoesNotCreateFalsePositiveOutsideProtectedPrefixes() {
        assertTrue(RuntimeUncertifiedApiHttpFilter.quarantinedPath("/api"));
        assertTrue(RuntimeUncertifiedApiHttpFilter.quarantinedPath("/api/"));
        assertTrue(RuntimeUncertifiedApiHttpFilter.quarantinedPath("/api/orchestrator/agents"));
        assertTrue(RuntimeUncertifiedApiHttpFilter.quarantinedPath("/api;session=x"));
        assertTrue(RuntimeUncertifiedApiHttpFilter.quarantinedPath("/actuator"));
        assertTrue(RuntimeUncertifiedApiHttpFilter.quarantinedPath("/actuator/health"));
        for (String unrelated:List.of(
            "/", "/apiary", "/actuators", "/__s0/auth-probe",
            "/assets/logo.svg", "/api-other", "/ACTUATOR")) {
            assertFalse(RuntimeUncertifiedApiHttpFilter.quarantinedPath(unrelated),unrelated);
        }
    }
}
