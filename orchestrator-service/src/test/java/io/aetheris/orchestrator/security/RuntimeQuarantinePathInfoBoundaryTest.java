package io.aetheris.orchestrator.security;

import io.aetheris.orchestrator.api.OwnerlessTaskMissionHttpFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Disposable request-representation test, NOT deployed Tomcat exploit proof.
 *
 * Under a servlet mapping with an empty servletPath, the decoded pathInfo may
 * carry the protected route while the original raw URI stays percent-encoded.
 * Both production quarantine filters must deny even in that split mapping.
 */
class RuntimeQuarantinePathInfoBoundaryTest {

    private static MockHttpServletRequest request(String encodedUri, String decodedPathInfo) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", encodedUri);
        request.setServletPath("");
        request.setPathInfo(decodedPathInfo);
        return request;
    }

    private static void assertClosed(MockHttpServletResponse response, MockFilterChain chain) {
        assertEquals(404, response.getStatus(), "Protected pathInfo must fail closed");
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertNull(chain.getRequest(), "The controller/filter continuation must never run");
    }

    @Test
    void genericApiAndActuatorAreDeniedWhenOnlyDecodedPathInfoRevealsProtectedPath()
            throws Exception {
        RuntimeUncertifiedApiHttpFilter filter = new RuntimeUncertifiedApiHttpFilter();
        String[][] examples = {
            {"/%61pi/orchestrator/agents", "/api/orchestrator/agents"},
            {"/%61ctuator/health", "/actuator/health"},
            {"/context/%61pi/orchestrator/live/events", "/api/orchestrator/live/events"}
        };
        for (String[] pair : examples) {
            MockHttpServletRequest request = request(pair[0], pair[1]);
            if (pair[0].startsWith("/context/")) request.setContextPath("/context");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertClosed(response, chain);
        }
    }

    @Test
    void ownerlessTaskAndMissionRoutesAreDeniedByTheirFirstPriorityFilter()
            throws Exception {
        OwnerlessTaskMissionHttpFilter filter = new OwnerlessTaskMissionHttpFilter();
        for (String[] pair : new String[][]{
            {"/%61pi/orchestrator/tasks", "/api/orchestrator/tasks"},
            {"/%61pi/orchestrator/tasks/00000000-0000-4000-8000-000000000000/events",
             "/api/orchestrator/tasks/00000000-0000-4000-8000-000000000000/events"},
            {"/%61pi/orchestrator/missions", "/api/orchestrator/missions"}
        }) {
            MockHttpServletRequest request = request(pair[0], pair[1]);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertClosed(response, chain);
        }
    }

    @Test
    void unrelatedStaticPathsDoNotBecomeProtectedDueToPathInfoInspection()
            throws Exception {
        MockHttpServletRequest request = request("/static/logo.svg", "/static/logo.svg");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new RuntimeUncertifiedApiHttpFilter().doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest(), "Unrelated paths should still be delegated");
    }
}
