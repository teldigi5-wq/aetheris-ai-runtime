package io.aetheris.orchestrator.task;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.UUID;

/**
 * Opt-in, unregistered adapter from committed journal task IDs to the
 * already-isolated VerifiedOwnerTaskEventRelay.
 *
 * This code is NOT a Spring component, HTTP endpoint, SSE subscriber, task
 * publisher, or native-client integration. The production global SSE route
 * and owner-sensitive HTTP task routes remain quarantined.
 *
 * Correctness: call stageAfterTrustedJournalAppend only within the exact
 * transaction that appends the journal record. The callback runs only after
 * successful commit and uses REQUIRES_NEW for the subsequent DB query; it
 * never passes source event messages, metadata, credentials or JWTs.
 */
public final class VerifiedOwnerPostCommitJournalBridge {
    private final OwnerTaskPostCommitGate gate;

    public VerifiedOwnerPostCommitJournalBridge(PlatformTransactionManager transactions,
                                                VerifiedOwnerTaskEventRelay relay) {
        Objects.requireNonNull(transactions);
        Objects.requireNonNull(relay);
        TransactionTemplate readCommitted = new TransactionTemplate(transactions);
        readCommitted.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        readCommitted.setReadOnly(true);
        this.gate = new OwnerTaskPostCommitGate(taskId ->
                readCommitted.executeWithoutResult(status -> relay.onJournalChanged(taskId)));
    }

    public OwnerTaskPostCommitGate.StageResult stageAfterTrustedJournalAppend(UUID taskId) {
        return gate.stageAfterJournalAppend(taskId);
    }
}
