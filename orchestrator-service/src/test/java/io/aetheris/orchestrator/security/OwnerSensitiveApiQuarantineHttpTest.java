package io.aetheris.orchestrator.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Actual Spring servlet filter integration: owner-sensitive API routes fail
 * before controllers can read private task/mission data. No real JWTs used.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OwnerSensitiveApiQuarantineHttpTest {

    @Autowired MockMvc mvc;

    @Test
    void directRuntimeTaskAndMissionReadsAreBlockedWithoutPrincipal() throws Exception {
        for (String uri : new String[] {
                "/api/orchestrator/tasks",
                "/api/orchestrator/tasks/11aa11aa-1111-4111-8111-111111111111",
                "/api/orchestrator/tasks/11aa11aa-1111-4111-8111-111111111111/events/history",
                "/api/orchestrator/missions",
                "/api/orchestrator/missions/11aa11aa-1111-4111-8111-111111111111/messages",
                "/api/orchestrator/live/events"
        }) {
            mvc.perform(get(uri))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.status").value("BLOCKED_OWNER_AUTH_NOT_VERIFIED"));
        }
    }

    @Test
    void forgedUserOwnerHeadersAndBearerDoNotAuthenticate() throws Exception {
        mvc.perform(get("/api/orchestrator/live/events")
                    .header("X-Aetheris-User-Id", "owner-a")
                    .header("X-Aetheris-Owner-Id", "owner-a")
                    .header("X-Aetheris-Scopes", "orchestrator:read")
                    .header("Authorization", "Bearer synthetic-attacker-value"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("BLOCKED_OWNER_AUTH_NOT_VERIFIED"));

        mvc.perform(post("/api/orchestrator/tasks")
                    .header("X-Aetheris-User-Id", "owner-b")
                    .header("X-Aetheris-Owner-Id", "owner-b")
                    .header("Authorization", "Bearer synthetic-attacker-value")
                    .contentType("application/json")
                    .content("{\"title\":\"unsafe\",\"command\":\"run\",\"mode\":\"BALANCED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unrelatedHealthEndpointRemainsAccessible() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
