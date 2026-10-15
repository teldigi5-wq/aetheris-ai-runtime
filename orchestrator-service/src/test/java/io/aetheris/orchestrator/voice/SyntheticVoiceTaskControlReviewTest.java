package io.aetheris.orchestrator.voice;

import io.aetheris.orchestrator.task.TaskControlService;
import io.aetheris.orchestrator.voice.SyntheticVoiceOwnerConsentLab.Intent;
import io.aetheris.orchestrator.voice.SyntheticVoiceOwnerConsentLab.OwnerPermit;
import io.aetheris.orchestrator.voice.SyntheticVoiceOwnerConsentLab.Status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Only a synthetic integration check of real TaskControlService emergency
 * state -> non-executing text/consent policy, NOT a real OS STOP dispatcher.
 */
@SpringBootTest(properties = "aetheris.memory.retention-sweep-ms=3600000")
class SyntheticVoiceTaskControlReviewTest {
    @Autowired TaskControlService control;

    @Test
    void signedVoiceProposalStaysCancelledAfterTaskControlStopAndRelease() throws Exception {
        var keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var lab=new SyntheticVoiceOwnerConsentLab(
                keys.getPublic(),control::isEmergencyStopActive,Clock.systemUTC());
        String owner="syntheticOwner";
        UUID session=lab.openTrustedSession(owner);
        assertThat(lab.receiveSyntheticTranscript(session,"hi sintra").status())
                .isEqualTo(Status.WAKE_ACKNOWLEDGED);
        var pending=lab.receiveSyntheticTranscript(session,"open editor");
        assertThat(pending.status()).isEqualTo(Status.AWAITING_TRUSTED_OWNER_APPROVAL);

        long now=Clock.systemUTC().instant().getEpochSecond();
        var unsigned=new OwnerPermit(UUID.randomUUID(),session,pending.requestId(),
                owner,Intent.EDITOR_OPEN_PREVIEW,now,now+15,"0".repeat(128));
        var signer=Signature.getInstance("Ed25519");
        signer.initSign(keys.getPrivate());
        signer.update(SyntheticVoiceOwnerConsentLab.canonicalPermit(unsigned));
        var valid=new OwnerPermit(unsigned.tokenId(),session,pending.requestId(),
                owner,Intent.EDITOR_OPEN_PREVIEW,now,now+15,
                HexFormat.of().formatHex(signer.sign()));

        try {
            control.engage("synthetic pre-pilot STOP bridge test");
            assertThat(lab.simulateWithOwnerPermit(valid).status())
                    .isEqualTo(Status.EMERGENCY_STOP_LATCHED);
            assertThat(lab.isSyntheticStopLatched()).isTrue();
        } finally {
            control.release("reset only the integration test flag");
        }
        // Even though the global volatile flag was released, an old
        // signed request must never become a runnable approval.
        assertThat(lab.simulateWithOwnerPermit(valid).status())
                .isEqualTo(Status.EMERGENCY_STOP_LATCHED);
        assertThatThrownBy(() -> lab.openTrustedSession(owner))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("VOICE_STOP_ACTIVE");
        // No workstation agent, OS process, microphone or network invoked.
    }

    @Test
    void lostStopProbeMustLatchAndCannotReviveWithLaterRecovery() throws Exception {
        var keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var unavailable=new java.util.concurrent.atomic.AtomicBoolean(true);
        var lab=new SyntheticVoiceOwnerConsentLab(keys.getPublic(),() -> {
            if (unavailable.get()) throw new IllegalStateException("synthetic STOP signal missing");
            return false;
        },Clock.systemUTC());
        assertThatThrownBy(() -> lab.openTrustedSession("syntheticOwner"))
                .isInstanceOf(SecurityException.class);
        unavailable.set(false);
        assertThat(lab.isSyntheticStopLatched()).isTrue();
        assertThatThrownBy(() -> lab.openTrustedSession("syntheticOwner"))
                .isInstanceOf(SecurityException.class);
    }
}
