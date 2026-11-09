package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.task.TaskEventStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Guard the *interim denial*, not a claim of authenticated task access. */
class SensitiveStreamContainmentTest {
    @Test
    void globalEventStreamMustNotSubscribeOrLeakEvents() throws Exception {
        TaskEventStreamService streams = mock(TaskEventStreamService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new OperationsStreamController(streams)).build();

        mvc.perform(get("/api/orchestrator/live/events")
                    .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        verifyNoInteractions(streams);
    }

    @Test
    void taskAndMissionRequestsCannotReachControllers() throws Exception {
        OwnerlessTaskMissionHttpFilter filter = new OwnerlessTaskMissionHttpFilter();
        for (String path : new String[]{
                "/api/orchestrator/tasks",
                "/api/orchestrator/tasks/00000000-0000-4000-8000-000000000000/events",
                "/api/orchestrator/tasks/00000000-0000-4000-8000-000000000000/events/history",
                "/api/orchestrator/missions"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setServletPath(path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertEquals(404, response.getStatus(), path);
            assertEquals("no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
            assertNull(chain.getRequest(), "Request must not reach controller: " + path);
        }
    }

    @Test
    void decodedPathInfoRepresentationIsAlsoDenied() throws Exception {
        OwnerlessTaskMissionHttpFilter filter = new OwnerlessTaskMissionHttpFilter();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/%61pi/orchestrator/tasks/123/events");
        request.setServletPath("");
        request.setPathInfo("/api/orchestrator/tasks/123/events");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(404, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void unrelatedPathsRemainAvailable() throws Exception {
        OwnerlessTaskMissionHttpFilter filter = new OwnerlessTaskMissionHttpFilter();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/static/test.svg");
        request.setServletPath("/static/test.svg");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }
}
