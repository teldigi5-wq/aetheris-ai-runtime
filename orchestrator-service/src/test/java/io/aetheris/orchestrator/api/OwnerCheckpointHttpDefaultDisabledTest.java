package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.security.OwnerSensitiveApiQuarantineFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Default-production posture: no secret supplied, no opt-in enabled, and
 * no checkpoint route handler registered in the servlet application.
 */
@SpringBootTest(properties = {"aetheris.owner-checkpoint-http.enabled=false"})
@AutoConfigureMockMvc
class OwnerCheckpointHttpDefaultDisabledTest {
    @Autowired MockMvc mvc;

    @Test
    void ownerCheckpointRoutesAreNotRegisteredWithFeatureOff() throws Exception {
        mvc.perform(get("/api/orchestrator/owner-checkpoints/00000000-0000-4000-8000-000000000001/events"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/orchestrator/owner-checkpoints/tasks/00000000-0000-4000-8000-000000000001")
                .header("Authorization","Bearer forged.invalid.token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerSseStreamIsAbsentWhenBothFeaturesAreDisabled() throws Exception {
        mvc.perform(get("/api/orchestrator/owner-checkpoints/00000000-0000-4000-8000-000000000001/stream")
                .header("Authorization", "Bearer forged.value.signature"))
                .andExpect(status().isNotFound());
    }

    @Test
    void originalTaskAndMissionQuarantineStillRunsEvenWithoutTheOptionalController() throws Exception {
        mvc.perform(get("/api/orchestrator/live/events"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/orchestrator/tasks"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/orchestrator/missions"))
                .andExpect(status().isForbidden());
    }
}
