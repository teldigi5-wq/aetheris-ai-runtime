package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.task.TaskEventStreamService;
import io.aetheris.orchestrator.task.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Spring MVC route dispatch; two synthetic purported principals cannot
 * see task events with forged headers or task UUID knowledge. This is a
 * lockdown test, not proof of deployed owner/project authorization.
 */
class OwnerlessTaskEventLockdownTest {
    @Test
    void allLiveAndHistoryRoutesDenyWithoutTaskLookupOrJournalAccess() throws Exception {
        TaskService tasks = mock(TaskService.class);
        TaskEventStreamService streams = mock(TaskEventStreamService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new TaskController(tasks, streams),
            new OperationsStreamController(streams)
        ).build();
        UUID aliceTask = UUID.fromString("74f8f932-0e2c-4ac4-b32a-85fb03295211");
        UUID bobTask = UUID.fromString("44f8f932-0e2c-4ac4-b32a-85fb03295222");
        for (UUID id : new UUID[]{aliceTask, bobTask}) {
            for (String claimedIdentity : new String[]{"alice", "bob"}) {
                mvc.perform(get("/api/orchestrator/live/events")
                        .header("X-Aetheris-User-Id", claimedIdentity)
                        .header("X-Aetheris-Tenant-Id", "fake-tenant")
                        .header("Authorization", "Bearer forged-unverified-token"))
                    .andExpect(status().isNotFound());
                mvc.perform(get("/api/orchestrator/tasks/" + id + "/events/history")
                        .header("X-Aetheris-User-Id", claimedIdentity)
                        .header("X-Aetheris-Project-Id", "forged-project"))
                    .andExpect(status().isNotFound());
                mvc.perform(get("/api/orchestrator/tasks/" + id + "/events")
                        .header("X-Forwarded-Client-Cert", "forged-client")
                        .header("Authorization", "Bearer forged-unverified-token"))
                    .andExpect(status().isNotFound());
            }
        }
        verifyNoInteractions(tasks, streams);
    }

    @Test
    void gateIsNonOverridableEvenWithPriorEnvironmentFlagNames() throws Exception {
        TaskService tasks = mock(TaskService.class);
        TaskEventStreamService streams = mock(TaskEventStreamService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new TaskController(tasks, streams),
            new OperationsStreamController(streams)
        ).build();
        mvc.perform(get("/api/orchestrator/live/events")
            .param("owner", "admin")
            .param("projectId", "trusted")
            .header("X-Authenticated-User", "admin"))
            .andExpect(status().isNotFound());
        verifyNoInteractions(tasks, streams);
    }
}
