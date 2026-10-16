package io.aetheris.orchestrator.trading;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Actual disposable H2/JPA snapshot checks; no owner identity or broker. */
@SpringBootTest
class PaperRecoverySnapshotFingerprintIntegrationTest {
    @Autowired PaperRecoverySnapshotFingerprintService snapshots;
    @Autowired PaperAccountRepository accounts;
    @Autowired PaperDrawdownGuardRepository guards;
    @Autowired PaperPositionRepository positions;
    @Autowired PaperEquitySnapshotRepository marks;
    @Autowired OfflinePaperReplayAnchorRepository anchors;

    @BeforeEach void setup() {
        marks.deleteAll();
        positions.deleteAll();
        guards.deleteById("paper-default");
        accounts.deleteById("paper-default");
        anchors.deleteById("paper-default");
        accounts.saveAndFlush(new PaperAccountEntity("paper-default", 10000));
        var guard = new PaperDrawdownGuardEntity("paper-default", 10000);
        guard.observe(10000, true);
        guards.saveAndFlush(guard);
        anchors.saveAndFlush(new OfflinePaperReplayAnchorEntity(
                "paper-default", 1, "a".repeat(64)));
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

    private PaperRecoverySnapshotFingerprintService.Assessment check(String fingerprint) {
        return snapshots.review(Instant.now(), fingerprint);
    }

    private static void deniedAuthority(PaperRecoverySnapshotFingerprintService.Assessment a) {
        assertThat(a.decision()).isEqualTo("NO_GO");
        assertThat(a.readOnly()).isTrue();
        assertThat(a.ownerAuthorized()).isFalse();
        assertThat(a.recoveryApplied()).isFalse();
        assertThat(a.drawdownReset()).isFalse();
        assertThat(a.paperOrTestnetOrdersAuthorized()).isFalse();
        assertThat(a.liveMoneyAuthorized()).isFalse();
        assertThat(a.productionAuthorized()).isFalse();
    }

    @Test void coherentLocalSnapshotYieldsUntrustedReviewFingerprintNotAuthorization() {
        var a=check(null);
        assertThat(a.evidenceConsistent()).isTrue();
        assertThat(a.expectedFingerprintMatched()).isFalse();
        assertThat(a.snapshotFingerprint()).matches("[0-9a-f]{64}");
        assertThat(a.blockers()).containsExactly("OWNER_IDENTITY_AND_EXTERNAL_CUSTODY_NOT_VERIFIED");
        deniedAuthority(a);
        var b=check(a.snapshotFingerprint());
        assertThat(b.evidenceConsistent()).isTrue();
        assertThat(b.expectedFingerprintMatched()).isTrue();
        assertThat(b.snapshotFingerprint()).isEqualTo(a.snapshotFingerprint());
        deniedAuthority(b);
    }

    @Test void staleReferenceAfterCashMutationIsRejected() {
        String first=check(null).snapshotFingerprint();
        var account=accounts.findById("paper-default").orElseThrow();
        account.realize(-75);
        accounts.saveAndFlush(account);
        var result=check(first);
        assertThat(result.evidenceConsistent()).isFalse();
        assertThat(result.blockers()).contains("ACCOUNT_MARK_CASH_DRIFT");
        deniedAuthority(result);
    }

    @Test void validNewMarkAndEquityChangeInvalidateOldReviewDigest() {
        String first=check(null).snapshotFingerprint();
        var guard=guards.findById("paper-default").orElseThrow();
        guard.observe(9900, true);
        guards.saveAndFlush(guard);
        marks.saveAndFlush(new PaperEquitySnapshotEntity(
                UUID.randomUUID(), 10000, 9900, -100, 0, 0));
        var result=check(first);
        assertThat(result.evidenceConsistent()).isFalse();
        assertThat(result.blockers()).contains("SNAPSHOT_FINGERPRINT_CHANGED");
        assertThat(result.snapshotFingerprint()).matches("[0-9a-f]{64}");
        deniedAuthority(result);
    }

    @Test void missingReplayAnchorOrCashAccountRefuses() {
        anchors.deleteById("paper-default");
        var noAnchor=check(null);
        assertThat(noAnchor.blockers()).contains("REPLAY_ANCHOR_MISSING");
        deniedAuthority(noAnchor);
        accounts.deleteById("paper-default");
        var noAccount=check(null);
        assertThat(noAccount.blockers()).contains("PAPER_ACCOUNT_MISSING");
        deniedAuthority(noAccount);
    }

    @Test void haltedDrawdownCannotBeResetByPassingFingerprint() {
        var g=guards.findById("paper-default").orElseThrow();
        g.observe(9000, true);
        guards.saveAndFlush(g);
        var a=check(null);
        assertThat(a.blockers()).contains("DRAWDOWN_LATCHED");
        assertThat(guards.findById("paper-default").orElseThrow().isHalted()).isTrue();
        deniedAuthority(a);
    }

    @Test void newOpenPositionWithoutMatchingMarketMarkIsRejected() {
        var signal=new TradeSignalEntity(UUID.randomUUID(),"BTCUSDT",TradeSide.LONG,
                100,99,102,104,106,1,.5,2,80,85,false,1000,"Synthetic",
                TradeSignalStatus.ACCEPTED_ANALYSIS);
        positions.saveAndFlush(new PaperPositionEntity(
                UUID.randomUUID(),signal,100,10));
        var a=check(null);
        assertThat(a.blockers()).contains("INCONSISTENT_MARK_OR_POSITIONS");
        deniedAuthority(a);
    }

    @Test void pastOrFutureMarkClockFailsClosed() {
        var old=snapshots.review(Instant.now().plusSeconds(300),null);
        assertThat(old.blockers()).contains("STALE_OR_FUTURE_MARK");
        var future=snapshots.review(Instant.now().minusSeconds(300),null);
        assertThat(future.blockers()).contains("STALE_OR_FUTURE_MARK");
        deniedAuthority(old);deniedAuthority(future);
    }

    @Test void arbitraryOrMalformedExpectedHashCannotAuthorize() {
        var invalid=check("abc");
        assertThat(invalid.blockers()).containsExactly("MALFORMED_REFERENCE_FINGERPRINT");
        var other=check("f".repeat(64));
        assertThat(other.blockers()).containsExactly("SNAPSHOT_FINGERPRINT_CHANGED");
        deniedAuthority(invalid);deniedAuthority(other);
    }

    @Test void repeatReviewDoesNotChangePersistedAccountOrCircuitVersion() {
        var before=guards.findById("paper-default").orElseThrow();
        var initialVersion=before.getVersion();
        var initialTime=before.getUpdatedAt();
        var anchor=anchors.findById("paper-default").orElseThrow();
        var anchorVersion=anchor.getVersion();
        String hash=anchor.getLastAcceptedDigest();
        long count=marks.count();
        var fingerprint=check(null).snapshotFingerprint();
        for(int i=0;i<3;i++) {
            var result=check(fingerprint);
            assertThat(result.evidenceConsistent()).isTrue();
            deniedAuthority(result);
        }
        assertThat(guards.findById("paper-default").orElseThrow().getVersion())
                .isEqualTo(initialVersion);
        assertThat(guards.findById("paper-default").orElseThrow().getUpdatedAt())
                .isEqualTo(initialTime);
        assertThat(anchors.findById("paper-default").orElseThrow().getVersion())
                .isEqualTo(anchorVersion);
        assertThat(anchors.findById("paper-default").orElseThrow().getLastAcceptedDigest())
                .isEqualTo(hash);
        assertThat(marks.count()).isEqualTo(count);
    }
}
