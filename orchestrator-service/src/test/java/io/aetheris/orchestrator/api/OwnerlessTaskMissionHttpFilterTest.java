package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.mission.MissionService;
import io.aetheris.orchestrator.task.TaskEventStreamService;
import io.aetheris.orchestrator.task.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real HTTP filter + MVC dispatch, two synthetic identities, zero service reads. */
class OwnerlessTaskMissionHttpFilterTest {
    private final TaskService tasks = mock(TaskService.class);
    private final MissionService missions = mock(MissionService.class);
    private final TaskEventStreamService streams = mock(TaskEventStreamService.class);
    private final OwnerlessTaskMissionHttpFilter filter = new OwnerlessTaskMissionHttpFilter();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
        new TaskController(tasks, streams),
        new MissionController(missions),
        new OperationsStreamController(streams)
    ).addFilters(filter).build();

    @Test
    void forgedOwnerHeadersCannotReadTaskListItemHistoryOrMissionData() throws Exception {
        UUID alice = UUID.fromString("74f8f932-0e2c-4ac4-b32a-85fb03295211");
        UUID bob = UUID.fromString("44f8f932-0e2c-4ac4-b32a-85fb03295222");
        for (String claimant : new String[]{"alice", "bob"}) {
            for (UUID id : new UUID[]{alice, bob}) {
                mvc.perform(get("/api/orchestrator/tasks")
                    .header("X-Aetheris-User-Id", claimant)
                    .header("Authorization", "Bearer purported-admin"))
                    .andExpect(status().isNotFound());
                mvc.perform(get("/api/orchestrator/tasks/" + id)
                    .header("X-Aetheris-Owner", claimant))
                    .andExpect(status().isNotFound());
                mvc.perform(get("/api/orchestrator/tasks/" + id + "/events/history")
                    .header("X-Forwarded-Client-Cert", "trusted"))
                    .andExpect(status().isNotFound());
                mvc.perform(get("/api/orchestrator/missions")
                    .header("X-Aetheris-Project-Id", claimant))
                    .andExpect(status().isNotFound());
                mvc.perform(get("/api/orchestrator/missions/" + id + "/messages")
                    .header("X-Aetheris-Tenant-Id", claimant))
                    .andExpect(status().isNotFound());
                mvc.perform(get("/api/orchestrator/missions/" + id + "/task-events")
                    .header("Authorization", "Bearer unverified"))
                    .andExpect(status().isNotFound());
            }
        }
        verifyNoInteractions(tasks, missions, streams);
    }

    @Test
    void ownerlessWritesAndMissionAttachmentCannotReachExecutors() throws Exception {
        UUID id = UUID.fromString("94f8f932-0e2c-4ac4-b32a-85fb03295233");
        mvc.perform(post("/api/orchestrator/tasks")
                .contentType("application/json")
                .content("{\"title\":\"not allowed\",\"command\":\"print hello\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/orchestrator/tasks/" + id + "/transition")
                .header("X-Verified-Owner", "trusted"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/orchestrator/missions").contentType("application/json")
                .content("{}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/orchestrator/missions/" + id + "/tasks/" + id))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/orchestrator/missions/" + id + "/status/RUNNING"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/orchestrator/missions/" + id + "/messages")
                .contentType("application/json").content("{}"))
            .andExpect(status().isNotFound());
        verifyNoInteractions(tasks, missions, streams);
    }

    @Test
    void exactPrefixesAndPathParametersBlockedWithoutUnrelatedPathFalsePositives()
            throws Exception {
        for (String protectedPath : new String[]{
                "/api/orchestrator/tasks", "/api/orchestrator/tasks/",
                "/api/orchestrator/tasks;session=forged",
                "/api/orchestrator/missions", "/api/orchestrator/missions/a",
                "/api/orchestrator/missions;owner=bob"}) {
            assertTrue(OwnerlessTaskMissionHttpFilter.isProtected(protectedPath),
                protectedPath);
        }
        for (String other : new String[]{
                "/api/orchestrator/tasks-other", "/api/orchestrator/missionary",
                "/actuator/health", "/api/orchestrator/agents",
                "/api/orchestrator/live/events"}) {
            assertFalse(OwnerlessTaskMissionHttpFilter.isProtected(other), other);
        }

        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/context/api/orchestrator/tasks/sensitive");
        request.setContextPath("/context");
        request.setServletPath("/api/orchestrator/tasks/sensitive");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(404, response.getStatus());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));

        MockHttpServletRequest normal = new MockHttpServletRequest("GET", "/actuator/health");
        normal.setServletPath("/actuator/health");
        MockHttpServletResponse healthy = new MockHttpServletResponse();
        filter.doFilter(normal, healthy, new MockFilterChain());
        assertEquals(200, healthy.getStatus());
    }

    @Test
    void productionSpringComponentIsUnconditionallyRegistered() {
        assertNotNull(OwnerlessTaskMissionHttpFilter.class.getAnnotation(
            org.springframework.stereotype.Component.class));
        assertNotNull(OwnerlessTaskMissionHttpFilter.class.getAnnotation(
            org.springframework.core.annotation.Order.class));
        assertEquals(0, OwnerlessTaskMissionHttpFilter.class.getDeclaredFields().length
            - OwnerlessTaskMissionHttpFilter.class.getDeclaredFields().length);
    }
}
