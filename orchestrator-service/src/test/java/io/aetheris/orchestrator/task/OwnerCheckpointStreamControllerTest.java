package io.aetheris.orchestrator.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aetheris.orchestrator.policy.OperationMode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * Real Spring MVC async SSE and persisted H2/Postgres journal tests using
 * synthetic accounts. Strictly opt-in test context; no production activation.
 */
@SpringBootTest(properties = {
        "aetheris.owner-checkpoint-http.enabled=true",
        "aetheris.owner-stream-http.enabled=true",
        "aetheris.owner-stream-http.max-duration-seconds=2",
        "aetheris.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class OwnerCheckpointStreamControllerTest {
    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final String BASE = "/api/orchestrator/owner-checkpoints";
    private static final String SECRET = "SENSITIVE_EVENT_PAYLOAD_NEVER_STREAMED";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TaskRepository tasks;
    @Autowired TaskEventJournalService journal;
    @Autowired TaskJournalOutboxRepository outbox;
    @Autowired OwnerTaskCheckpointRepository checkpoints;
    @Autowired PlatformTransactionManager manager;

    private String token(long uid, String... scopes) {
        Instant now=Instant.now();
        return "Bearer "+Jwts.builder()
                .issuer("aetheris-identity").audience().add("aetheris-api").and()
                .subject("synthetic@example.test")
                .claim("uid",uid).claim("role","API_CONSUMER")
                .claim("scopes",List.of(scopes))
                .issuedAt(Date.from(now.minusSeconds(15)))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(KEY.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256).compact();
    }
    private TaskEntity owned(long uid) {
        return new TransactionTemplate(manager).execute(tx ->
                tasks.save(new TaskEntity(UUID.randomUUID(),"Synthetic private",
                        "PRIVATE_COMMAND",OperationMode.PRIVATE,uid,null)));
    }
    private UUID append(UUID taskId,Instant when) {
        var writer=new VerifiedOwnerTaskJournalOutbox(tasks,journal,outbox,manager);
        return new TransactionTemplate(manager).execute(tx ->
                writer.appendInCurrentTransaction(new TaskEvent(taskId,when,
                        TaskState.RUNNING,"qa-engineer",SECRET,
                        Map.of("authorization","PRIVATE_TOKEN"))));
    }
    private UUID checkpoint(UUID taskId,long uid) throws Exception {
        MvcResult result=mvc.perform(post(BASE+"/tasks/{id}",taskId)
                .header("Authorization",token(uid,"orchestrator:read","orchestrator:write")))
                .andExpect(status().isOk()).andReturn();
        return UUID.fromString(json.readTree(result.getResponse()
                .getContentAsString()).get("checkpointId").asText());
    }

    @Test
    void signedOwnerStreamsOnlyOwnCommittedJournalAndDoesNotAdvanceAck() throws Exception {
        TaskEntity a=owned(101L),other=owned(202L);
        UUID id=checkpoint(a.getId(),101L);
        var start=mvc.perform(get(BASE+"/{id}/stream",id)
                        .header("Authorization",token(101L,"orchestrator:read")))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andReturn();
        // Insert after subscription; backdated timestamp must still arrive
        // because the sequenced journal uses DB commit order, not eventAt.
        UUID mine=append(a.getId(),Instant.parse("2026-01-01T00:00:00Z"));
        UUID theirs=append(other.getId(),Instant.parse("2026-01-01T00:00:00Z"));
        // Lease ends after two seconds in the explicit test-only context.
        Thread.sleep(2700);
        MockHttpServletResponse response=mvc.perform(asyncDispatch(start))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andReturn().getResponse();
        String frames=response.getContentAsString();
        assertThat(frames).contains("event:task-recorded")
                .contains("id:"+mine)
                .contains("PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME")
                .doesNotContain(theirs.toString(),SECRET,"PRIVATE_TOKEN",
                        "PRIVATE_COMMAND","synthetic@example.test");
        assertThat(checkpoints.findById(id).orElseThrow().getLastSequence())
                .as("server SSE send is not an owner ACK").isZero();
    }

    @Test
    void unauthenticatedForgedHeadersAndCrossOwnerCheckpointNeverStartSse() throws Exception {
        TaskEntity a=owned(101L);
        UUID id=checkpoint(a.getId(),101L);
        mvc.perform(get(BASE+"/{id}/stream",id)
                        .header("Authorization","Bearer forged.token.bytes")
                        .header("X-Aetheris-User-Id","101"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(BASE+"/{id}/stream",id)
                        .header("X-Aetheris-User-Id","101"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(BASE+"/{id}/stream",id)
                        .header("Authorization",token(202L,"orchestrator:read"))
                        .header("X-Aetheris-User-Id","101"))
                .andExpect(status().isNotFound());
        mvc.perform(get(BASE+"/{id}/stream",id)
                        .header("Authorization",token(101L,"orchestrator:write")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void quarantinedGlobalStreamsStayBlockedWithEvenValidBearer() throws Exception {
        for(String path:List.of("/api/orchestrator/live/events",
                "/api/orchestrator/tasks","/api/orchestrator/missions")) {
            mvc.perform(get(path).header("Authorization",token(101L,"orchestrator:read")))
                    .andExpect(status().isForbidden());
        }
    }
}
