package io.aetheris.orchestrator.trading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Real JPA checkpoint/restore comparisons; intentionally NO database repair
 * or production trust chain. The fixed signer key is explicitly public CI data.
 */
@SpringBootTest
class OfflinePaperRollbackCheckpointIntegrationTest {
    @Autowired OfflinePaperReplayAnchorRepository repository;
    @Autowired OfflinePaperReplayLedgerService ledger;
    @Autowired OfflinePaperRollbackCheckpointService review;
    private final ObjectMapper mapper = new ObjectMapper();
    private JsonNode fixture;
    private JsonNode snapshots;
    private byte[] key;
    private byte[] ledgerKey;

    @BeforeEach void setUp() throws Exception {
        repository.deleteById("paper-default");
        Path dir = Path.of("..", "risk-contracts");
        if (!Files.isDirectory(dir)) dir = Path.of("risk-contracts");
        fixture = mapper.readTree(Files.readString(
                dir.resolve("stage46-independent-replay-checkpoint-fixture.json")));
        JsonNode prior = mapper.readTree(Files.readString(
                dir.resolve("stage46-offline-paper-state-chain-fixtures.json")));
        snapshots = prior.path("snapshots");
        ledgerKey = prior.path("test_key_utf8").asText()
                .getBytes(StandardCharsets.US_ASCII);
        key = fixture.path("public_test_key_utf8").asText()
                .getBytes(StandardCharsets.US_ASCII);
    }

    @AfterEach void tearDown() {
        repository.deleteById("paper-default");
    }

    private OfflinePaperReplayLedgerService.Decision record(int index) {
        var row=snapshots.get(index);
        return ledger.inspectAndRecord(row.path("state"),row.path("tag").asText(),
                row.path("verify_at_ms").asLong(),ledgerKey);
    }

    private OfflinePaperRollbackCheckpointService.Review check() {
        return review.compare(fixture.path("checkpoint"),fixture.path("tag").asText(),
                key,fixture.path("check_at_ms").asLong());
    }

    private void noAuthority(OfflinePaperRollbackCheckpointService.Review r) {
        assertThat(r.decision()).isEqualTo("NO_GO");
        assertThat(r.independentTrustRootVerified()).isFalse();
        assertThat(r.accountStateImported()).isFalse();
        assertThat(r.pythonQuantTestnetAuthorized()).isFalse();
        assertThat(r.liveMoneyAuthorized()).isFalse();
        assertThat(r.productionAuthorized()).isFalse();
    }

    @Test void twoCommittedSnapshotsMatchPresentedSeparatelyPinnedSyntheticCheckpoint() {
        assertThat(record(0).accepted()).isTrue();
        assertThat(record(1).accepted()).isTrue();
        var result=check();
        assertThat(result.checkpointAuthenticatedWithSuppliedTestKey()).isTrue();
        assertThat(result.databaseMatchesPresentedCheckpoint()).isTrue();
        assertThat(result.classification()).isEqualTo("FIXTURE_CHECKPOINT_MATCH");
        noAuthority(result);
        assertThat(repository.findById("paper-default").orElseThrow()
                .getLastAcceptedSequence()).isEqualTo(2);
    }

    @Test void losingEntireDatabaseAnchorIsReportedAsRestoreNotAsGenesis() {
        assertThat(record(0).accepted()).isTrue();
        assertThat(record(1).accepted()).isTrue();
        repository.deleteById("paper-default");
        var result=check();
        assertThat(result.databaseMatchesPresentedCheckpoint()).isFalse();
        assertThat(result.classification()).isEqualTo("DB_ANCHOR_MISSING_OR_RESTORED");
        noAuthority(result);
        assertThat(repository.findById("paper-default")).isEmpty();
    }

    @Test void staleDbBackupAtSequenceOneIsDetectedAgainstPinnedSequenceTwo() {
        assertThat(record(0).accepted()).isTrue();
        assertThat(record(1).accepted()).isTrue();
        repository.deleteById("paper-default"); // simulate restored old DB row
        repository.saveAndFlush(new OfflinePaperReplayAnchorEntity(
                "paper-default",1,snapshots.get(0).path("sha256").asText()));
        var result=check();
        assertThat(result.classification()).isEqualTo("STALE_DB_RESTORE_DETECTED");
        assertThat(result.databaseMatchesPresentedCheckpoint()).isFalse();
        noAuthority(result);
        assertThat(repository.findById("paper-default").orElseThrow()
                .getLastAcceptedSequence()).isEqualTo(1);
    }

