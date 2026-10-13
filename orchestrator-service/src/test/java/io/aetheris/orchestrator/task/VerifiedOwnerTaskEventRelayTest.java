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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class VerifiedOwnerTaskEventRelayTest {
    private static final String TEST_SECRET = "0123456789abcdef0123456789abcdef";
    private static final String SECRET_MESSAGE = "SECRET_TASK_COMMAND_OR_CLIENT_DATA";
    private static final String SECRET_METADATA = "SECRET_AUTHORIZATION_TOKEN";

    @Autowired TaskRepository tasks;
    @Autowired TaskEventJournalService journal;

    private static String token(long uid, String audience, List<String> scopes) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(RuntimeAccessTokenVerifier.DEFAULT_ISSUER)
                .audience().add(audience).and()
                .subject("synthetic@example.test")
                .claim("uid", uid)
                .claim("role", "API_CONSUMER")
                .claim("scopes", scopes)
                .issuedAt(Date.from(now.minusSeconds(30)))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
    }

    private static String readJwt(long id) {
        return token(id, RuntimeAccessTokenVerifier.DEFAULT_AUDIENCE,
                List.of("orchestrator:read"));
    }

    private TaskEntity task(Long owner, UUID project) {
        TaskEntity entity = owner == null
                ? new TaskEntity(UUID.randomUUID(), "Legacy", "SECRET_UNOWNED",
                        OperationMode.PRIVATE)
                : new TaskEntity(UUID.randomUUID(), "Owner Mission", "PRIVATE_WORKSTATION_ACTION",
                        OperationMode.PRIVATE, owner, project);
        return tasks.save(entity);
    }

    private static final Instant SOURCE_TIME = Instant.parse("2026-10-13T12:00:00Z");
    private void record(UUID task, long offset, TaskState state, String agent) {
        journal.append(new TaskEvent(task, SOURCE_TIME.plusSeconds(offset),
                state, agent, SECRET_MESSAGE, Map.of("accessToken", SECRET_METADATA)));
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;
        MutableClock(Instant initial) { current = new AtomicReference<>(initial); }
        void advance(Duration delta) { current.updateAndGet(value -> value.plus(delta)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current.get(); }
    }

    private VerifiedOwnerTaskEventRelay relay(Clock clock) {
        return new VerifiedOwnerTaskEventRelay(tasks, journal,
                new RuntimeAccessTokenVerifier(TEST_SECRET), clock);
    }

    @Test
    void twoSignedOwnersReceiveOnlyTheirOwnNewJournaledEvents() {
        TaskEntity a=task(101L,null), b=task(202L,null);
        MutableClock clock=new MutableClock(Instant.now());
        var relay=relay(clock);
        var ownerA=new ArrayList<VerifiedOwnerTaskEventRelay.RecordedTaskProgress>();
        var ownerB=new ArrayList<VerifiedOwnerTaskEventRelay.RecordedTaskProgress>();
        var subscriptionA=relay.subscribe(readJwt(101L),a.getId(),ownerA::add);
        var subscriptionB=relay.subscribe(readJwt(202L),b.getId(),ownerB::add);

        record(a.getId(),1,TaskState.RUNNING,"backend-engineer");
        record(b.getId(),1,TaskState.PLANNING,"project-manager");
        relay.onJournalChanged(a.getId());
        assertThat(ownerA).hasSize(1);
        assertThat(ownerB).isEmpty();
        relay.onJournalChanged(b.getId());
        assertThat(ownerB).hasSize(1);
        assertThat(ownerA.get(0).taskId()).isEqualTo(a.getId());
        assertThat(ownerB.get(0).taskId()).isEqualTo(b.getId());
        assertThat(ownerA.toString()+ownerB.toString())
                .doesNotContain(SECRET_MESSAGE,SECRET_METADATA,"PRIVATE_WORKSTATION_ACTION",
                        "synthetic@example.test");
        assertThat(ownerA.get(0).provenance())
                .isEqualTo("PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME");

        // Replaying the journal without new rows never replays updates.
        relay.onJournalChanged(a.getId());
        relay.onJournalChanged(b.getId());
        assertThat(ownerA).hasSize(1);
        assertThat(ownerB).hasSize(1);
        subscriptionA.close();
        subscriptionB.close();
        assertThat(relay.activeSubscriptions()).isZero();
    }

    @Test
    void anotherOwnerAndLegacyOrProjectTasksCannotBeSubscribedTo() {
        TaskEntity a=task(101L,null);
        TaskEntity legacy=task(null,null);
        TaskEntity project=task(101L,UUID.randomUUID());
        var relay=relay(new MutableClock(Instant.now()));
        for (UUID denied : List.of(a.getId(),legacy.getId(),project.getId(),UUID.randomUUID())) {
            assertThatThrownBy(() -> relay.subscribe(readJwt(202L),denied,e -> {}))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("TASK_NOT_AVAILABLE");
        }
        assertThatThrownBy(() -> relay.subscribe(readJwt(101L),project.getId(),e -> {}))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("TASK_NOT_AVAILABLE");
        assertThat(relay.activeSubscriptions()).isZero();
    }

    @Test
    void wrongAudienceWriteOnlyTokenAndMalformedTokenAreRejected() {
        TaskEntity a=task(101L,null);
        var relay=relay(new MutableClock(Instant.now()));
        for (String jwt : List.of(
                token(101L,"some-other-app",List.of("orchestrator:read")),
                token(101L,RuntimeAccessTokenVerifier.DEFAULT_AUDIENCE,
                        List.of("orchestrator:write")),
                "unsigned.fake.jwt")) {
            assertThatThrownBy(() -> relay.subscribe(jwt,a.getId(),e -> {}))
                    .isInstanceOf(RuntimeAccessTokenVerifier.RejectedToken.class)
                    .hasMessage("ACCESS_TOKEN_REJECTED");
        }
    }

    @Test
    void leaseExpiresWithinOneMinuteAndRevocationImmediatelyClosesSubscribers() {
        TaskEntity a=task(101L,null);
        MutableClock clock=new MutableClock(Instant.now());
        var relay=relay(clock);
        var observed=new ArrayList<VerifiedOwnerTaskEventRelay.RecordedTaskProgress>();
        var lease=relay.subscribe(readJwt(101L),a.getId(),observed::add);
        clock.advance(Duration.ofSeconds(61));
        record(a.getId(),1,TaskState.RUNNING,"qa-engineer");
        relay.onJournalChanged(a.getId());
        assertThat(observed).isEmpty();
        assertThat(lease.isClosed()).isTrue();
        assertThat(relay.activeSubscriptions()).isZero();

        var newLease=relay.subscribe(readJwt(101L),a.getId(),observed::add);
        relay.revokeOwner(101L);
        assertThat(newLease.isClosed()).isTrue();
        record(a.getId(),2,TaskState.VERIFYING,"qa-engineer");
        relay.onJournalChanged(a.getId());
        assertThat(observed).isEmpty();
    }

    @Test
    void callbacksCannotBeUsedToExtractRawPayloadAndFailingSinkIsRemoved() {
        TaskEntity a=task(101L,null);
        var relay=relay(new MutableClock(Instant.now()));
        var events=new ArrayList<VerifiedOwnerTaskEventRelay.RecordedTaskProgress>();
        var failing=relay.subscribe(readJwt(101L),a.getId(),e -> {
            events.add(e);
            throw new IllegalStateException("DO_NOT_LOG_A_TOKEN");
        });
        record(a.getId(),1,TaskState.RUNNING,"<script>secret</script>");
        relay.onJournalChanged(a.getId());
        assertThat(failing.isClosed()).isTrue();
        assertThat(relay.activeSubscriptions()).isZero();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).agentId()).isNull();
        assertThat(events.toString()).doesNotContain(SECRET_MESSAGE,SECRET_METADATA,
                "script", "DO_NOT_LOG_A_TOKEN");
    }

    @Test
    void newSubscribersDoNotAutomaticallyReplayOldJournalHistory() {
        TaskEntity a=task(101L,null);
        record(a.getId(),1,TaskState.QUEUED,"planner");
        var relay=relay(new MutableClock(Instant.now()));
        var observed=new ArrayList<VerifiedOwnerTaskEventRelay.RecordedTaskProgress>();
        try (var lease=relay.subscribe(readJwt(101L),a.getId(),observed::add)) {
            relay.onJournalChanged(a.getId());
            assertThat(observed).isEmpty();
            record(a.getId(),2,TaskState.PLANNING,"planner");
            relay.onJournalChanged(a.getId());
            assertThat(observed).hasSize(1);
            assertThat(observed.get(0).state()).isEqualTo(TaskState.PLANNING);
        }
    }

    @Test
    void subscriberCapacityIsBoundedAndCloseReleasesCapacity() {
        TaskEntity a=task(101L,null);
        var relay=relay(new MutableClock(Instant.now()));
        var leases=new ArrayList<VerifiedOwnerTaskEventRelay.Lease>();
        for (int i=0;i<VerifiedOwnerTaskEventRelay.MAX_SUBSCRIPTIONS;i++) {
            leases.add(relay.subscribe(readJwt(101L),a.getId(),e -> {}));
        }
        assertThat(relay.activeSubscriptions()).isEqualTo(VerifiedOwnerTaskEventRelay.MAX_SUBSCRIPTIONS);
        assertThatThrownBy(() -> relay.subscribe(readJwt(101L),a.getId(),e -> {}))
                .isInstanceOf(NoSuchElementException.class);
        leases.forEach(VerifiedOwnerTaskEventRelay.Lease::close);
        assertThat(relay.activeSubscriptions()).isZero();
        try (var newLease=relay.subscribe(readJwt(101L),a.getId(),e -> {})) {
            assertThat(relay.activeSubscriptions()).isEqualTo(1);
        }
        assertThat(relay.activeSubscriptions()).isZero();
    }

    @Test
    void lateEventsCannotBeDeliveredAfterTaskDeletion() {
        TaskEntity a=task(101L,null);
        var relay=relay(new MutableClock(Instant.now()));
        var observed=new ArrayList<VerifiedOwnerTaskEventRelay.RecordedTaskProgress>();
        var lease=relay.subscribe(readJwt(101L),a.getId(),observed::add);
        tasks.delete(a);
        tasks.flush();
        relay.onJournalChanged(a.getId());
        assertThat(lease.isClosed()).isTrue();
        assertThat(observed).isEmpty();
    }
}
