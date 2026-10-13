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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real H2/Spring transaction integration tests. No user account, HTTP,
 * Tauri command, production credential or live SSE transport is contacted.
 * These tests are deliberately NOT @Transactional: they exercise actual
 * commit and rollback through TransactionTemplate.
 */
@SpringBootTest
class VerifiedOwnerPostCommitJournalBridgeTest {
    private static final String SYNTHETIC_KEY = "0123456789abcdef0123456789abcdef";
    private static final Instant BASE = Instant.parse("2026-10-13T14:00:00Z");
    private static final String PRIVATE_MESSAGE = "PERSONAL_CONTENT_MUST_NOT_LEAK";
    private static final String PRIVATE_METADATA = "REFRESH_TOKEN_MUST_NOT_LEAK";

    @Autowired TaskRepository tasks;
    @Autowired TaskEventJournalService journal;
    @Autowired PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private TaskEntity persist(long ownerId) {
        return transaction().execute(status ->
                tasks.save(new TaskEntity(UUID.randomUUID(), "Synthetic owner task",
                        "PRIVATE_SYSTEM_COMMAND", OperationMode.PRIVATE, ownerId, null)));
    }

    private static String signedOwner(long uid) {
        Instant t = Instant.now();
        return Jwts.builder()
                .issuer(RuntimeAccessTokenVerifier.DEFAULT_ISSUER)
                .audience().add(RuntimeAccessTokenVerifier.DEFAULT_AUDIENCE).and()
                .subject("synthetic@example.test")
                .claim("uid", uid)
                .claim("role", "API_CONSUMER")
                .claim("scopes", List.of("orchestrator:read"))
                .issuedAt(Date.from(t.minusSeconds(10)))
                .expiration(Date.from(t.plusSeconds(240)))
                .signWith(Keys.hmacShaKeyFor(
                        SYNTHETIC_KEY.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    private VerifiedOwnerTaskEventRelay relay() {
        return new VerifiedOwnerTaskEventRelay(tasks, journal,
                new RuntimeAccessTokenVerifier(SYNTHETIC_KEY));
    }

    private void append(UUID taskId, int increment, TaskState state) {
        journal.append(new TaskEvent(taskId, BASE.plusSeconds(increment),
                state, "qa-engineer", PRIVATE_MESSAGE,
                Map.of("sensitiveToken", PRIVATE_METADATA)));
    }

    @Test
    void committedJournalChangeProducesNoEventUntilCommitAndThenDeliversSanitizedState() {
        TaskEntity a = persist(101L);
        var relay = relay();
        var bridge = new VerifiedOwnerPostCommitJournalBridge(transactionManager, relay);
        var output = new ArrayList<VerifiedOwnerTaskEventRelay.RecordedTaskProgress>();
        try (var lease = relay.subscribe(signedOwner(101L), a.getId(), output::add)) {
            transaction().executeWithoutResult(status -> {
                append(a.getId(), 1, TaskState.RUNNING);
                assertThat(bridge.stageAfterTrustedJournalAppend(a.getId()))
                        .isEqualTo(OwnerTaskPostCommitGate.StageResult.REGISTERED);
                assertThat(output).as("no uncommitted progress display").isEmpty();
            });
            assertThat(output).hasSize(1);
            assertThat(output.get(0).state()).isEqualTo(TaskState.RUNNING);
            assertThat(output.get(0).provenance())
                    .isEqualTo("PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME");
            assertThat(output.toString()).doesNotContain(
                    PRIVATE_MESSAGE, PRIVATE_METADATA, "PRIVATE_SYSTEM_COMMAND");
        }
    }

    @Test
    void aRollbackNeverNotifiesAndRolledBackEventsNeverReachOwner() {
        TaskEntity a = persist(101L);
        var relay = relay();
        var bridge = new VerifiedOwnerPostCommitJournalBridge(transactionManager, relay);
        var output = new ArrayList<VerifiedOwnerTaskEventRelay.RecordedTaskProgress>();
        try (var lease = relay.subscribe(signedOwner(101L), a.getId(), output::add)) {
            assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
                append(a.getId(), 2, TaskState.FAILED);
                assertThat(bridge.stageAfterTrustedJournalAppend(a.getId()))
                        .isEqualTo(OwnerTaskPostCommitGate.StageResult.REGISTERED);
                throw new IllegalStateException("synthetic deliberate rollback");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(output).isEmpty();
            assertThat(journal.history(a.getId())).isEmpty();

            transaction().executeWithoutResult(status -> {
                append(a.getId(), 3, TaskState.VERIFYING);
                bridge.stageAfterTrustedJournalAppend(a.getId());
            });
            assertThat(output).hasSize(1);
            assertThat(output.get(0).state()).isEqualTo(TaskState.VERIFYING);
        }
    }

    @Test
    void twoSignedOwnersReceiveOnlyTheirOwnCommittedUpdates() {
        TaskEntity a = persist(101L), b = persist(202L);
        var relay = relay();
        var bridge = new VerifiedOwnerPostCommitJournalBridge(transactionManager, relay);
        var observedA = new ArrayList<VerifiedOwnerTaskEventRelay.RecordedTaskProgress>();
        var observedB = new ArrayList<VerifiedOwnerTaskEventRelay.RecordedTaskProgress>();
        try (var leaseA = relay.subscribe(signedOwner(101L), a.getId(), observedA::add);
             var leaseB = relay.subscribe(signedOwner(202L), b.getId(), observedB::add)) {
            transaction().executeWithoutResult(status -> {
                append(a.getId(), 4, TaskState.RUNNING);
                append(a.getId(), 5, TaskState.VERIFYING);
                append(b.getId(), 4, TaskState.PLANNING);
                assertThat(bridge.stageAfterTrustedJournalAppend(a.getId()))
                        .isEqualTo(OwnerTaskPostCommitGate.StageResult.REGISTERED);
                assertThat(bridge.stageAfterTrustedJournalAppend(a.getId()))
                        .isEqualTo(OwnerTaskPostCommitGate.StageResult.COALESCED);
                assertThat(bridge.stageAfterTrustedJournalAppend(b.getId()))
                        .isEqualTo(OwnerTaskPostCommitGate.StageResult.REGISTERED);
                assertThat(observedA).isEmpty();
                assertThat(observedB).isEmpty();
            });
            assertThat(observedA).hasSize(2);
            assertThat(observedA).extracting(VerifiedOwnerTaskEventRelay.RecordedTaskProgress::taskId)
                    .containsOnly(a.getId());
            assertThat(observedB).hasSize(1);
            assertThat(observedB.get(0).taskId()).isEqualTo(b.getId());
        }
    }

    @Test
    void noTransactionNeverRegistersOrNotifiesAndNullTaskFailsClosed() {
        var seen = new ArrayList<UUID>();
        var gate = new OwnerTaskPostCommitGate(seen::add);
        TaskEntity a = persist(101L);
        assertThat(gate.stageAfterJournalAppend(a.getId()))
                .isEqualTo(OwnerTaskPostCommitGate.StageResult.DENIED_NO_TRANSACTION);
        assertThat(gate.stageAfterJournalAppend(null))
                .isEqualTo(OwnerTaskPostCommitGate.StageResult.DENIED_INVALID_TASK);
        assertThat(seen).isEmpty();
    }

    @Test
    void boundedTransactionScopedDedupIsResetAcrossTransactions() {
        var seen = new ArrayList<UUID>();
        var gate = new OwnerTaskPostCommitGate(seen::add);
        UUID first = UUID.randomUUID();
        transaction().executeWithoutResult(status -> {
            assertThat(gate.stageAfterJournalAppend(first))
                    .isEqualTo(OwnerTaskPostCommitGate.StageResult.REGISTERED);
            assertThat(gate.stageAfterJournalAppend(first))
                    .isEqualTo(OwnerTaskPostCommitGate.StageResult.COALESCED);
            for (int i = 1; i < OwnerTaskPostCommitGate.MAX_TASK_IDS_PER_TRANSACTION; i++) {
                assertThat(gate.stageAfterJournalAppend(UUID.randomUUID()))
                        .isEqualTo(OwnerTaskPostCommitGate.StageResult.REGISTERED);
            }
            assertThat(gate.stageAfterJournalAppend(UUID.randomUUID()))
                    .isEqualTo(OwnerTaskPostCommitGate.StageResult.DENIED_CAPACITY);
            assertThat(seen).isEmpty();
        });
        assertThat(seen).hasSize(OwnerTaskPostCommitGate.MAX_TASK_IDS_PER_TRANSACTION);
        transaction().executeWithoutResult(status -> {
            assertThat(gate.stageAfterJournalAppend(first))
                    .isEqualTo(OwnerTaskPostCommitGate.StageResult.REGISTERED);
        });
        assertThat(seen).hasSize(OwnerTaskPostCommitGate.MAX_TASK_IDS_PER_TRANSACTION + 1);
    }

    @Test
    void privateCallbackFailureCannotMakeDatabaseCommitAppearRolledBack() {
        var gate = new OwnerTaskPostCommitGate(taskId -> {
            throw new IllegalStateException("PRIVATE_STACK_TRACE_MUST_NOT_LEAK");
        });
        TaskEntity a = persist(101L);
        transaction().executeWithoutResult(status -> {
            append(a.getId(), 20, TaskState.QUEUED);
            assertThat(gate.stageAfterJournalAppend(a.getId()))
                    .isEqualTo(OwnerTaskPostCommitGate.StageResult.REGISTERED);
        });
        assertThat(journal.history(a.getId())).hasSize(1);
    }
}
