package io.aetheris.orchestrator.trading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Real H2/JPA integration with independent committed transactions and a
 * fresh verifier instance; no orders, real keys, broker or owner-PC access.
 */
@SpringBootTest
class OfflinePaperReplayLedgerIntegrationTest {
    @Autowired OfflinePaperReplayAnchorRepository repository;
    @Autowired OfflinePaperReplayLedgerService service;
    @Autowired PlatformTransactionManager manager;

    private final ObjectMapper json = new ObjectMapper();
    private JsonNode fixture;
    private byte[] publicLabKey;

    @BeforeEach void setup() throws Exception {
        repository.deleteById("paper-default");
        Path path = Path.of("..", "risk-contracts",
                "stage46-offline-paper-state-chain-fixtures.json");
        if (!Files.isRegularFile(path)) {
            path = Path.of("risk-contracts",
                    "stage46-offline-paper-state-chain-fixtures.json");
        }
        fixture = json.readTree(Files.readString(path));
        publicLabKey = fixture.path("test_key_utf8").asText()
                .getBytes(StandardCharsets.US_ASCII);
    }

    @AfterEach void cleanup() {
        repository.deleteById("paper-default");
    }

    private JsonNode sample(int n) { return fixture.path("snapshots").get(n); }
    private OfflinePaperReplayLedgerService.Decision check(int n) {
        JsonNode row = sample(n);
        return service.inspectAndRecord(
                row.path("state"), row.path("tag").asText(),
                row.path("verify_at_ms").asLong(), publicLabKey);
    }

    private void noAuthority(OfflinePaperReplayLedgerService.Decision decision) {
        assertThat(decision.decision()).isEqualTo("NO_GO");
        assertThat(decision.snapshotApplied()).isFalse();
        assertThat(decision.pythonTestnetOrderAuthorized()).isFalse();
        assertThat(decision.liveOrderAuthorized()).isFalse();
        assertThat(decision.productionAuthorized()).isFalse();
    }

    @Test void twoCommittedSignedSnapshotsAdvanceOneDurableAnchor() {
        var first = check(0);
        var second = check(1);
        assertThat(first.accepted()).isTrue();
        assertThat(first.acceptedSequence()).isEqualTo(1);
        assertThat(second.accepted()).isTrue();
        assertThat(second.acceptedSequence()).isEqualTo(2);
        noAuthority(first);
        noAuthority(second);
        var anchor = repository.findById("paper-default").orElseThrow();
        assertThat(anchor.getLastAcceptedSequence()).isEqualTo(2);
        assertThat(anchor.getLastAcceptedDigest()).isEqualTo(sample(1).path("sha256").asText());
        assertThat(anchor.invalid()).isFalse();
    }

    @Test void freshServiceObjectCannotReplayPreviouslyCommittedFirstSnapshot() {
        assertThat(check(0).accepted()).isTrue();
        var fresh = new OfflinePaperReplayLedgerService(repository, manager);
        var row = sample(0);
        var repeated = fresh.inspectAndRecord(
                row.path("state"), row.path("tag").asText(),
                row.path("verify_at_ms").asLong(), publicLabKey);
        assertThat(repeated.accepted()).isFalse();
        noAuthority(repeated);
        assertThat(repository.findById("paper-default").orElseThrow()
                .getLastAcceptedSequence()).isEqualTo(1);
    }

    @Test void replayAfterBothAcceptedCannotChangeHashOrSequence() {
        assertThat(check(0).accepted()).isTrue();
        assertThat(check(1).accepted()).isTrue();
        assertThat(check(0).accepted()).isFalse();
        assertThat(check(1).accepted()).isFalse();
        var anchor = repository.findById("paper-default").orElseThrow();
        assertThat(anchor.getLastAcceptedSequence()).isEqualTo(2);
        assertThat(anchor.getLastAcceptedDigest()).isEqualTo(sample(1).path("sha256").asText());
    }

    @Test void secondSnapshotBeforeGenesisDoesNotInitializeAnchor() {
        var denied = check(1);
        assertThat(denied.accepted()).isFalse();
        assertThat(repository.findById("paper-default")).isEmpty();
        noAuthority(denied);
    }

