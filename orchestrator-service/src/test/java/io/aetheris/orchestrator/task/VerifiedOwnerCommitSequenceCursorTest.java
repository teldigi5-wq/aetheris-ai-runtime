package io.aetheris.orchestrator.task;

import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.security.RuntimeAccessTokenVerifier;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real Spring/H2 commits, rollbacks and concurrent transactions; no external
 * user, network transport, automated task execution or physical workstation.
 */
@SpringBootTest
class VerifiedOwnerCommitSequenceCursorTest {
    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final String PRIVATE_MSG = "PRIVATE_MESSAGE_NEVER_IN_CURSOR";
    private static final String PRIVATE_META = "SECRET_TOKEN_NEVER_IN_CURSOR";
    @Autowired TaskRepository tasks;
    @Autowired TaskEventJournalService journal;
    @Autowired TaskEventJournalRepository events;
    @Autowired TaskJournalOutboxRepository outbox;
    @Autowired PlatformTransactionManager manager;

    private TransactionTemplate tx() { return new TransactionTemplate(manager); }

    private TaskEntity task(Long owner, UUID project) {
        return tx().execute(t -> tasks.save(owner == null
                ? new TaskEntity(UUID.randomUUID(),"legacy","private",OperationMode.PRIVATE)
                : new TaskEntity(UUID.randomUUID(),"owner","private command",
                        OperationMode.PRIVATE,owner,project)));
    }

    private String token(long id, String audience, List<String> scopes) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(RuntimeAccessTokenVerifier.DEFAULT_ISSUER)
                .audience().add(audience).and()
                .subject("synthetic@example.test")
                .claim("uid",id).claim("role","API_CONSUMER")
                .claim("scopes",scopes)
                .issuedAt(Date.from(now.minusSeconds(15)))
                .expiration(Date.from(now.plusSeconds(250)))
                .signWith(Keys.hmacShaKeyFor(KEY.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256).compact();
    }
    private String owner(long id) {
        return token(id,RuntimeAccessTokenVerifier.DEFAULT_AUDIENCE,List.of("orchestrator:read"));
    }
    private VerifiedOwnerTaskJournalOutbox writer() {
        return new VerifiedOwnerTaskJournalOutbox(tasks,journal,outbox,manager);
    }
    private VerifiedOwnerCommitSequenceCursor reader() {
        return new VerifiedOwnerCommitSequenceCursor(tasks,events,
                new RuntimeAccessTokenVerifier(KEY));
    }
    private TaskEvent event(UUID id, Instant at, String agent) {
        return new TaskEvent(id,at,TaskState.RUNNING,agent,
                PRIVATE_MSG,Map.of("accessToken",PRIVATE_META));
    }
    private UUID append(VerifiedOwnerTaskJournalOutbox writer, TaskEvent input) {
        return tx().execute(t -> writer.appendInCurrentTransaction(input));
    }

    @Test
    void replayUsesCommitOrderEvenWhenTimestampsAreBackdatedOrEqual() {
        TaskEntity a=task(101L,null);
        var write=writer();
        Instant base=Instant.parse("2026-10-13T13:00:00Z");
        UUID first=append(write,event(a.getId(),base.plusSeconds(40),"planner"));
        UUID second=append(write,event(a.getId(),base.minusSeconds(500),"backend-engineer"));
        UUID third=append(write,event(a.getId(),base.minusSeconds(500),"qa-engineer"));
        var cursor=reader();
        var page1=cursor.page(owner(101L),a.getId(),null,2);
        assertThat(page1.hasMore()).isTrue();
        assertThat(page1.events()).extracting(VerifiedOwnerCommitSequenceCursor.SafeEvent::sequence)
                .containsExactly(1L,2L);
        assertThat(page1.events()).extracting(VerifiedOwnerCommitSequenceCursor.SafeEvent::eventId)
                .containsExactly(first,second);
        var page2=cursor.page(owner(101L),a.getId(),page1.nextEventId(),2);
        assertThat(page2.hasMore()).isFalse();
        assertThat(page2.events()).extracting(VerifiedOwnerCommitSequenceCursor.SafeEvent::eventId)
                .containsExactly(third);
        assertThat(page2.events().get(0).sequence()).isEqualTo(3);
        assertThat(page1.toString()+page2.toString()).doesNotContain(
                PRIVATE_MSG,PRIVATE_META,"private command","synthetic@example.test");
        assertThat(page2.coverage()).isEqualTo("OUTBOX_SEQUENCED_ONLY_LEGACY_NOT_INCLUDED");
    }

