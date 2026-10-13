package io.aetheris.orchestrator.task;

import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.security.RuntimeAccessTokenVerifier;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Database-backed crash/retry tests, with real Spring commits/rollbacks.
 * NOT @Transactional: the tests intentionally control transaction lifetimes.
 */
@SpringBootTest
class VerifiedOwnerTaskJournalOutboxTest {
    private static final String SYNTHETIC_SECRET = "0123456789abcdef0123456789abcdef";
    private static final String SENSITIVE_MESSAGE = "PRIVATE_MESSAGE_NOT_IN_OUTBOX";
    private static final String SENSITIVE_METADATA = "PRIVATE_TOKEN_NOT_IN_OUTBOX";
    private static final Instant BASE = Instant.parse("2026-10-13T16:00:00Z");

    @Autowired TaskRepository tasks;
    @Autowired TaskEventJournalService journal;
    @Autowired TaskJournalOutboxRepository outbox;
    @Autowired PlatformTransactionManager manager;

    // The JUnit Spring context is shared: isolate the intentionally durable
    // outbox across methods instead of relying on method discovery order.
    @BeforeEach
    void clearSyntheticOutbox() { outbox.deleteAll(); }


    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        MutableClock(Instant at) { instant = new AtomicReference<>(at); }
        void advance(Duration duration) { instant.updateAndGet(x -> x.plus(duration)); }
        @Override public Instant instant() { return instant.get(); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    private TransactionTemplate tx() { return new TransactionTemplate(manager); }

    private TaskEntity task(Long owner, UUID project) {
        return tx().execute(status -> tasks.save(owner == null
                ? new TaskEntity(UUID.randomUUID(), "Legacy", "UNOWNED_PRIVATE",
                        OperationMode.PRIVATE)
                : new TaskEntity(UUID.randomUUID(), "Signed owner task", "PRIVATE_COMMAND",
                        OperationMode.PRIVATE, owner, project)));
    }

    private TaskEvent event(UUID id, int offset) {
        return new TaskEvent(id, BASE.plusSeconds(offset), TaskState.RUNNING,
                "qa-engineer", SENSITIVE_MESSAGE, Map.of("token", SENSITIVE_METADATA));
    }

    private VerifiedOwnerTaskJournalOutbox service(MutableClock clock) {
        return new VerifiedOwnerTaskJournalOutbox(tasks, journal, outbox, manager, clock);
    }

    private UUID persist(VerifiedOwnerTaskJournalOutbox service, TaskEvent event) {
        return tx().execute(status -> service.appendInCurrentTransaction(event));
    }

    private static String signedUser(long id) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(RuntimeAccessTokenVerifier.DEFAULT_ISSUER)
                .audience().add(RuntimeAccessTokenVerifier.DEFAULT_AUDIENCE).and()
                .subject("synthetic@example.test")
                .claim("uid", id).claim("role", "API_CONSUMER")
                .claim("scopes", List.of("orchestrator:read"))
                .issuedAt(Date.from(now.minusSeconds(15)))
                .expiration(Date.from(now.plusSeconds(200)))
                .signWith(Keys.hmacShaKeyFor(
                        SYNTHETIC_SECRET.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void journalAndOutboxCommitAtomicallyWithStableEventIdAndNoPrivateOutboxPayload() {
        MutableClock clock = new MutableClock(Instant.now());
        var service = service(clock);
        TaskEntity a = task(101L,null);
        UUID journalId = persist(service,event(a.getId(),1));
        var row = outbox.findByJournalEventId(journalId).orElseThrow();
        assertThat(row.getTaskId()).isEqualTo(a.getId());
        assertThat(row.getState()).isEqualTo(TaskJournalOutboxEntity.State.PENDING);
        assertThat(journal.history(a.getId())).hasSize(1);
        assertThat(row.toString()).doesNotContain(SENSITIVE_MESSAGE,SENSITIVE_METADATA);
        assertThat(outbox.findByJournalEventId(journalId)).isPresent();
    }

    @Test
    void rollbackPersistsNeitherOutboxNorEvent() {
        MutableClock clock = new MutableClock(Instant.now());
        var service=service(clock);
        TaskEntity a=task(101L,null);
        AtomicReference<UUID> journalId=new AtomicReference<>();
        assertThatThrownBy(()->tx().executeWithoutResult(status -> {
            journalId.set(service.appendInCurrentTransaction(event(a.getId(),2)));
            throw new IllegalStateException("synthetic rollback after both writes");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(journal.history(a.getId())).isEmpty();
        assertThat(outbox.findByJournalEventId(journalId.get())).isEmpty();
    }

    @Test
    void noTransactionAndUnownedOrProjectBoundTasksCannotEnterOutbox() {
        MutableClock clock = new MutableClock(Instant.now());
        var service=service(clock);
        TaskEntity owned=task(101L,null), project=task(101L,UUID.randomUUID()),
                legacy=task(null,null);
        assertThatThrownBy(()->service.appendInCurrentTransaction(event(owned.getId(),3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TRUSTED_TRANSACTION_REQUIRED");
        for (var blocked:List.of(project,legacy)) {
            assertThatThrownBy(()->tx().executeWithoutResult(status ->
                    service.appendInCurrentTransaction(event(blocked.getId(),4))))
                    .hasMessage("TASK_NOT_AVAILABLE");
            assertThat(journal.history(blocked.getId())).isEmpty();
        }
    }

    @Test
    void persistedPendingRowCanBeClaimedAfterNewServiceInstanceAndFencedAfterCrash() {
        MutableClock clock=new MutableClock(Instant.now());
        var beforeRestart=service(clock);
        TaskEntity owned=task(101L,null);
        UUID journalId=persist(beforeRestart,event(owned.getId(),5));
        var crashedClaim=beforeRestart.claimReady();
        assertThat(crashedClaim).isNotNull();
        assertThat(crashedClaim.journalEventId()).isEqualTo(journalId);
        assertThat(beforeRestart.claimReady()).isNull();

        // Simulate process death: new worker object, same persisted DB.
        clock.advance(Duration.ofSeconds(31));
        var recovered=service(clock);
        var retryClaim=recovered.claimReady();
        assertThat(retryClaim).isNotNull();
        assertThat(retryClaim.outboxId()).isEqualTo(crashedClaim.outboxId());
        assertThat(retryClaim.leaseToken()).isNotEqualTo(crashedClaim.leaseToken());
        assertThat(recovered.acknowledgeInternalHandoff(crashedClaim)).isFalse();
        assertThat(recovered.acknowledgeInternalHandoff(retryClaim)).isTrue();
        assertThat(outbox.findById(retryClaim.outboxId()).orElseThrow().getState())
                .isEqualTo(TaskJournalOutboxEntity.State.HANDOFF_ATTEMPTED);
        assertThat(recovered.claimReady()).isNull();
    }

    @Test
    void callbackFailureRetriesAfterBackoffWithoutSavingExceptions() {
        MutableClock clock=new MutableClock(Instant.now());
        var service=service(clock);
        TaskEntity owned=task(101L,null);
        UUID journalId=persist(service,event(owned.getId(),6));
        assertThat(service.dispatchOnce(id -> {
            throw new IllegalStateException("PRIVATE_EXCEPTION_TOKEN");
        })).isEqualTo(VerifiedOwnerTaskJournalOutbox.DrainResult.RETRY_SCHEDULED);
        var row=outbox.findByJournalEventId(journalId).orElseThrow();
        assertThat(row.getState()).isEqualTo(TaskJournalOutboxEntity.State.PENDING);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.toString()).doesNotContain("PRIVATE_EXCEPTION_TOKEN");
        assertThat(service.dispatchOnce(id -> {}))
                .isEqualTo(VerifiedOwnerTaskJournalOutbox.DrainResult.NOTHING_READY);
        clock.advance(Duration.ofSeconds(3));
        var received=new ArrayList<UUID>();
        assertThat(service.dispatchOnce(received::add))
                .isEqualTo(VerifiedOwnerTaskJournalOutbox.DrainResult.HANDOFF_ATTEMPTED);
        assertThat(received).containsExactly(owned.getId());
        assertThat(outbox.findByJournalEventId(journalId).orElseThrow().getState())
                .isEqualTo(TaskJournalOutboxEntity.State.HANDOFF_ATTEMPTED);
    }

    @Test
    void fiveFailedAttemptsParkRecordInsteadOfInfiniteRetry() {
        MutableClock clock=new MutableClock(Instant.now());
        var service=service(clock);
        TaskEntity owned=task(101L,null);
        UUID journalId=persist(service,event(owned.getId(),7));
        for(int attempt=1;attempt<=VerifiedOwnerTaskJournalOutbox.MAX_ATTEMPTS;attempt++) {
            assertThat(service.dispatchOnce(id -> {
                throw new IllegalStateException("no delivery");
            })).isEqualTo(VerifiedOwnerTaskJournalOutbox.DrainResult.RETRY_SCHEDULED);
            clock.advance(Duration.ofSeconds(65));
        }
        assertThat(outbox.findByJournalEventId(journalId).orElseThrow().getState())
                .isEqualTo(TaskJournalOutboxEntity.State.PARKED);
        assertThat(service.claimReady()).isNull();
        assertThat(outbox.findByJournalEventId(journalId).orElseThrow().getAttempts())
                .isEqualTo(VerifiedOwnerTaskJournalOutbox.MAX_ATTEMPTS);
    }

    @Test
    void sanitizedOwnerRelayCanBeNotifiedFromDurableWorkWithoutCrossOwnerEvents() {
        MutableClock clock=new MutableClock(Instant.now());
        var service=service(clock);
        var relay=new VerifiedOwnerTaskEventRelay(tasks,journal,
                new RuntimeAccessTokenVerifier(SYNTHETIC_SECRET));
        TaskEntity a=task(101L,null),b=task(202L,null);
        var receivedA=new ArrayList<VerifiedOwnerTaskEventRelay.RecordedTaskProgress>();
        var receivedB=new ArrayList<VerifiedOwnerTaskEventRelay.RecordedTaskProgress>();
        try(var leaseA=relay.subscribe(signedUser(101L),a.getId(),receivedA::add);
            var leaseB=relay.subscribe(signedUser(202L),b.getId(),receivedB::add)) {
            persist(service,event(a.getId(),8));
            persist(service,event(b.getId(),9));
            assertThat(receivedA).isEmpty();
            assertThat(receivedB).isEmpty();
            assertThat(service.dispatchOnce(relay::onJournalChanged))
                    .isEqualTo(VerifiedOwnerTaskJournalOutbox.DrainResult.HANDOFF_ATTEMPTED);
            assertThat(service.dispatchOnce(relay::onJournalChanged))
                    .isEqualTo(VerifiedOwnerTaskJournalOutbox.DrainResult.HANDOFF_ATTEMPTED);
            assertThat(receivedA).hasSize(1);
            assertThat(receivedB).hasSize(1);
            assertThat(receivedA.get(0).taskId()).isEqualTo(a.getId());
            assertThat(receivedB.get(0).taskId()).isEqualTo(b.getId());
            assertThat(receivedA.toString()+receivedB.toString())
                    .doesNotContain(SENSITIVE_MESSAGE,SENSITIVE_METADATA);
        }
    }
}
