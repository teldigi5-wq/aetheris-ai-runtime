package io.aetheris.orchestrator.trading;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Stage46.31 OFFLINE-only, synthetic account, test-key replay proof.
 *
 * A verified message advances ONLY a tiny DB replay anchor; it NEVER applies
 * cash/equity/positions, places a paper/broker order, or grants authorization.
 * The caller must provide the test-only key; there is NO configured identity
 * provider or trusted producer in this milestone. No web/controller method.
 *
 * This ledger serializes existing rows with a pessimistic lock. Competing
 * first inserts rely on the database unique PK: one fails closed on conflict.
 * Failure returns NO_GO and does not advance sequence or grant trading.
 */
@Service
public final class OfflinePaperReplayLedgerService {
    private static final String ACCOUNT = "paper-default";
    private static final String GENESIS = "0".repeat(64);
    private static final long MAX_CLOCK = 1_000_000_000_000_000L;
    private final OfflinePaperReplayAnchorRepository anchors;
    private final TransactionTemplate tx;

    public record Decision(
            boolean accepted, String reason, long acceptedSequence,
            boolean durableAnchorWritten, boolean snapshotApplied,
            boolean pythonTestnetOrderAuthorized, boolean liveOrderAuthorized,
            boolean productionAuthorized, String decision) {}

    public OfflinePaperReplayLedgerService(OfflinePaperReplayAnchorRepository anchors,
                                           PlatformTransactionManager manager) {
        this.anchors = anchors;
        this.tx = new TransactionTemplate(manager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * The transaction includes sequence check AND DB write. Checked exceptions
     * and DB concurrency/lock failures return refusal; no retry silently grants
     * a duplicate sequence. Time and authentication are checked before any DB
     * operation. No source of secure keys or realtime clock is claimed.
     */
    public Decision inspectAndRecord(JsonNode state, String tag,
                                     long nowMs, byte[] syntheticKey) {
        try {
            if (syntheticKey == null || syntheticKey.length < 32 ||
                    nowMs < 0 || nowMs > MAX_CLOCK ||
                    tag == null || !tag.matches("[0-9a-f]{64}")) {
                return refused("INVALID_OFFLINE_AUTHENTICATION");
            }
            byte[] canonical = OfflinePaperStateReconciliation.canonical(state);
            long issued = state.path("issued_ms").longValue();
            long expires = state.path("expires_ms").longValue();
            if (nowMs < issued || nowMs > expires) {
                return refused("EXPIRED_OR_FUTURE_SYNTHETIC_SNAPSHOT");
            }
            byte[] expected = HexFormat.of().parseHex(
                    OfflinePaperStateReconciliation.mac(state, syntheticKey));
            if (!MessageDigest.isEqual(HexFormat.of().parseHex(tag), expected)) {
                return refused("INVALID_SYNTHETIC_HMAC");
            }
            String candidateDigest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical));
            long candidateSequence = state.path("sequence").longValue();
            String expectedPrior = state.path("previous_digest").textValue();

            Decision result = tx.execute(status -> {
                var current = anchors.locked(ACCOUNT);
                if (current.isEmpty()) {
                    if (candidateSequence != 1 || !GENESIS.equals(expectedPrior)) {
                        return refused("MISSING_GENESIS_OR_OUT_OF_ORDER_SNAPSHOT");
                    }
                    OfflinePaperReplayAnchorEntity newAnchor =
                            new OfflinePaperReplayAnchorEntity(
                                    ACCOUNT, candidateSequence, candidateDigest);
                    anchors.saveAndFlush(newAnchor);
                    return accepted(candidateSequence);
                }
                OfflinePaperReplayAnchorEntity anchor = current.get();
                if (anchor.invalid()) {
                    return refused("CORRUPT_PERSISTED_REPLAY_ANCHOR");
                }
                if (anchor.getLastAcceptedSequence() == Long.MAX_VALUE ||
                        candidateSequence != anchor.getLastAcceptedSequence() + 1 ||
                        !expectedPrior.equals(anchor.getLastAcceptedDigest())) {
                    return refused("DURABLE_REPLAY_OR_CHAIN_GAP");
                }
                anchor.advance(candidateSequence, expectedPrior, candidateDigest);
                anchors.saveAndFlush(anchor);
                return accepted(candidateSequence);
            });
            return result == null ? refused("NO_DURABLE_TRANSACTION_RESULT") : result;
        } catch (Exception e) {
            // DB constraint conflict, serialization failure, malformed
            // payload, missing key and invalid cryptography all fail CLOSED.
            // No exception details or sensitive values in result.
            return refused("OFFLINE_REPLAY_LEDGER_FAIL_CLOSED");
        }
    }

    private static Decision accepted(long seq) {
        return new Decision(true, "SIGNED_SYNTHETIC_ANCHOR_ONLY",
                seq, true, false, false, false, false, "NO_GO");
    }

    private static Decision refused(String reason) {
        return new Decision(false, reason, 0, false, false,
                false, false, false, "NO_GO");
    }
}
