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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Stage46.33 real H2/JPA recovery read-only diagnostics. Tests are synthetic
 * and make NO trades, checkpoint export, real owner approval or key claims.
 */
@SpringBootTest
class PaperRecoveryReviewIntegrationTest {
    @Autowired PaperRecoveryReviewService review;
    @Autowired PaperAccountRepository accounts;
    @Autowired PaperDrawdownGuardRepository guards;
    @Autowired PaperPositionRepository positions;
    @Autowired PaperEquitySnapshotRepository marks;
    @Autowired OfflinePaperReplayAnchorRepository anchors;
    @Autowired OfflinePaperReplayLedgerService ledger;

    private final ObjectMapper mapper = new ObjectMapper();
    private JsonNode checkpointFixture;
    private JsonNode paperFixture;
    private byte[] publicCheckpointKey;
    private byte[] publicLedgerKey;

    @BeforeEach void setup() throws Exception {
        // Disposable H2/JPA integration-only fixtures. No actual accounts.
        marks.deleteAll();
        positions.deleteAll();
        guards.deleteById("paper-default");
        accounts.deleteById("paper-default");
        anchors.deleteById("paper-default");

        Path root = Path.of("..", "risk-contracts");
        if (!Files.isDirectory(root)) root = Path.of("risk-contracts");
        checkpointFixture = mapper.readTree(Files.readString(
                root.resolve("stage46-independent-replay-checkpoint-fixture.json")));
        paperFixture = mapper.readTree(Files.readString(
                root.resolve("stage46-offline-paper-state-chain-fixtures.json")));
        publicCheckpointKey = checkpointFixture.path("public_test_key_utf8")
                .asText().getBytes(StandardCharsets.US_ASCII);
        publicLedgerKey = paperFixture.path("test_key_utf8")
                .asText().getBytes(StandardCharsets.US_ASCII);
        for (JsonNode row : paperFixture.path("snapshots")) {
            assertThat(ledger.inspectAndRecord(row.path("state"),
                    row.path("tag").asText(), row.path("verify_at_ms").asLong(),
                    publicLedgerKey).accepted()).isTrue();
        }
        accounts.saveAndFlush(new PaperAccountEntity("paper-default", 10000));
        PaperDrawdownGuardEntity guard =
                new PaperDrawdownGuardEntity("paper-default", 10000);
        guard.observe(10000, true);
        guards.saveAndFlush(guard);
        marks.saveAndFlush(new PaperEquitySnapshotEntity(
                UUID.randomUUID(), 10000, 10000, 0, 0, 0));
    }

    @AfterEach void cleanup() {
        marks.deleteAll();
        positions.deleteAll();
        guards.deleteById("paper-default");
        accounts.deleteById("paper-default");
        anchors.deleteById("paper-default");
    }

    private PaperRecoveryReviewService.Review assess() {
        return review.assess(
                checkpointFixture.path("checkpoint"),
                checkpointFixture.path("tag").asText(),
                publicCheckpointKey,
                checkpointFixture.path("check_at_ms").asLong(),
                Instant.now());
    }

    private void noAuthority(PaperRecoveryReviewService.Review value) {
        assertThat(value.decision()).isEqualTo("NO_GO");
        assertThat(value.stateMutated()).isFalse();
        assertThat(value.drawdownCircuitReset()).isFalse();
        assertThat(value.ownerApprovedRecovery()).isFalse();
        assertThat(value.testnetOrdersAuthorized()).isFalse();
        assertThat(value.liveMoneyAuthorized()).isFalse();
        assertThat(value.productionAuthorized()).isFalse();
    }

    @Test void consistentSyntheticPortfolioOnlyAllowsHumanReviewNotRecovery() {
        var output = assess();
        assertThat(output.classification())
                .isEqualTo("SYNTHETIC_EVIDENCE_CONSISTENT_REQUIRES_OWNER_REVIEW");
        assertThat(output.evidenceEligibleForIndependentHumanReview()).isTrue();
        assertThat(output.blockers()).containsExactly(
                "INDEPENDENT_CHECKPOINT_CUSTODY_UNVERIFIED");
        assertThat(output.observedReplaySequence()).isEqualTo(2);
        assertThat(output.pinnedCheckpointSequence()).isEqualTo(2);
        assertThat(output.observedOpenPositions()).isZero();
        assertThat(output.marketMarkFresh()).isTrue();
        noAuthority(output);
    }

    @Test void missingAccountBlocksReviewDespiteValidSignedFixture() {
        accounts.deleteById("paper-default");
        var result=assess();
        assertThat(result.evidenceEligibleForIndependentHumanReview()).isFalse();
        assertThat(result.blockers()).contains("PAPER_ACCOUNT_MISSING");
        noAuthority(result);
    }

    @Test void accountCashDisagreementWithLastMarketMarkFailsClosed() {
        var account=accounts.findById("paper-default").orElseThrow();
        account.realize(-100);
        accounts.saveAndFlush(account);
        var result=assess();
        assertThat(result.blockers())
                .contains("MARK_CASH_BALANCE_DISAGREES_WITH_ACCOUNT");
        assertThat(result.evidenceEligibleForIndependentHumanReview()).isFalse();
        noAuthority(result);
    }