    @Test void equalSequenceWithDivergentHashFailsClosed() {
        assertThat(record(0).accepted()).isTrue();
        assertThat(record(1).accepted()).isTrue();
        ObjectNode tampered=fixture.path("checkpoint").deepCopy();
        tampered.put("highest_digest","f".repeat(64));
        var result=review.compare(tampered,
                OfflinePaperRollbackCheckpointService.fixtureMac(tampered,key),
                key,fixture.path("check_at_ms").asLong());
        assertThat(result.classification()).isEqualTo("EQUAL_SEQUENCE_DIFFERENT_DIGEST");
        noAuthority(result);
    }

    @Test void dbAheadOfPresentedCheckpointIsUnanchoredAndRefused() {
        assertThat(record(0).accepted()).isTrue();
        assertThat(record(1).accepted()).isTrue();
        ObjectNode old=fixture.path("checkpoint").deepCopy();
        old.put("highest_sequence",1);
        old.put("highest_digest",snapshots.get(0).path("sha256").asText());
        var result=review.compare(old,
                OfflinePaperRollbackCheckpointService.fixtureMac(old,key),
                key,fixture.path("check_at_ms").asLong());
        assertThat(result.classification()).isEqualTo("UNANCHORED_DB_ADVANCE");
        noAuthority(result);
    }

    @Test void alteredReceiptCannotReusePreviousTag() {
        assertThat(record(0).accepted()).isTrue();
        assertThat(record(1).accepted()).isTrue();
        ObjectNode altered=fixture.path("checkpoint").deepCopy();
        altered.put("highest_sequence",1);
        var result=review.compare(altered,fixture.path("tag").asText(),key,
                fixture.path("check_at_ms").asLong());
        assertThat(result.classification()).isEqualTo("CHECKPOINT_TAG_MISMATCH");
        noAuthority(result);
    }

    @Test void unknownPermissionsInReceiptRejectBeforeDbRead() {
        ObjectNode receipt=fixture.path("checkpoint").deepCopy();
        receipt.put("authorize_orders",true);
        var result=review.compare(receipt,fixture.path("tag").asText(),key,
                fixture.path("check_at_ms").asLong());
        assertThat(result.databaseMatchesPresentedCheckpoint()).isFalse();
        noAuthority(result);
    }

    @Test void expiredAndFutureReceiptAreNeverAccepted() {
        assertThat(record(0).accepted()).isTrue();
        assertThat(record(1).accepted()).isTrue();
        JsonNode checkpoint=fixture.path("checkpoint");
        for (long now : new long[] {
                checkpoint.path("issued_ms").asLong()-1,
                checkpoint.path("expires_ms").asLong()+1 }) {
            var result=review.compare(checkpoint,fixture.path("tag").asText(),key,now);
            assertThat(result.classification()).isEqualTo("INVALID_OR_EXPIRED_CHECKPOINT");
            noAuthority(result);
        }
    }

    @Test void shortPublicKeyAndMalformedSignatureFailClosed() {
        for (String tag : new String[] { "f".repeat(64),"nothex" }) {
            var result=review.compare(fixture.path("checkpoint"),tag,key,
                    fixture.path("check_at_ms").asLong());
            assertThat(result.databaseMatchesPresentedCheckpoint()).isFalse();
            noAuthority(result);
        }
        var result=review.compare(fixture.path("checkpoint"),
                fixture.path("tag").asText(),new byte[1],fixture.path("check_at_ms").asLong());
        assertThat(result.databaseMatchesPresentedCheckpoint()).isFalse();
        noAuthority(result);
    }

    @Test void invalidSignedLongAndUnknownScopeAreRejected() {
        ObjectNode mutated=fixture.path("checkpoint").deepCopy();
        mutated.put("highest_sequence",Long.MAX_VALUE);
        assertThatThrownBy(()->OfflinePaperRollbackCheckpointService.canonical(mutated))
                .isInstanceOf(IllegalArgumentException.class);
        mutated=fixture.path("checkpoint").deepCopy();
        mutated.put("scope","REAL_BROKER");
        assertThatThrownBy(()->OfflinePaperRollbackCheckpointService.canonical(mutated))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void recheckNeverChangesPersistedAnchorVersionOrDigest() {
        assertThat(record(0).accepted()).isTrue();
        assertThat(record(1).accepted()).isTrue();
        var first=repository.findById("paper-default").orElseThrow();
        Long version=first.getVersion();
        String digest=first.getLastAcceptedDigest();
        assertThat(check().databaseMatchesPresentedCheckpoint()).isTrue();
        assertThat(check().databaseMatchesPresentedCheckpoint()).isTrue();
        var after=repository.findById("paper-default").orElseThrow();
        assertThat(after.getVersion()).isEqualTo(version);
        assertThat(after.getLastAcceptedDigest()).isEqualTo(digest);
    }
}
