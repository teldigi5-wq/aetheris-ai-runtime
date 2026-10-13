package io.aetheris.orchestrator.task;

import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.security.RuntimeAccessTokenVerifier;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real database + signed synthetic account identities. No HTTP listener,
 * remote owner header, Tauri app, owner PC or cloud model is used.
 */
@SpringBootTest
@Transactional
class VerifiedOwnerTaskTimelineTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String SENSITIVE_MESSAGE = "SECRET_PERSONAL_FILE_CONTENT";
    private static final String SENSITIVE_META = "VERY_PRIVATE_TOKEN";
    @Autowired TaskRepository tasks;
    @Autowired TaskEventJournalService journal;

    private VerifiedOwnerTaskTimeline timeline() {
        return new VerifiedOwnerTaskTimeline(tasks, journal,
                new RuntimeAccessTokenVerifier(SECRET));
    }

    private static String jwt(long uid, String audience, List<String> scopes) {
        Instant t = Instant.now();
        return Jwts.builder()
                .issuer(RuntimeAccessTokenVerifier.DEFAULT_ISSUER)
                .audience().add(audience).and()
                .subject("synthetic@example.test")
                .claim("uid", uid)
                .claim("role", "API_CONSUMER")
                .claim("scopes", scopes)
                .issuedAt(Date.from(t.minusSeconds(15)))
                .expiration(Date.from(t.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
    }
    private static String ownerA() {
        return jwt(101L, RuntimeAccessTokenVerifier.DEFAULT_AUDIENCE,
                List.of("orchestrator:read"));
    }
    private static String ownerB() {
        return jwt(202L, RuntimeAccessTokenVerifier.DEFAULT_AUDIENCE,
                List.of("orchestrator:read"));
    }
    private TaskEntity task(long owner, UUID project) {
        return tasks.save(new TaskEntity(UUID.randomUUID(), "Sensitive mission",
                "PRIVATE_COMMAND_DO_NOT_DISPLAY", OperationMode.PRIVATE, owner, project));
    }
    private void add(UUID task, TaskState state, String agentId, long seconds) {
        journal.append(new TaskEvent(task, Instant.parse("2026-10-13T12:00:00Z").plusSeconds(seconds),
                state, agentId, SENSITIVE_MESSAGE,
                Map.of("accessToken", SENSITIVE_META)));
    }

    @Test
    void readHistoryOfOwnerAExcludesOwnerBAndAllPrivateMessages() {
        TaskEntity a=task(101L,null), b=task(202L,null);
        add(a.getId(),TaskState.QUEUED,"planner",0);
        add(a.getId(),TaskState.RUNNING,"backend-engineer",1);
        add(a.getId(),TaskState.VERIFYING,"qa-engineer",2);
        add(b.getId(),TaskState.COMPLETED,"other-owner",3);
        tasks.flush();

        var timeline=timeline();
        var events=timeline.recentHistory(ownerA(),a.getId());
        assertThat(events).hasSize(3);
        assertThat(events).allMatch(e -> e.provenance() ==
                VerifiedOwnerTaskTimeline.Provenance.PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME);
        assertThat(events).extracting(VerifiedOwnerTaskTimeline.TimelineUpdate::state)
                .containsExactly(TaskState.QUEUED,TaskState.RUNNING,TaskState.VERIFYING);
        assertThat(events).extracting(VerifiedOwnerTaskTimeline.TimelineUpdate::agentId)
                .containsExactly("planner","backend-engineer","qa-engineer");
        assertThat(events).allMatch(e->e.taskId().equals(a.getId()));
        assertThat(events.toString()).doesNotContain(SENSITIVE_MESSAGE,SENSITIVE_META,
                "PRIVATE_COMMAND", "synthetic@example.test");
        assertThatThrownBy(()->timeline.recentHistory(ownerB(),a.getId()))
                .isInstanceOf(NoSuchElementException.class).hasMessage("TASK_NOT_AVAILABLE");
        assertThat(timeline.recentHistory(ownerB(),b.getId())).hasSize(1);
        assertThatThrownBy(()->timeline.recentHistory(ownerA(),b.getId()))
                .isInstanceOf(NoSuchElementException.class).hasMessage("TASK_NOT_AVAILABLE");
    }

    @Test
    void legacyAndProjectBoundTasksDenyHistoryEvenWithCorrectOwnerJwt() {
        TaskEntity scoped=task(101L,UUID.randomUUID());
        TaskEntity old=tasks.save(new TaskEntity(UUID.randomUUID(),"ownerless",
                "private", OperationMode.PRIVATE));
        add(scoped.getId(),TaskState.RUNNING,"agent",1);
        add(old.getId(),TaskState.COMPLETED,"agent",2);
        var timeline=timeline();
        for(UUID denied:List.of(scoped.getId(),old.getId(),UUID.randomUUID())) {
            assertThatThrownBy(()->timeline.recentHistory(ownerA(),denied))
                    .isInstanceOf(NoSuchElementException.class).hasMessage("TASK_NOT_AVAILABLE");
        }
    }

    @Test
    void everyFutureEventIsRecheckedForOwnerAndMatchingTask() {
        TaskEntity a=task(101L,null),b=task(202L,null);
        var one=new TaskEvent(a.getId(), Instant.now(), TaskState.RUNNING,
                "backend-engineer", SENSITIVE_MESSAGE,Map.of("secret",SENSITIVE_META));
        var forged=new TaskEvent(b.getId(), Instant.now(),TaskState.COMPLETED,
                "fake-agent", SENSITIVE_MESSAGE,Map.of("secret",SENSITIVE_META));
        var timeline=timeline();
        var safe=timeline.inspectSignedOwnerEvent(ownerA(),a.getId(),one);
        assertThat(safe.taskId()).isEqualTo(a.getId());
        assertThat(safe.provenance()).isEqualTo(
                VerifiedOwnerTaskTimeline.Provenance.UNTRUSTED_EVENT_ARGUMENT);
        assertThat(safe.toString()).doesNotContain(SENSITIVE_MESSAGE,SENSITIVE_META);
        assertThatThrownBy(()->timeline.inspectSignedOwnerEvent(ownerA(),a.getId(),forged))
                .isInstanceOf(NoSuchElementException.class).hasMessage("TASK_NOT_AVAILABLE");
        assertThatThrownBy(()->timeline.inspectSignedOwnerEvent(ownerB(),a.getId(),one))
                .isInstanceOf(NoSuchElementException.class).hasMessage("TASK_NOT_AVAILABLE");
    }

    @Test
    void malformedAgentFieldIsDiscardedAndNeverReachesStatusView() {
        TaskEntity a=task(101L,null);
        add(a.getId(),TaskState.RUNNING,"<script>alert('x')</script>",0);
        var events=timeline().recentHistory(ownerA(),a.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).agentId()).isNull();
        assertThat(events.toString()).doesNotContain("script");
    }

    @Test
    void wrongAudienceAndScopeFailBeforeReadingTaskHistory() {
        TaskEntity a=task(101L,null);
        add(a.getId(),TaskState.RUNNING,"backend-engineer",0);
        for(String token:List.of(
                jwt(101L,"different-recipient",List.of("orchestrator:read")),
                jwt(101L,RuntimeAccessTokenVerifier.DEFAULT_AUDIENCE,List.of("users:read")),
                "not-a-token")) {
            assertThatThrownBy(()->timeline().recentHistory(token,a.getId()))
                    .isInstanceOf(RuntimeAccessTokenVerifier.RejectedToken.class)
                    .hasMessage("ACCESS_TOKEN_REJECTED");
        }
    }

    @Test
    void journalProjectionIsLimitedToLatestHundredEvents() {
        TaskEntity a=task(101L,null);
        for(int i=0;i<121;i++) {
            add(a.getId(),TaskState.RUNNING,"qa-engineer",i);
        }
        var events=timeline().recentHistory(ownerA(),a.getId());
        assertThat(events).hasSize(VerifiedOwnerTaskTimeline.MAX_HISTORY_EVENTS);
        assertThat(events.get(0).timestamp())
                .isEqualTo(Instant.parse("2026-10-13T12:00:21Z"));
        assertThat(events.get(events.size()-1).timestamp())
                .isEqualTo(Instant.parse("2026-10-13T12:02:00Z"));
    }
}
