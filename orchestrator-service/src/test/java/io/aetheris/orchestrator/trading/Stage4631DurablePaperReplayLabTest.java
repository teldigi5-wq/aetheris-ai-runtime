package io.aetheris.orchestrator.trading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class Stage4631DurablePaperReplayLabTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private DriverManagerDataSource ds;
    private Stage4631OfflineReplayLedgerLab ledger;
    private JsonNode fixture;
    private byte[] key;

    @BeforeEach
    void initializeDisposableDatabase() throws Exception {
        ds = new DriverManagerDataSource(
                "jdbc:h2:mem:stage4631_" + UUID.randomUUID().toString().replace("-", "") +
                        ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000", "sa", "");
        ds.setDriverClassName("org.h2.Driver");
        Stage4631OfflineReplayLedgerLab.seedDisposableSchema(ds);
        ledger = new Stage4631OfflineReplayLedgerLab(ds, syntheticKey());
        fixture = fixture();
        key = syntheticKey();
    }

    private JsonNode fixture() throws Exception {
        Path path = Path.of("..", "risk-contracts",
                "stage46-offline-paper-state-chain-fixtures.json");
        if (!Files.isRegularFile(path))
            path = Path.of("risk-contracts", "stage46-offline-paper-state-chain-fixtures.json");
        return mapper.readTree(Files.readString(path));
    }

    private byte[] syntheticKey() throws Exception {
        if (fixture != null) return
                fixture.path("test_key_utf8").asText().getBytes(StandardCharsets.US_ASCII);
        return fixture().path("test_key_utf8").asText().getBytes(StandardCharsets.US_ASCII);
    }

    private JsonNode row(int idx) { return fixture.path("snapshots").get(idx); }

    private Stage4631OfflineReplayLedgerLab.Verdict inspect(int idx) {
        JsonNode r = row(idx);
        return ledger.inspect(r.path("state"), r.path("tag").asText(),
                r.path("verify_at_ms").asLong());
    }

    private void assertNoRuntimeAuthority(Stage4631OfflineReplayLedgerLab.Verdict v) {
        assertThat(v.decision()).isEqualTo("NO_GO");
        assertThat(v.stateApplied()).isFalse();
        assertThat(v.orderAuthorized()).isFalse();
        assertThat(v.productionAuthorized()).isFalse();
    }

    @Test
    void exactPythonGoldenReferenceIsCommittedToDisposableH2() {
        var first = inspect(0);
        assertThat(first.accepted()).isTrue();
        assertThat(first.sequence()).isEqualTo(1);
        assertThat(ledger.durableCursor().digest()).isEqualTo(row(0).path("sha256").asText());
        assertNoRuntimeAuthority(first);
    }

    @Test
    void secondIndependentInstanceRetainsPreviousSequenceAndDigest() {
        assertThat(inspect(0).accepted()).isTrue();
        Stage4631OfflineReplayLedgerLab restarted =
                new Stage4631OfflineReplayLedgerLab(ds, key);
        assertThat(restarted.durableCursor().sequence()).isEqualTo(1);
        assertThat(restarted.inspect(row(0).path("state"), row(0).path("tag").asText(),
                row(0).path("verify_at_ms").asLong()).accepted()).isFalse();
        var second = restarted.inspect(row(1).path("state"), row(1).path("tag").asText(),
                row(1).path("verify_at_ms").asLong());
        assertThat(second.accepted()).isTrue();
        assertThat(second.sequence()).isEqualTo(2);
        assertThat(restarted.durableCursor().digest()).isEqualTo(row(1).path("sha256").asText());
        assertNoRuntimeAuthority(second);
    }

    @Test
    void nextSequenceCannotSkipCommittedPredecessor() {
        var denied=inspect(1);
        assertThat(denied.accepted()).isFalse();
        assertThat(ledger.durableCursor().sequence()).isZero();
        assertNoRuntimeAuthority(denied);
    }

    @Test
    void changedBalanceWithOldTagDoesNotAdvanceDurableCursor() {
        ObjectNode altered = row(0).path("state").deepCopy();
        altered.put("cash_minor", 500000);
        var result = ledger.inspect(altered, row(0).path("tag").asText(),
                row(0).path("verify_at_ms").asLong());
        assertThat(result.accepted()).isFalse();
        assertThat(ledger.durableCursor().sequence()).isZero();
    }

    @Test
    void malformedProducerAccountOrAuthorityIsRejected() {
        ObjectNode altered = row(0).path("state").deepCopy();
        altered.put("account_id", "not-the-fixed-lab-account");
        assertThat(ledger.inspect(altered, row(0).path("tag").asText(),
                row(0).path("verify_at_ms").asLong()).accepted()).isFalse();
        altered = row(0).path("state").deepCopy();
        altered.put("exchange_order_authorized", true);
        assertThat(ledger.inspect(altered, row(0).path("tag").asText(),
                row(0).path("verify_at_ms").asLong()).accepted()).isFalse();
    }

    @Test
    void rejectedExpiredSnapshotDoesNotAdvanceCursor() {
        var r=row(0);
        assertThat(ledger.inspect(r.path("state"),r.path("tag").asText(),
                r.path("state").path("expires_ms").asLong()+1).accepted()).isFalse();
        assertThat(ledger.durableCursor().sequence()).isZero();
    }

    @Test
    void simulatedFailureAfterSqlUpdateRollsBackAtomically() {
        var first=row(0);
        var failed=ledger.inspectWithSimulatedCommitFailure(first.path("state"),
                first.path("tag").asText(),first.path("verify_at_ms").asLong());
        assertThat(failed.accepted()).isFalse();
        assertThat(ledger.durableCursor().sequence()).isZero();
        assertThat(ledger.durableCursor().digest()).isEqualTo("0".repeat(64));
        assertThat(inspect(0).accepted()).isTrue();
    }

    @Test
    void twoConcurrentConsumersCannotAdvanceSameSequenceTwice() throws Exception {
        var first=row(0);
        var latch=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Stage4631OfflineReplayLedgerLab.Verdict> task=()->{
                latch.await();
                Stage4631OfflineReplayLedgerLab instance=
                        new Stage4631OfflineReplayLedgerLab(ds,key);
                return instance.inspect(first.path("state"),first.path("tag").asText(),
                        first.path("verify_at_ms").asLong());
            };
            var a=pool.submit(task);
            var b=pool.submit(task);
            latch.countDown();
            var firstResult=a.get(15,TimeUnit.SECONDS);
            var secondResult=b.get(15,TimeUnit.SECONDS);
            assertThat((firstResult.accepted()?1:0)+(secondResult.accepted()?1:0))
                    .isEqualTo(1);
            assertNoRuntimeAuthority(firstResult);
            assertNoRuntimeAuthority(secondResult);
            assertThat(ledger.durableCursor().sequence()).isEqualTo(1);
            assertThat(ledger.durableCursor().digest()).isEqualTo(first.path("sha256").asText());
        }
    }

    @Test
    void failedDigestChainAfterFirstCommitCannotAdvance() {
        assertThat(inspect(0).accepted()).isTrue();
        ObjectNode changed=row(1).path("state").deepCopy();
        changed.put("previous_digest","0".repeat(64));
        String tag=OfflinePaperStateReconciliation.mac(changed,key);
        var result=ledger.inspect(changed,tag,row(1).path("verify_at_ms").asLong());
        assertThat(result.accepted()).isFalse();
        assertThat(ledger.durableCursor().sequence()).isEqualTo(1);
    }

    @Test
    void removedCursorFailClosedInsteadOfReinitializingFromZero() {
        org.springframework.jdbc.core.JdbcTemplate sql=new org.springframework.jdbc.core.JdbcTemplate(ds);
        sql.update("delete from stage4631_inert_replay_cursor");
        var result=inspect(0);
        assertThat(result.accepted()).isFalse();
        assertThat(ledger.durableCursor().sequence()).isEqualTo(-1);
    }
}
