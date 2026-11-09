package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.task.TaskEventStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Actual loopback TCP / embedded Tomcat test of the production HTTP
 * containment components from PR #276, with test-only probe controllers.
 *
 * Deliberately does NOT boot the full orchestrator, use real customer data,
 * implement owner authentication, or prove production tenant isolation.
 * No live DB, external provider, credentials or background dispatcher.
 */
@SpringBootTest(
        classes = OwnerlessRealSocketContainmentTest.IsolatedServletApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.address=127.0.0.1",
                "server.port=0",
                "server.servlet.context-path=/runtime",
                "management.endpoints.enabled-by-default=false",
                "spring.task.scheduling.enabled=false"
        })
class OwnerlessRealSocketContainmentTest {
    private static final String SENSITIVE = "TEST_ONLY_SENSITIVE_CONTROLLER_REACHED";
    private static final String SAFE = "TEST_ONLY_UNRELATED_ROUTE_OK";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @LocalServerPort int port;
    @Autowired TaskEventStreamService streams;

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
    })
    @Import({ OwnerlessTaskMissionHttpFilter.class,
              OperationsStreamController.class,
              ProbeRoutes.class })
    static class IsolatedServletApp {
        @Bean
        TaskEventStreamService streamService() {
            return mock(TaskEventStreamService.class);
        }
    }

    @RestController
    static class ProbeRoutes {
        @GetMapping("/api/orchestrator/tasks")
        String task() { return SENSITIVE; }

        @GetMapping("/api/orchestrator/tasks/{id}/events/history")
        String history() { return SENSITIVE; }

        @PostMapping("/api/orchestrator/missions")
        String mission() { return SENSITIVE; }

        @GetMapping("/api/orchestrator/tasks-extra")
        String sibling() { return SAFE; }

        @GetMapping("/static/probe")
        String unrelated() { return SAFE; }
    }

    private HttpResponse<String> send(String method, String path,
                                      Map<String, String> headers) throws Exception {
        assertTrue(port > 0, "Embedded TCP server must have a real port");
        HttpRequest.Builder request = HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + port + "/runtime" + path))
                .timeout(Duration.ofSeconds(8));
        request.method(method, method.equals("POST")
                ? HttpRequest.BodyPublishers.ofString("{}")
                : HttpRequest.BodyPublishers.noBody());
        for (var header : headers.entrySet()) {
            request.header(header.getKey(), header.getValue());
        }
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertDenied(String method, String path,
                              Map<String, String> headers) throws Exception {
        HttpResponse<String> result = send(method, path, headers);
        assertEquals(404, result.statusCode(), method + " " + path);
        assertEquals("no-store", result.headers().firstValue("Cache-Control").orElse(null));
        assertEquals("nosniff",
                result.headers().firstValue("X-Content-Type-Options").orElse(null));
        assertFalse(result.body().contains(SENSITIVE), "Protected controller reached");
    }

    @Test
    void directTcpRequestsCannotReachTaskOrMissionEvenWithForgedIdentity() throws Exception {
        Map<String, String> spoofed = Map.of(
                "Authorization", "Bearer caller.supplied.invalid",
                "X-Aetheris-Owner", "admin",
                "X-Aetheris-Project", "all",
                "X-Forwarded-User", "root");
        for (Map<String, String> headers : new Map[]{Map.of(), spoofed}) {
            assertDenied("GET", "/api/orchestrator/tasks", headers);
            assertDenied("GET", "/api/orchestrator/tasks/123/events/history", headers);
            assertDenied("POST", "/api/orchestrator/missions", headers);
        }
    }

    @Test
    void realGlobalSseEndpointRemainsQuarantinedWithoutSubscribing() throws Exception {
        assertDenied("GET", "/api/orchestrator/live/events",
                Map.of("Accept", MediaType.TEXT_EVENT_STREAM_VALUE));
        verifyNoInteractions(streams);
    }

    @Test
    void unrelatedAndPrefixSiblingRoutesRemainAvailable() throws Exception {
        for (String path : new String[]{"/static/probe", "/api/orchestrator/tasks-extra"}) {
            HttpResponse<String> response = send("GET", path, Map.of());
            assertEquals(200, response.statusCode(), path);
            assertEquals(SAFE, response.body(), path);
        }
    }
}
