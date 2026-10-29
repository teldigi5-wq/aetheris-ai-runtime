package io.aetheris.orchestrator.security;

import io.aetheris.orchestrator.AetherisOrchestratorApplication;
import io.aetheris.orchestrator.api.OwnerlessTaskMissionHttpFilter;
import io.aetheris.orchestrator.task.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S0-E6C: actual production-component filter chain over 127.0.0.1 TCP,
 * including alternate HTTP verbs, conflicting proxy headers, and URI
 * canonicalization attempts. All results must remain fail-closed.
 *
 * No test-only auth filter or routes. Actual full Spring Boot component graph.
 * This is not a real user-authentication resource server or release approval.
 */
@SpringBootTest(
    classes = AetherisOrchestratorApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "server.address=127.0.0.1",
        "server.port=0",
        "spring.datasource.url=jdbc:h2:mem:s0_e6c_http_denial;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class RuntimeRealDirectPortDenialRegressionTest {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private static final String FAKE_BEARER = "Bearer caller-written.invalid.signature";

    @Autowired ApplicationContext app;
    @Autowired TaskRepository tasks;
    @Autowired RuntimePinnedIdentityAuthority authority;
    @Autowired RuntimeRouteAdmissionPolicy routePolicy;

    @LocalServerPort int port;

    private HttpResponse<String> send(String method, String path,
                                      String bearer, Map<String,String> headers)
            throws Exception {
        assertTrue(port > 0);
        var builder = HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(8));
        boolean noBody = method.equals("GET") || method.equals("HEAD")
            || method.equals("OPTIONS") || method.equals("DELETE");
        builder.method(method, noBody
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString("{}"));
        if (bearer != null) builder.header("Authorization", bearer);
        for (var e : headers.entrySet()) builder.header(e.getKey(), e.getValue());
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void remainsClosed(String method, String path, String bearer,
                               Map<String,String> headers) throws Exception {
        var result = send(method, path, bearer, headers);
        assertEquals(404, result.statusCode(),
            method + " " + path + " " + (bearer == null ? "absent" : "supplied"));
        assertEquals("no-store",
            result.headers().firstValue("Cache-Control").orElse(null), path);
        assertEquals("nosniff",
            result.headers().firstValue("X-Content-Type-Options").orElse(null), path);
    }

    @Test
    void genuineProductionFiltersAreBothPresentAndOrderedBeforeControllers() {
        var taskFilter = app.getBean(OwnerlessTaskMissionHttpFilter.class);
        var generalFilter = app.getBean(RuntimeUncertifiedApiHttpFilter.class);
        assertNotNull(taskFilter);
        assertNotNull(generalFilter);
        assertEquals(Ordered.HIGHEST_PRECEDENCE,
            taskFilter.getClass().getAnnotation(
                org.springframework.core.annotation.Order.class).value());
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 1,
            generalFilter.getClass().getAnnotation(
                org.springframework.core.annotation.Order.class).value());
        assertFalse(app.containsBean("onlyTestSyntheticGate"));
        assertFalse(app.containsBean("testOnlyPinnedBearerSecond"));
        assertFalse(authority.isConfigured(),
            "No implicit operator key may activate under an unconfigured runtime");
    }

    @Test
    void alternateMethodsCannotBypassRealTaskMissionAndApiQuarantine() throws Exception {
        long initialTaskCount = tasks.count();
        List<String> paths = List.of(
            "/api", "/api/orchestrator/agents", "/api/orchestrator/overview",
            "/api/orchestrator/models/execute",
            "/api/orchestrator/connectors/connections",
            "/api/orchestrator/live/events",
            "/api/orchestrator/tasks",
            "/api/orchestrator/tasks/22222222-2222-4222-8222-222222222222/events",
            "/api/orchestrator/missions",
            "/actuator", "/actuator/health"
        );
        int requests = 0;
        for (String path : paths) {
            for (String method : List.of(
                    "GET","HEAD","POST","PUT","PATCH","DELETE","OPTIONS")) {
                remainsClosed(method, path, FAKE_BEARER, Map.of(
                    "X-HTTP-Method-Override", "GET",
                    "X-Aetheris-Owner", "admin"));
                requests++;
            }
        }
        assertEquals(initialTaskCount, tasks.count(),
            "No HTTP method override may create a Task");
        System.out.println("AETHERIS_S0_E6C_REAL_HTTP_METHOD_NEGATIVE_COUNT=" + requests);
    }

    @Test
    void forwardedIdentityAndProxyRewriteHeadersNeverAuthorizeDirectPortCalls()
            throws Exception {
        for (var headers : List.of(
            Map.of("X-Forwarded-Client-Cert", "spiffe://fake-owner",
                   "X-Aetheris-Verified-Uid", "1"),
            Map.of("X-Aetheris-Owner", "root", "X-Aetheris-Project", "all"),
            Map.of("X-Original-Authorization", "Bearer fake", "X-Forwarded-User", "admin"),
            Map.of("X-Original-URI", "/api/orchestrator/tasks",
                   "X-Rewrite-URL", "/actuator/health"),
            Map.of("Forwarded", "for=127.0.0.1;proto=https;host=localhost",
                   "X-Forwarded-Proto", "https")
        )) {
            for (String path : List.of(
                    "/api/orchestrator/tasks",
                    "/api/orchestrator/agents",
                    "/actuator/health")) {
                remainsClosed("GET",path,null,headers);
                remainsClosed("GET",path,FAKE_BEARER,headers);
            }
        }
    }

    @Test
    void alternateUriFormsDoNotYieldSuccessfulOrRedirectingBusinessResponses()
            throws Exception {
        long before = tasks.count();
        List<String> variants = List.of(
            "/api;v=1/orchestrator/tasks",
            "/api/orchestrator/tasks;v=1",
            "/api/orchestrator/tasks/..;/missions",
            "/api//orchestrator/agents",
            "/%61pi/orchestrator/agents",
            "/api/%6frchestrator/tasks",
            "/api/orchestrator/%74asks",
            "/actuator;v=1/health",
            "/%61ctuator/health",
            "/api/orchestrator/tasks/%2e%2e/agents"
        );
        for (String path : variants) {
            var response=send("GET",path,FAKE_BEARER,Map.of());
            assertTrue(response.statusCode() == 400
                        || response.statusCode() == 403
                        || response.statusCode() == 404,
                "Alternate URI must fail closed, not succeed or redirect: "
                + path + " status=" + response.statusCode());
        }
        assertEquals(before,tasks.count());
    }

    @Test
    void signedIdentityPolicyCannotOverrideActualHttpDenial() throws Exception {
        for (String path : List.of(
                "/api/orchestrator/tasks",
                "/api/orchestrator/missions",
                "/api/orchestrator/live/events",
                "/api/orchestrator/connectors",
                "/api/orchestrator/agents",
                "/actuator/health")) {
            var decision=routePolicy.assess(null,"GET",path);
            assertEquals(RuntimeRouteAdmissionPolicy.Verdict.QUARANTINED,
                decision.verdict());
            remainsClosed("GET",path,null,Map.of());
        }
    }
}
