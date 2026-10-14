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
 * H2 and PostgreSQL-backed tests with two synthetic signed accounts.
 * This is NOT a live client delivery or user-PC integration test.
 */
@SpringBootTest
class VerifiedOwnerDeliveryCheckpointTest {
    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final String PRIVATE_TEXT = "PERSONAL_TASK_INSTRUCTIONS_DO_NOT_RENDER";
    private static final String PRIVATE_META = "SECRET_CREDENTIAL_DO_NOT_RENDER";

    @Autowired TaskRepository tasks;
    @Autowired TaskEventJournalRepository events;
    @Autowired TaskEventJournalService journal;
    @Autowired TaskJournalOutboxRepository outbox;
    @Autowired OwnerTaskCheckpointRepository checkpoints;
    @Autowired PlatformTransactionManager manager;

    private TransactionTemplate tx() { return new TransactionTemplate(manager); }

    private TaskEntity task(Long owner, UUID project) {
        return tx().execute(status -> tasks.save(owner == null
                ? new TaskEntity(UUID.randomUUID(), "Legacy", PRIVATE_TEXT,
                        OperationMode.PRIVATE)
                : new TaskEntity(UUID.randomUUID(), "Owned", PRIVATE_TEXT,
                        OperationMode.PRIVATE, owner, project)));
    }

