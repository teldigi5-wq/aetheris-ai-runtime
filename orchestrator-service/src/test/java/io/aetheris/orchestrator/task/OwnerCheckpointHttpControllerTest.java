package io.aetheris.orchestrator.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.task.TaskEvent;
import io.aetheris.orchestrator.task.TaskJournalOutboxRepository;
import io.aetheris.orchestrator.task.TaskRepository;
import io.aetheris.orchestrator.task.TaskState;
import io.aetheris.orchestrator.task.VerifiedOwnerTaskJournalOutbox;
import io.aetheris.orchestrator.task.TaskEventJournalService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Actual Spring servlet & PostgreSQL/H2 JPA integration tests with the
 * explicitly enabled opt-in controller. These tests use only synthetic
 * tokens/owners and must not activate live task/mission/global SSE routes.
 */
@SpringBootTest(properties = {
        "aetheris.owner-checkpoint-http.enabled=true",
        "aetheris.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class OwnerCheckpointHttpControllerTest {
    private static final String TEST_KEY = "0123456789abcdef0123456789abcdef";
    private static final String BASE = "/api/orchestrator/owner-checkpoints";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired TaskRepository tasks;
    @Autowired TaskEventJournalService journal;
    @Autowired TaskJournalOutboxRepository outbox;
    @Autowired PlatformTransactionManager manager;

    private String jwt(long ownerId, String audience, String... scopes) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("aetheris-identity")
                .audience().add(audience).and()
                .subject("synthetic-owner@example.test")
                .claim("uid", ownerId)
                .claim("role", "API_CONSUMER")
                .claim("scopes", List.of(scopes))
                .issuedAt(Date.from(now.minusSeconds(15)))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(TEST_KEY.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
    }

    private String read(long uid) { return "Bearer " + jwt(uid,"aetheris-api","orchestrator:read"); }
    private String writer(long uid) {
        return "Bearer " + jwt(uid,"aetheris-api","orchestrator:read","orchestrator:write");
    }

    private TaskEntity task(long uid) {
        return new TransactionTemplate(manager).execute(status ->
                tasks.save(new TaskEntity(UUID.randomUUID(),"Secret task",
                        "PRIVATE_COMMAND_MUST_NOT_LEAK",OperationMode.PRIVATE,uid,null)));
    }

    private UUID append(UUID task, Instant when) {
        var outboxWriter = new VerifiedOwnerTaskJournalOutbox(
                tasks,journal,outbox,manager);
        return new TransactionTemplate(manager).execute(status ->
                outboxWriter.appendInCurrentTransaction(new TaskEvent(
                        task,when,TaskState.RUNNING,"qa-engineer",
                        "PRIVATE_EVENT_MESSAGE_MUST_NOT_LEAK",
                        Map.of("authorization", "PRIVATE_METADATA_MUST_NOT_LEAK"))));
    }

    private UUID createCheckpoint(TaskEntity task, String authorization) throws Exception {
        var response = mvc.perform(post(BASE+"/tasks/{taskId}",task.getId())
                        .header("Authorization",authorization))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();
        JsonNode value=mapper.readTree(response.getContentAsString());
        return UUID.fromString(value.path("checkpointId").asText());
    }

    @Test
    void bearerAuthorizedOwnerCanCreateReplayAndContiguouslyAcknowledge() throws Exception {
        TaskEntity a=task(101L);
        Instant t=Instant.parse("2026-10-14T09:00:00Z");
        UUID first=append(a.getId(),t.plusSeconds(100));
        UUID second=append(a.getId(),t.minusSeconds(200));
        UUID checkpoint=createCheckpoint(a,writer(101L));

        mvc.perform(get(BASE+"/{checkpointId}/events",checkpoint)
                        .header("Authorization",read(101L)).param("limit","1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].eventId").value(first.toString()))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.provenance").value("OUTBOX_SEQUENCED_ONLY_ACK_NOT_ATTESTED"));

        mvc.perform(post(BASE+"/{checkpointId}/ack",checkpoint)
                        .header("Authorization",writer(101L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\""+second+"\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ACK_OUT_OF_ORDER"));
        mvc.perform(post(BASE+"/{checkpointId}/ack",checkpoint)
                        .header("Authorization",writer(101L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\""+first+"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACK_ADVANCED"));
        mvc.perform(post(BASE+"/{checkpointId}/ack",checkpoint)
                        .header("Authorization",writer(101L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\""+first+"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACK_DUPLICATE"));
        String replay=mvc.perform(get(BASE+"/{checkpointId}/events",checkpoint)
                        .header("Authorization",read(101L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastAcknowledged").value(1))
                .andExpect(jsonPath("$.events[0].eventId").value(second.toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(replay).doesNotContain("PRIVATE_COMMAND","PRIVATE_EVENT_MESSAGE",
                "PRIVATE_METADATA","synthetic-owner@example.test");
    }

    @Test
    void forgedHeadersAndWrongSignedAccountNeverReadOrAcknowledgeOtherUser() throws Exception {
        TaskEntity a=task(101L),b=task(202L);
        UUID event=append(a.getId(),Instant.now());
        UUID checkpoint=createCheckpoint(a,writer(101L));
        mvc.perform(get(BASE+"/{checkpointId}/events",checkpoint)
                        .header("Authorization",read(202L))
                        .header("X-Aetheris-User-Id","101")
                        .header("X-Aetheris-Owner-Id","101"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("TASK_NOT_AVAILABLE"));
        mvc.perform(post(BASE+"/{checkpointId}/ack",checkpoint)
                        .header("Authorization",writer(202L))
                        .header("X-Aetheris-User-Id","101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\""+event+"\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post(BASE+"/tasks/{taskId}",b.getId())
                        .header("Authorization",writer(101L))
                        .header("X-Aetheris-User-Id","202"))
                .andExpect(status().isNotFound());
        mvc.perform(get(BASE+"/{checkpointId}/events",checkpoint)
                        .header("Authorization",read(101L)))
                .andExpect(jsonPath("$.lastAcknowledged").value(0));
    }

    @Test
    void malformedUnsignedAndWrongAudienceBearerFailWithNoStore() throws Exception {
        TaskEntity a=task(101L);
        for(String invalid:new String[]{"Bearer malformed.jwt.value","synthetic",
                "Bearer abc def", "Bearer "+jwt(101L,"other-api","orchestrator:read")}) {
            mvc.perform(post(BASE+"/tasks/{taskId}",a.getId())
                            .header("Authorization",invalid))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Cache-Control","no-store"))
                    .andExpect(jsonPath("$.status").value("UNAUTHORIZED"));
        }
        mvc.perform(post(BASE+"/tasks/{taskId}",a.getId())
                        .header("X-Aetheris-User-Id","101"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readonlyTokenCannotMutateButCanReplay() throws Exception {
        TaskEntity a=task(101L);
        UUID checkpoint=createCheckpoint(a,writer(101L));
        UUID event=append(a.getId(),Instant.now());
        mvc.perform(post(BASE+"/tasks/{taskId}",a.getId())
                        .header("Authorization",read(101L)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(BASE+"/{checkpointId}/ack",checkpoint)
                        .header("Authorization",read(101L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\""+event+"\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(BASE+"/{checkpointId}/events",checkpoint)
                        .header("Authorization",read(101L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].eventId").value(event.toString()));
    }

    @Test
    void legacyGlobalTaskAndMissionRoutesRemainQuarantinedEvenWithValidSignedToken() throws Exception {
        for (String uri : List.of("/api/orchestrator/tasks",
                "/api/orchestrator/live/events", "/api/orchestrator/missions")) {
            mvc.perform(get(uri).header("Authorization",writer(101L)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value("BLOCKED_OWNER_AUTH_NOT_VERIFIED"));
        }
    }
}