    @Test
    void rollbackDoesNotConsumeSequenceAndLegacyJournalEventsCannotPoseAsSequenced() {
        TaskEntity a=task(101L,null);
        var writer=writer();
        Instant base=Instant.parse("2026-10-13T13:00:00Z");
        UUID first=append(writer,event(a.getId(),base,"qa-engineer"));
        assertThatThrownBy(()->tx().executeWithoutResult(status->{
            writer.appendInCurrentTransaction(event(a.getId(),base.plusSeconds(1),"qa-engineer"));
            throw new IllegalStateException("deliberate rollback");
        })).isInstanceOf(IllegalStateException.class);
        UUID second=append(writer,event(a.getId(),base.minusSeconds(100),"qa-engineer"));
        assertThat(events.sequencedPageAfter(a.getId(),0,
                org.springframework.data.domain.PageRequest.of(0,10)))
                .extracting(TaskJournalSequencedRow::sequence).containsExactly(1L,2L);
        assertThat(tasks.findById(a.getId()).orElseThrow().getLastJournalSequence()).isEqualTo(2L);
        UUID legacy=tx().execute(status -> journal.appendReturningId(
                event(a.getId(),base.plusSeconds(1000),"legacy-agent")));
        var page=reader().page(owner(101L),a.getId(),null,10);
        assertThat(page.events()).extracting(VerifiedOwnerCommitSequenceCursor.SafeEvent::eventId)
                .containsExactly(first,second).doesNotContain(legacy);
        assertThatThrownBy(()->reader().page(owner(101L),a.getId(),legacy,10))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("TASK_NOT_AVAILABLE");
    }

    @Test
    void concurrentTaskWritersSerializeUnderTheSameDatabaseLock() throws Exception {
        TaskEntity a=task(101L,null);
        var writer=writer();
        Instant now=Instant.parse("2026-10-13T13:00:00Z");
        var allocated=new CountDownLatch(1);
        var unblock=new CountDownLatch(1);
        var startedSecond=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->tx().execute(status->{
                UUID id=writer.appendInCurrentTransaction(event(
                        a.getId(),now.plusSeconds(200),"first-agent"));
                allocated.countDown();
                try {
                    if(!unblock.await(6,TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                return id;
            }));
            assertThat(allocated.await(6,TimeUnit.SECONDS)).isTrue();
            var second=pool.submit(()->{
                startedSecond.countDown();
                return append(writer,event(a.getId(),now.minusSeconds(600),"second-agent"));
            });
            assertThat(startedSecond.await(5,TimeUnit.SECONDS)).isTrue();
            unblock.countDown();
            UUID firstId=first.get(10,TimeUnit.SECONDS);
            UUID secondId=second.get(10,TimeUnit.SECONDS);
            var page=reader().page(owner(101L),a.getId(),null,10);
            assertThat(page.events()).extracting(VerifiedOwnerCommitSequenceCursor.SafeEvent::eventId)
                    .containsExactly(firstId,secondId);
            assertThat(page.events()).extracting(VerifiedOwnerCommitSequenceCursor.SafeEvent::sequence)
                    .containsExactly(1L,2L);
            assertThat(page.hasMore()).isFalse();
        }finally{unblock.countDown();pool.shutdownNow();}
    }

    @Test
    void signedOwnerRecheckedOnEachPageAndForeignProjectLegacyRejected() {
        TaskEntity a=task(101L,null),other=task(202L,null),
                project=task(101L,UUID.randomUUID()),legacy=task(null,null);
        var writer=writer();
        UUID aEvent=append(writer,event(a.getId(),Instant.now(),"qa-engineer"));
        UUID bEvent=append(writer,event(other.getId(),Instant.now(),"qa-engineer"));
        var reader=reader();
        assertThat(reader.page(owner(101L),a.getId(),null,10).events()).hasSize(1);
        for(UUID taskId:List.of(other.getId(),project.getId(),legacy.getId(),UUID.randomUUID())) {
            assertThatThrownBy(()->reader.page(owner(101L),taskId,null,10))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("TASK_NOT_AVAILABLE");
        }
        assertThatThrownBy(()->reader.page(owner(101L),a.getId(),bEvent,10))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(()->reader.page(owner(202L),other.getId(),aEvent,10))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(()->reader.page(
                token(101L,"wrong-app",List.of("orchestrator:read")),a.getId(),null,10))
                .isInstanceOf(RuntimeAccessTokenVerifier.RejectedToken.class);
        assertThatThrownBy(()->reader.page(
                token(101L,RuntimeAccessTokenVerifier.DEFAULT_AUDIENCE,List.of("users:read")),
                a.getId(),null,10)).isInstanceOf(RuntimeAccessTokenVerifier.RejectedToken.class);
        for(int size:new int[]{0,-1,101,9999}){
            assertThatThrownBy(()->reader.page(owner(101L),a.getId(),null,size))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }
}