    private static String jwt(long uid, String audience, List<String> scopes) {
        Instant now = Instant.now();
        return Jwts.builder().issuer(RuntimeAccessTokenVerifier.DEFAULT_ISSUER)
                .audience().add(audience).and()
                .subject("synthetic-owner@example.test")
                .claim("uid", uid).claim("role", "API_CONSUMER")
                .claim("scopes", scopes)
                .issuedAt(Date.from(now.minusSeconds(10)))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(KEY.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
    }

    private static String owner(long uid) {
        return jwt(uid, RuntimeAccessTokenVerifier.DEFAULT_AUDIENCE,
                List.of("orchestrator:read"));
    }

    private VerifiedOwnerDeliveryCheckpoint checkpointService() {
        return new VerifiedOwnerDeliveryCheckpoint(tasks, events, checkpoints,
                new RuntimeAccessTokenVerifier(KEY), manager);
    }

    private UUID record(UUID taskId, Instant time, String agentId) {
        var writer = new VerifiedOwnerTaskJournalOutbox(tasks, journal, outbox, manager);
        return tx().execute(status -> writer.appendInCurrentTransaction(
                new TaskEvent(taskId, time, TaskState.RUNNING, agentId, PRIVATE_TEXT,
                        Map.of("accessToken", PRIVATE_META))));
    }

    @Test
    void survivesNewReaderInstanceAndRejectsOutOfOrderOrForeignAcknowledgements() {
        TaskEntity a = task(101L, null), b = task(202L, null);
        Instant base = Instant.parse("2026-10-14T05:00:00Z");
        UUID a1=record(a.getId(),base.plusSeconds(200),"planner");
        UUID a2=record(a.getId(),base.minusSeconds(300),"backend-engineer");
        UUID a3=record(a.getId(),base.minusSeconds(300),"qa-engineer");
        UUID b1=record(b.getId(),base,"other-owner");

        var service=checkpointService();
        var handle=service.open(owner(101L),a.getId());
        var batch=service.resume(owner(101L),handle.checkpointId(),2);
        assertThat(batch.events()).extracting(VerifiedOwnerDeliveryCheckpoint.SafeUpdate::eventId)
                .containsExactly(a1,a2);
        assertThat(batch.hasMore()).isTrue();
        assertThat(batch.provenance()).isEqualTo("OUTBOX_SEQUENCED_ONLY_ACK_NOT_ATTESTED");
        assertThat(batch.toString()).doesNotContain(PRIVATE_TEXT,PRIVATE_META,
                "synthetic-owner@example.test");
        assertThat(service.acknowledge(owner(101L),handle.checkpointId(),a2))
                .isEqualTo(VerifiedOwnerDeliveryCheckpoint.Acknowledgement.OUT_OF_ORDER);
        assertThat(service.acknowledge(owner(101L),handle.checkpointId(),a1))
                .isEqualTo(VerifiedOwnerDeliveryCheckpoint.Acknowledgement.ADVANCED);
        assertThat(service.acknowledge(owner(101L),handle.checkpointId(),a1))
                .isEqualTo(VerifiedOwnerDeliveryCheckpoint.Acknowledgement.DUPLICATE);
        assertThatThrownBy(()->service.acknowledge(owner(101L),handle.checkpointId(),b1))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("TASK_NOT_AVAILABLE");

        // A new application object reads the same persisted DB checkpoint.
        var afterRestart=checkpointService();
        var pending=afterRestart.resume(owner(101L),handle.checkpointId(),2);
        assertThat(pending.lastAcknowledged()).isEqualTo(1L);
        assertThat(pending.events()).extracting(VerifiedOwnerDeliveryCheckpoint.SafeUpdate::eventId)
                .containsExactly(a2,a3);
        assertThat(afterRestart.acknowledge(owner(101L),handle.checkpointId(),a2))
                .isEqualTo(VerifiedOwnerDeliveryCheckpoint.Acknowledgement.ADVANCED);
        assertThat(afterRestart.acknowledge(owner(101L),handle.checkpointId(),a3))
                .isEqualTo(VerifiedOwnerDeliveryCheckpoint.Acknowledgement.ADVANCED);
        assertThat(afterRestart.resume(owner(101L),handle.checkpointId(),10).events())
                .isEmpty();
        assertThat(checkpoints.findById(handle.checkpointId()).orElseThrow().getLastSequence())
                .isEqualTo(3L);
    }

    @Test
    void aDifferentSignedUserCannotOpenResumeOrAcknowledgeAnotherOwnersTask() {
        TaskEntity a=task(101L,null),b=task(202L,null);
        UUID aEvent=record(a.getId(),Instant.now(),"qa-engineer");
        var service=checkpointService();
        var handle=service.open(owner(101L),a.getId());
        var other=service.open(owner(202L),b.getId());
        assertThatThrownBy(()->service.open(owner(202L),a.getId()))
                .isInstanceOf(NoSuchElementException.class).hasMessage("TASK_NOT_AVAILABLE");
        assertThatThrownBy(()->service.resume(owner(202L),handle.checkpointId(),10))
                .isInstanceOf(NoSuchElementException.class).hasMessage("TASK_NOT_AVAILABLE");
        assertThatThrownBy(()->service.acknowledge(owner(202L),handle.checkpointId(),aEvent))
                .isInstanceOf(NoSuchElementException.class).hasMessage("TASK_NOT_AVAILABLE");
        assertThatThrownBy(()->service.resume(owner(101L),other.checkpointId(),10))
                .isInstanceOf(NoSuchElementException.class).hasMessage("TASK_NOT_AVAILABLE");
        assertThat(checkpoints.findById(handle.checkpointId()).orElseThrow().getLastSequence())
                .isZero();
    }

    @Test
    void legacyOrProjectTasksAndUnsignedOrWrongAudienceTokensFailClosed() {
        TaskEntity legacy=task(null,null);
        TaskEntity project=task(101L,UUID.randomUUID());
        TaskEntity global=task(101L,null);
        var service=checkpointService();
        for (UUID id: List.of(legacy.getId(),project.getId(),UUID.randomUUID())) {
            assertThatThrownBy(()->service.open(owner(101L),id))
                    .isInstanceOf(NoSuchElementException.class).hasMessage("TASK_NOT_AVAILABLE");
        }
        for (String invalid:List.of(
                jwt(101L,"wrong-client",List.of("orchestrator:read")),
                jwt(101L,RuntimeAccessTokenVerifier.DEFAULT_AUDIENCE,List.of("users:read")),
                "malformed.jwt")) {
            assertThatThrownBy(()->service.open(invalid,global.getId()))
                    .isInstanceOf(RuntimeAccessTokenVerifier.RejectedToken.class)
                    .hasMessage("ACCESS_TOKEN_REJECTED");
        }
        var handle=service.open(owner(101L),global.getId());
        for(int limit:new int[]{0,-1,101,Integer.MAX_VALUE}) {
            assertThatThrownBy(()->service.resume(owner(101L),handle.checkpointId(),limit))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Test
    void duplicateTimestampAndInvalidAgentValuesAreSafelyProjectedInCommitOrder() {
        TaskEntity a=task(101L,null);
        Instant t=Instant.parse("2026-10-14T00:00:00Z");
        UUID first=record(a.getId(),t,"<script>secret()</script>");
        UUID second=record(a.getId(),t,"qa-engineer");
        var service=checkpointService();
        var handle=service.open(owner(101L),a.getId());
        var page=service.resume(owner(101L),handle.checkpointId(),10);
        assertThat(page.events()).extracting(VerifiedOwnerDeliveryCheckpoint.SafeUpdate::eventId)
                .containsExactly(first,second);
        assertThat(page.events().get(0).agentId()).isNull();
        assertThat(page.events().get(1).agentId()).isEqualTo("qa-engineer");
        assertThat(page.toString()).doesNotContain("script",PRIVATE_TEXT,PRIVATE_META);
    }

    @Test
    void concurrentAcknowledgementsSerializeWithoutLosingForwardProgress() throws Exception {
        TaskEntity a=task(101L,null);
        UUID first=record(a.getId(),Instant.now(),"qa-engineer");
        var service=checkpointService();
        var handle=service.open(owner(101L),a.getId());
        var ready=new CountDownLatch(2);
        var launch=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var tasks=new ArrayList<java.util.concurrent.Future<VerifiedOwnerDeliveryCheckpoint.Acknowledgement>>();
            for(int i=0;i<2;i++) {
                tasks.add(pool.submit(()->{
                    ready.countDown();
                    if(!launch.await(8,TimeUnit.SECONDS))throw new IllegalStateException("launch timeout");
                    return checkpointService().acknowledge(owner(101L),handle.checkpointId(),first);
                }));
            }
            assertThat(ready.await(8,TimeUnit.SECONDS)).isTrue();
            launch.countDown();
            var results=List.of(tasks.get(0).get(12,TimeUnit.SECONDS),
                    tasks.get(1).get(12,TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder(
                    VerifiedOwnerDeliveryCheckpoint.Acknowledgement.ADVANCED,
                    VerifiedOwnerDeliveryCheckpoint.Acknowledgement.DUPLICATE);
            assertThat(checkpoints.findById(handle.checkpointId()).orElseThrow().getLastSequence())
                    .isEqualTo(1L);
        }finally{launch.countDown();pool.shutdownNow();}
    }
}
