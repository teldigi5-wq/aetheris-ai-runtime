package io.aetheris.orchestrator.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adversarial native-servlet unit tests, including forged gateway headers.
 * No mock identity is allowed to grant access.
 */
class OwnerSensitiveApiQuarantineFilterTest {

    private record Result(MockHttpServletResponse response, MockFilterChain chain) {}

    private Result request(String method, String requestUri, String servletPath) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, requestUri);
        request.setServletPath(servletPath);
        request.addHeader("Authorization", "Bearer untrusted-synthetic-jwt");
        request.addHeader("X-Aetheris-User-Id", "attacker-controlled-user");
        request.addHeader("X-Aetheris-Owner-Id", "claimed-owner");
        request.addHeader("X-Aetheris-Scopes", "orchestrator:read,orchestrator:write");
        request.addHeader("X-Forwarded-For", "127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new OwnerSensitiveApiQuarantineFilter().doFilter(request, response, chain);
        return new Result(response, chain);
    }

    @Test
    void forbidsAllTaskMissionAndLiveEndpointsIrrespectiveOfMethodOrForgedHeaders() throws Exception {
        for (String path : new String[] {
                "/api/orchestrator/tasks",
                "/api/orchestrator/tasks/11aa11aa-1111-4111-8111-111111111111",
                "/api/orchestrator/tasks/11aa11aa-1111-4111-8111-111111111111/events",
                "/api/orchestrator/tasks/11aa11aa-1111-4111-8111-111111111111/events/history",
                "/api/orchestrator/tasks/11aa11aa-1111-4111-8111-111111111111/transition",
                "/api/orchestrator/missions",
                "/api/orchestrator/missions/11aa11aa-1111-4111-8111-111111111111/messages",
                "/api/orchestrator/missions/11aa11aa-1111-4111-8111-111111111111/task-events",
                "/api/orchestrator/live/events"
        }) {
            for (String method : new String[] { "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD" }) {
                Result denied = request(method, path, path);
                assertThat(denied.response().getStatus()).as(method + " " + path).isEqualTo(403);
                assertThat(denied.response().getContentAsString()).contains("BLOCKED_OWNER_AUTH_NOT_VERIFIED");
                assertThat(denied.response().getHeader("Cache-Control")).isEqualTo("no-store");
                assertThat(denied.response().getContentAsString())
                        .doesNotContain("attacker-controlled-user", "claimed-owner", "jwt");
                assertThat(denied.chain().getRequest()).isNull();
            }
        }
    }

    @Test
    void decodedAndEncodedOwnerRoutesAreDeniedEvenWhenServletPathIsUnusual() throws Exception {
        for (String path : new String[] {
                "/api%2Forchestrator%2Flive%2Fevents",
                "/api/orchestrator/%74asks",
                "/api%252Forchestrator%252Fmissions",
                "/context/api/orchestrator/tasks/11aa11aa-1111-4111-8111-111111111111/events",
                "/api/orchestrator/tasks;ignored=true",
                "/api/orchestrator/live;ignored=true/events"
        }) {
            Result denied = request("GET", path, "/fallback");
            assertThat(denied.response().getStatus()).as(path).isEqualTo(403);
            assertThat(denied.chain().getRequest()).isNull();
        }
    }

    @Test
    void unrelatedPlatformOperationsAndHealthAreNotModified() throws Exception {
        for (String path : new String[] {
                "/actuator/health",
                "/api/orchestrator/models/providers",
                "/api/orchestrator/agents",
                "/api/orchestrator/control/emergency-stop"
        }) {
            Result allowed = request("GET", path, path);
            assertThat(allowed.response().getStatus()).as(path).isEqualTo(200);
            assertThat(allowed.chain().getRequest()).isNotNull();
        }
    }

    @Test
    void neverTreatsDirectLoopbackOrSyntheticBearerAsOwnerVerification() throws Exception {
        Result denied = request("GET", "/api/orchestrator/live/events", "/api/orchestrator/live/events");
        assertThat(denied.response().getStatus()).isEqualTo(403);
        assertThat(denied.response().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }
}