    @Test void missingMarkedEquityRejectsRecoveryReview() {
        marks.deleteAll();
        var result=assess();
        assertThat(result.blockers()).contains("LATEST_PAPER_MARK_STALE_OR_MISSING");
        assertThat(result.marketMarkFresh()).isFalse();
        noAuthority(result);
    }

    @Test void explicitStalenessIsBasedOnActualMarkTimestampNotFixtureClock() {
        var result=review.assess(checkpointFixture.path("checkpoint"),
                checkpointFixture.path("tag").asText(), publicCheckpointKey,
                checkpointFixture.path("check_at_ms").asLong(),
                Instant.now().plusSeconds(400));
        assertThat(result.blockers()).contains("LATEST_PAPER_MARK_STALE_OR_MISSING");
        assertThat(result.evidenceEligibleForIndependentHumanReview()).isFalse();
        noAuthority(result);
    }

    @Test void latchedDrawdownIsNeverClearedByEvidenceReview() {
        var guard=guards.findById("paper-default").orElseThrow();
        guard.observe(9000, true);
        guards.saveAndFlush(guard);
        var before=guards.findById("paper-default").orElseThrow();
        var halt=before.getHaltedAt();
        assertThat(before.isHalted()).isTrue();
        var output=assess();
        assertThat(output.blockers()).contains("PAPER_DRAWDOWN_CIRCUIT_LATCHED");
        assertThat(guards.findById("paper-default").orElseThrow().getHaltedAt())
                .isEqualTo(halt);
        assertThat(guards.findById("paper-default").orElseThrow().isHalted())
                .isTrue();
        noAuthority(output);
    }

    @Test void portfolioPositionCountMismatchBlocksReview() {
        var signal=new TradeSignalEntity(UUID.randomUUID(), "BTCUSDT",
                TradeSide.LONG,100,99,102,104,106,1,.5,2.5,80,85,
                false,1000,"Inert synthetic",TradeSignalStatus.ACCEPTED_ANALYSIS);
        positions.saveAndFlush(new PaperPositionEntity(
                UUID.randomUUID(),signal,100,10));
        var output=assess();
        assertThat(output.blockers()).contains("PAPER_EQUITY_MARK_INCONSISTENT");
        assertThat(output.evidenceEligibleForIndependentHumanReview()).isFalse();
        noAuthority(output);
    }

    @Test void missingOrRolledBackReplayAnchorBlocksReview() {
        anchors.deleteById("paper-default");
        var missing=assess();
        assertThat(missing.blockers()).contains(
                "CHECKPOINT_DB_ANCHOR_MISSING_OR_RESTORED",
                "DURABLE_REPLAY_ANCHOR_UNAVAILABLE");
        noAuthority(missing);
        anchors.saveAndFlush(new OfflinePaperReplayAnchorEntity(
                "paper-default", 1,
                paperFixture.path("snapshots").get(0).path("sha256").asText()));
        var stale=assess();
        assertThat(stale.blockers()).contains(
                "CHECKPOINT_STALE_DB_RESTORE_DETECTED",
                "CHECKPOINT_ANCHOR_SEQUENCE_DRIFT");
        noAuthority(stale);
    }

    @Test void forgedOrExpiredCheckpointIsNotTrustedForRecovery() {
        ObjectNode edited=checkpointFixture.path("checkpoint").deepCopy();
        edited.put("highest_sequence", 1);
        var output=review.assess(edited,
                checkpointFixture.path("tag").asText(),publicCheckpointKey,
                checkpointFixture.path("check_at_ms").asLong(),Instant.now());
        assertThat(output.blockers()).contains("CHECKPOINT_CHECKPOINT_TAG_MISMATCH");
        assertThat(output.evidenceEligibleForIndependentHumanReview()).isFalse();
        noAuthority(output);
        var expired=review.assess(checkpointFixture.path("checkpoint"),
                checkpointFixture.path("tag").asText(),publicCheckpointKey,
                checkpointFixture.path("checkpoint").path("expires_ms").asLong()+1,
                Instant.now());
        assertThat(expired.blockers()).contains("CHECKPOINT_INVALID_OR_EXPIRED_CHECKPOINT");
        noAuthority(expired);
    }

    @Test void inspectionDoesNotChangeAnyExistingDatabaseStateOrVersion() {
        var initialAnchor=anchors.findById("paper-default").orElseThrow();
        var initialGuard=guards.findById("paper-default").orElseThrow();
        long version=initialAnchor.getVersion();
        String digest=initialAnchor.getLastAcceptedDigest();
        var guardTime=initialGuard.getUpdatedAt();
        long accountsCount=accounts.count(), markCount=marks.count(),
                positionCount=positions.count();
        for(int i=0;i<2;i++) {
            var result=assess();
            noAuthority(result);
        }
        assertThat(anchors.findById("paper-default").orElseThrow().getVersion())
                .isEqualTo(version);
        assertThat(anchors.findById("paper-default").orElseThrow().getLastAcceptedDigest())
                .isEqualTo(digest);
        assertThat(guards.findById("paper-default").orElseThrow().getUpdatedAt())
                .isEqualTo(guardTime);
        assertThat(accounts.count()).isEqualTo(accountsCount);
        assertThat(marks.count()).isEqualTo(markCount);
        assertThat(positions.count()).isEqualTo(positionCount);
    }
}