    @Test void forgedBalanceWithValidOldTagDoesNotAdvanceLedger() {
        ObjectNode altered = sample(0).path("state").deepCopy();
        altered.put("cash_minor", 900000);
        var denied = service.inspectAndRecord(altered,
                sample(0).path("tag").asText(),
                sample(0).path("verify_at_ms").asLong(), publicLabKey);
        assertThat(denied.accepted()).isFalse();
        assertThat(repository.findById("paper-default")).isEmpty();
        noAuthority(denied);
    }

    @Test void futureExpiredOrMissingKeyNeverCreatesAnchor() {
        JsonNode row=sample(0);
        for(long now : new long[] {
                row.path("state").path("issued_ms").asLong()-1,
                row.path("state").path("expires_ms").asLong()+1
        }) {
            assertThat(service.inspectAndRecord(row.path("state"),
                    row.path("tag").asText(), now, publicLabKey).accepted()).isFalse();
        }
        assertThat(service.inspectAndRecord(row.path("state"),
                row.path("tag").asText(), row.path("verify_at_ms").asLong(),
                new byte[0]).accepted()).isFalse();
        assertThat(repository.findById("paper-default")).isEmpty();
    }

    @Test void competingRequestsForSameSequenceAllowAtMostOneCommit() throws Exception {
        assertThat(check(0).accepted()).isTrue();
        var row = sample(1);
        CountDownLatch start = new CountDownLatch(1);
        Callable<OfflinePaperReplayLedgerService.Decision> call = () -> {
            start.await(5, TimeUnit.SECONDS);
            return service.inspectAndRecord(
                    row.path("state"), row.path("tag").asText(),
                    row.path("verify_at_ms").asLong(), publicLabKey);
        };
        try(var threads=Executors.newFixedThreadPool(2)) {
            var a=threads.submit(call);
            var b=threads.submit(call);
            start.countDown();
            var first=a.get(20,TimeUnit.SECONDS);
            var second=b.get(20,TimeUnit.SECONDS);
            long accepted=(first.accepted()?1:0)+(second.accepted()?1:0);
            assertThat(accepted).isEqualTo(1);
            noAuthority(first);
            noAuthority(second);
        }
        var durable=repository.findById("paper-default").orElseThrow();
        assertThat(durable.getLastAcceptedSequence()).isEqualTo(2);
        assertThat(durable.getLastAcceptedDigest())
                .isEqualTo(sample(1).path("sha256").asText());
    }

    @Test void competingGenesisCreationCannotBeApprovedTwice() throws Exception {
        var row=sample(0);
        CountDownLatch latch=new CountDownLatch(1);
        Callable<OfflinePaperReplayLedgerService.Decision> task=()->{
            latch.await(5,TimeUnit.SECONDS);
            return service.inspectAndRecord(
                    row.path("state"), row.path("tag").asText(),
                    row.path("verify_at_ms").asLong(), publicLabKey);
        };
        try(var threads=Executors.newFixedThreadPool(2)) {
            var a=threads.submit(task);
            var b=threads.submit(task);
            latch.countDown();
            var x=a.get(20,TimeUnit.SECONDS);
            var y=b.get(20,TimeUnit.SECONDS);
            assertThat((x.accepted()?1:0)+(y.accepted()?1:0)).isEqualTo(1);
            noAuthority(x);
            noAuthority(y);
        }
        assertThat(repository.findById("paper-default").orElseThrow()
                .getLastAcceptedSequence()).isEqualTo(1);
    }

    @Test void wrongPositionNotionalAndUnreviewedAuthorityCannotWriteAnchor() {
        ObjectNode malformed=sample(1).path("state").deepCopy();
        ((ObjectNode)malformed.withArray("positions").get(0))
                .put("notional_minor",12345);
        var outcome=service.inspectAndRecord(malformed,
                sample(1).path("tag").asText(),
                sample(1).path("verify_at_ms").asLong(),publicLabKey);
        assertThat(outcome.accepted()).isFalse();
        malformed=sample(0).path("state").deepCopy();
        malformed.put("authorize_live_trade",true);
        outcome=service.inspectAndRecord(malformed,
                sample(0).path("tag").asText(),
                sample(0).path("verify_at_ms").asLong(),publicLabKey);
        assertThat(outcome.accepted()).isFalse();
        assertThat(repository.findById("paper-default")).isEmpty();
    }
}
