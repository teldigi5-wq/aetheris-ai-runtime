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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class VerifiedOwnerJournalCursorTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String PRIVATE_MESSAGE = "PRIVATE_AGENT_INSTRUCTIONS";
    private static final String PRIVATE_META = "PRIVATE_SESSION_TOKEN";
    private static final Instant EVENT_TIME = Instant.parse("2026-10-13T10:00:00Z");

    @Autowired TaskRepository tasks;
    @Autowired TaskEventJournalRepository repository;
    @Autowired TaskEventJournalService journal;

    private VerifiedOwnerJournalCursor cursor() {
        return new VerifiedOwnerJournalCursor(tasks, repository,
                new RuntimeAccessTokenVerifier(SECRET));
    }

    private static String token(long uid, String audience, List<String> scopes) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(RuntimeAccessTokenVerifier.DEFAULT_ISSUER)
                .audience().add(audience).and()
                .subject("synthetic@example.test")
                .claim("uid", uid)
                .claim("role", "API_CONSUMER")
                .claim("scopes", scopes)
                .issuedAt(Date.from(now.minusSeconds(15)))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
    }

    private static String owner(long id) {
        return token(id, RuntimeAccessTokenVerifier.DEFAULT_AUDIENCE,
                List.of("orchestrator:read"));
    }

    private TaskEntity task(Long user, UUID project) {
        return tasks.save(user == null
                ? new TaskEntity(UUID.randomUUID(),"Legacy","private legacy command",
                        OperationMode.PRIVATE)
                : new TaskEntity(UUID.randomUUID(),"Owner task","private owner command",
                        OperationMode.PRIVATE,user,project));
    }

    private UUID write(UUID taskId, Instant when, String agent) {
        UUID id = journal.appendReturningId(new TaskEvent(taskId,when,TaskState.RUNNING,
                agent,PRIVATE_MESSAGE,Map.of("token",PRIVATE_META)));
        repository.flush();
        return id;
    }

    @Test
    void recoversMoreThanLegacyTwoHundredLimitWithBoundedSequentialPages() {
        TaskEntity a = task(101L,null);
        List<UUID> inserted = new ArrayList<>();
        for(int n=0;n<275;n++) {
            inserted.add(write(a.getId(),EVENT_TIME.plusSeconds(n),"qa-engineer"));
        }
        assertThat(journal.history(a.getId())).hasSize(200);

        var reader = cursor();
        UUID after = null;
        List<VerifiedOwnerJournalCursor.RecordedUpdate> all = new ArrayList<>();
        int pages = 0;
        do {
            var p=reader.page(owner(101L),a.getId(),after,37);
            assertThat(p.events().size()).isBetween(1,37);
            all.addAll(p.events());
            pages++;
            after=p.nextEventId();
            if(!p.hasMore()) break;
            assertThat(pages).isLessThan(15);
        } while(true);

        assertThat(pages).isEqualTo(8);
        assertThat(all).hasSize(275);
        assertThat(all.stream().map(VerifiedOwnerJournalCursor.RecordedUpdate::eventId).toList())
                .containsExactlyElementsOf(inserted);
        assertThat(new HashSet<>(all.stream().map(
                VerifiedOwnerJournalCursor.RecordedUpdate::eventId).toList()))
                .hasSize(275);
        assertThat(all.toString()).doesNotContain(PRIVATE_MESSAGE,PRIVATE_META,
                "private owner command","synthetic@example.test");
        assertThat(all).allMatch(e -> e.provenance().equals(
                "PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME"));
        var done=reader.page(owner(101L),a.getId(),after,37);
        assertThat(done.events()).isEmpty();
        assertThat(done.hasMore()).isFalse();
        assertThat(done.nextEventId()).isEqualTo(after);
    }

    @Test
    void sameTimestampEventsNeverCollapseDueToStableUuidTieBreaker() {
        TaskEntity a=task(101L,null);
        List<UUID> events = new ArrayList<>();
        for(int i=0;i<24;i++) events.add(write(a.getId(),EVENT_TIME,"planner"));
        var reader=cursor();
        UUID after=null;
        var seen=new ArrayList<UUID>();
        for(int i=0;i<8;i++){
            var page=reader.page(owner(101L),a.getId(),after,3);
            assertThat(page.events()).hasSize(3);
            for(var event:page.events()) {
                assertThat(event.eventAt()).isEqualTo(EVENT_TIME);
                seen.add(event.eventId());
            }
            after=page.nextEventId();
        }
        assertThat(seen).containsExactlyInAnyOrderElementsOf(events);
        assertThat(new HashSet<>(seen)).hasSize(24);
        assertThat(reader.page(owner(101L),a.getId(),after,3).events()).isEmpty();
    }

    @Test
    void ownerBAndForeignTaskCursorCannotReadOwnerAEvents() {
        TaskEntity a=task(101L,null),b=task(202L,null);
        UUID aCursor=write(a.getId(),EVENT_TIME,"qa-engineer");
        UUID bCursor=write(b.getId(),EVENT_TIME,"qa-engineer");
        var reader=cursor();
        assertThat(reader.page(owner(101L),a.getId(),null,20).events()).hasSize(1);
        assertThat(reader.page(owner(202L),b.getId(),null,20).events()).hasSize(1);
        assertThatThrownBy(()->reader.page(owner(202L),a.getId(),null,20))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("TASK_NOT_AVAILABLE");
        for(UUID forbidden:List.of(bCursor,UUID.randomUUID())) {
            assertThatThrownBy(()->reader.page(owner(101L),a.getId(),forbidden,20))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("TASK_NOT_AVAILABLE");
        }
        assertThatThrownBy(()->reader.page(owner(202L),b.getId(),aCursor,20))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("TASK_NOT_AVAILABLE");
    }

    @Test
    void legacyAndProjectRecordsCannotBeReadEvenWithOwnerSignedJwt() {
        TaskEntity legacy=task(null,null),project=task(101L,UUID.randomUUID());
        write(legacy.getId(),EVENT_TIME,"qa-engineer");
        write(project.getId(),EVENT_TIME,"qa-engineer");
        for(UUID taskId:List.of(legacy.getId(),project.getId(),UUID.randomUUID())){
            assertThatThrownBy(()->cursor().page(owner(101L),taskId,null,20))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("TASK_NOT_AVAILABLE");
        }
    }

    @Test
    void rejectsWrongAudienceScopeAndBadPageSizeBeforeAnyDataExposure() {
        TaskEntity a=task(101L,null);
        write(a.getId(),EVENT_TIME,"qa-engineer");
        for(String invalid:List.of(
                token(101L,"other-service",List.of("orchestrator:read")),
                token(101L,RuntimeAccessTokenVerifier.DEFAULT_AUDIENCE,
                        List.of("users:read")),
                "not-a-signed-token")) {
            assertThatThrownBy(()->cursor().page(invalid,a.getId(),null,20))
                    .isInstanceOf(RuntimeAccessTokenVerifier.RejectedToken.class)
                    .hasMessage("ACCESS_TOKEN_REJECTED");
        }
        for(int n:new int[]{-1,0,101,Integer.MAX_VALUE}){
            assertThatThrownBy(()->cursor().page(owner(101L),a.getId(),null,n))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("TASK_NOT_AVAILABLE");
        }
    }

    @Test
    void strangeAgentFieldsAreSuppressedAndSourcePayloadsAreNeverProjected() {
        TaskEntity a=task(101L,null);
        write(a.getId(),EVENT_TIME,"<script>alert('x')</script>");
        var page=cursor().page(owner(101L),a.getId(),null,1);
        assertThat(page.events()).hasSize(1);
        assertThat(page.events().get(0).agentId()).isNull();
        assertThat(page.toString()).doesNotContain("script",PRIVATE_MESSAGE,PRIVATE_META);
    }
}
