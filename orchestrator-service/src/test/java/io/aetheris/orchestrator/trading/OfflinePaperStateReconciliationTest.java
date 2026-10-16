package io.aetheris.orchestrator.trading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class OfflinePaperStateReconciliationTest {
    private final ObjectMapper json = new ObjectMapper();

    private JsonNode data() throws Exception {
        Path path = Path.of("..", "risk-contracts", "stage46-offline-paper-state-chain-fixtures.json");
        if (!Files.isRegularFile(path)) {
            path = Path.of("risk-contracts", "stage46-offline-paper-state-chain-fixtures.json");
        }
        return json.readTree(Files.readString(path));
    }

    private byte[] key() throws Exception {
        return data().path("test_key_utf8").asText().getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void pythonReferenceWireHashesAndAuthenticationTagsMatchRealJava() throws Exception {
        for (JsonNode row : data().path("snapshots")) {
            assertThat(OfflinePaperStateReconciliation.digest(row.path("state")))
                    .isEqualTo(row.path("sha256").asText());
            assertThat(OfflinePaperStateReconciliation.mac(row.path("state"), key()))
                    .isEqualTo(row.path("tag").asText());
        }
    }

    @Test
    void orderedStateReviewsNeverWriteOrAuthorizeTrading() throws Exception {
        var reviewer = new OfflinePaperStateReconciliation.OfflineVerifier(key());
        int sequence = 0;
        for (JsonNode row : data().path("snapshots")) {
            var result = reviewer.inspect(row.path("state"), row.path("tag").asText(),
                    row.path("verify_at_ms").asLong());
            assertThat(result.accepted()).isTrue();
            assertThat(result.sequence()).isEqualTo(++sequence);
            assertThat(result.testOnly()).isTrue();
            assertThat(result.stateApplied()).isFalse();
            assertThat(result.pythonQuantTestnetAuthorized()).isFalse();
            assertThat(result.liveMoneyAuthorized()).isFalse();
            assertThat(result.productionAuthorized()).isFalse();
            assertThat(result.decision()).isEqualTo("NO_GO");
        }
    }

    @Test
    void previouslyReviewedSnapshotCannotBeReviewedAgain() throws Exception {
        JsonNode first = data().path("snapshots").get(0);
        var reviewer = new OfflinePaperStateReconciliation.OfflineVerifier(key());
        assertThat(reviewer.inspect(first.path("state"), first.path("tag").asText(),
                first.path("verify_at_ms").asLong()).accepted()).isTrue();
        var repeat = reviewer.inspect(first.path("state"), first.path("tag").asText(),
                first.path("verify_at_ms").asLong());
        assertThat(repeat.accepted()).isFalse();
        assertThat(repeat.sequence()).isEqualTo(1);
    }

    @Test
    void changedCashOrEquityCannotReuseOldAuthenticationTag() throws Exception {
        JsonNode first = data().path("snapshots").get(0);
        ObjectNode changed = first.path("state").deepCopy();
        changed.put("equity_minor", 900000);
        var result = new OfflinePaperStateReconciliation.OfflineVerifier(key()).inspect(
                changed, first.path("tag").asText(), first.path("verify_at_ms").asLong());
        assertThat(result.accepted()).isFalse();
        assertThat(result.sequence()).isZero();
        assertThat(result.decision()).isEqualTo("NO_GO");
    }

    @Test
    void missingPreviousSequenceOrWrongDigestCannotBypassChain() throws Exception {
        JsonNode first = data().path("snapshots").get(0);
        JsonNode second = data().path("snapshots").get(1);
        var verifier = new OfflinePaperStateReconciliation.OfflineVerifier(key());
        assertThat(verifier.inspect(second.path("state"), second.path("tag").asText(),
                second.path("verify_at_ms").asLong()).accepted()).isFalse();
        assertThat(verifier.inspect(first.path("state"), first.path("tag").asText(),
                first.path("verify_at_ms").asLong()).accepted()).isTrue();
        ObjectNode incorrect = second.path("state").deepCopy();
        incorrect.put("previous_digest", "0".repeat(64));
        assertThat(verifier.inspect(incorrect,
                OfflinePaperStateReconciliation.mac(incorrect, key()),
                second.path("verify_at_ms").asLong()).accepted()).isFalse();
    }

    @Test
    void expirationOrPrematureTimestampAlwaysRefusesImport() throws Exception {
        JsonNode first = data().path("snapshots").get(0);
        var reviewer = new OfflinePaperStateReconciliation.OfflineVerifier(key());
        assertThat(reviewer.inspect(first.path("state"), first.path("tag").asText(),
                first.path("state").path("expires_ms").asLong()+1).accepted()).isFalse();
        assertThat(reviewer.inspect(first.path("state"), first.path("tag").asText(),
                first.path("state").path("issued_ms").asLong()-1).accepted()).isFalse();
    }

    @Test
    void duplicatePositionIdentifiersAndInconsistentQuantityRefused() throws Exception {
        ObjectNode second = data().path("snapshots").get(1).path("state").deepCopy();
        second.withArray("positions").add(second.withArray("positions").get(0).deepCopy());
        assertThatThrownBy(() -> OfflinePaperStateReconciliation.canonical(second))
                .isInstanceOf(IllegalArgumentException.class);
        ObjectNode changed = data().path("snapshots").get(1).path("state").deepCopy();
        ((ObjectNode) changed.withArray("positions").get(0)).put("quantity_microunits", 2000001);
        assertThatThrownBy(() -> OfflinePaperStateReconciliation.canonical(changed))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newFieldsOrMissingKeyNeverCreateOrderPermission() throws Exception {
        ObjectNode initial = data().path("snapshots").get(0).path("state").deepCopy();
        initial.put("allow_exchange_order", true);
        assertThatThrownBy(() -> OfflinePaperStateReconciliation.canonical(initial))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OfflinePaperStateReconciliation.OfflineVerifier(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
