package io.aetheris.orchestrator.trading;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * Stage 46.31 TEST-SOURCE-ONLY durable ledger using disposable H2.
 *
 * Not a production Spring bean, repository, HTTP API, exchange bridge, owner
 * authenticator, financial account sync or authorization service. A public
 * CI-only HMAC fixture key never constitutes a real trusted producer.
 */
final class Stage4631OfflineReplayLedgerLab {
    private static final String ACCOUNT = "paper-default";
    private static final String ZERO_HASH = "0".repeat(64);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final byte[] fixtureKey;

    record Cursor(long sequence, String digest) {}
    record Verdict(boolean accepted, long sequence, String reason,
                   boolean stateApplied, boolean orderAuthorized,
                   boolean productionAuthorized, String decision) {}

    Stage4631OfflineReplayLedgerLab(DataSource dataSource, byte[] key) {
        if (dataSource == null || key == null || key.length < 32)
            throw new IllegalArgumentException("TEST_DB_AND_FIXTURE_KEY_REQUIRED");
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        fixtureKey = key.clone();
    }

    /** Invoke once at fixture initialization, NEVER at verifier restart. */
    static void seedDisposableSchema(DataSource ds) {
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table stage4631_inert_replay_cursor (" +
                "account_id varchar(40) primary key, " +
                "sequence_number bigint not null, " +
                "last_digest varchar(64) not null)");
        jdbc.update("insert into stage4631_inert_replay_cursor " +
                "(account_id, sequence_number, last_digest) values (?, ?, ?)",
                ACCOUNT, 0L, ZERO_HASH);
    }

    Cursor durableCursor() {
        List<Cursor> rows = jdbc.query(
                "select sequence_number,last_digest from stage4631_inert_replay_cursor where account_id=?",
                (rs,n) -> new Cursor(rs.getLong(1), rs.getString(2)), ACCOUNT);
        return rows.size() == 1 ? rows.get(0) : new Cursor(-1, "");
    }

    Verdict inspect(JsonNode state, String tag, long nowMs) {
        return verify(state, tag, nowMs, false);
    }

    /** Test-only fault injection to prove SQL update rolls back on crash. */
    Verdict inspectWithSimulatedCommitFailure(JsonNode state, String tag, long nowMs) {
        return verify(state, tag, nowMs, true);
    }

    private Verdict verify(JsonNode state, String tag, long nowMs, boolean injectCrash) {
        Cursor before = durableCursor();
        try {
            byte[] canonical = OfflinePaperStateReconciliation.canonical(state);
            if (tag == null || !tag.matches("[0-9a-f]{64}") ||
                    nowMs < 0 || nowMs > 1_000_000_000_000_000L ||
                    nowMs < state.path("issued_ms").longValue() ||
                    nowMs > state.path("expires_ms").longValue()) {
                return deny(before, "INVALID_OR_STALE_SYNTHETIC_EVIDENCE");
            }
            byte[] supplied = HexFormat.of().parseHex(tag);
            byte[] expected = HexFormat.of().parseHex(
                    OfflinePaperStateReconciliation.mac(state, fixtureKey));
            if (!MessageDigest.isEqual(supplied, expected)) {
                return deny(before, "INVALID_SYNTHETIC_AUTHENTICATION");
            }
            final long next = state.path("sequence").longValue();
            final String parent = state.path("previous_digest").textValue();
            final String hash = OfflinePaperStateReconciliation.digest(state);
            Boolean committed = transactions.execute(tx -> {
                List<Cursor> rows = jdbc.query(
                        "select sequence_number,last_digest from stage4631_inert_replay_cursor where account_id=?",
                        (rs,n)->new Cursor(rs.getLong(1),rs.getString(2)), ACCOUNT);
                if (rows.size() != 1) return false;
                Cursor old = rows.get(0);
                if (next != old.sequence() + 1 || !parent.equals(old.digest())) return false;
                int updated = jdbc.update(
                        "update stage4631_inert_replay_cursor set sequence_number=?,last_digest=? " +
                        "where account_id=? and sequence_number=? and last_digest=?",
                        next, hash, ACCOUNT, old.sequence(), old.digest());
                if (updated != 1) return false;
                if (injectCrash) throw new IllegalStateException("DISPOSABLE_TEST_ROLLBACK");
                return true;
            });
            Cursor after = durableCursor();
            if (Boolean.TRUE.equals(committed)) return
                    new Verdict(true, after.sequence(), "DURABLE_SYNTHETIC_SEQUENCE_ONLY",
                            false, false, false, "NO_GO");
            return deny(after, "REPLAY_GAP_CHAIN_OR_TRANSACTION_CONFLICT");
        } catch (RuntimeException failure) {
            return deny(durableCursor(), "FAILED_CLOSED_OR_ROLLED_BACK");
        }
    }

    private static Verdict deny(Cursor state, String reason) {
        return new Verdict(false, state.sequence(), reason,
                false, false, false, "NO_GO");
    }
}
