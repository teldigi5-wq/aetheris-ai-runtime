package io.aetheris.orchestrator.syntracore;

import io.aetheris.orchestrator.stage32.EmergencyMode;
import io.aetheris.orchestrator.stage33.AdapterActivationAuthorityBridge;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase13AdapterActivationGovernanceTest {
    private static final String DATASET_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String ARTIFACT_HASH = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String CANDIDATE = "aetheris-adapter://candidate/experiment-9/owner-adapter";
    private static final String PROMOTED = "aetheris-adapter://promoted/experiment-9/owner-adapter";
    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");

    private final SyntraAdapterLifecycleGovernanceService lifecycle =
            new SyntraAdapterLifecycleGovernanceService();

    @Test
    void exactApprovedArtifactActivatesOnlyWithStage33OwnerAuthorityAndVerification() {
        AdapterLifecycleRequest request = request();
        FakeActivationPort port = new FakeActivationPort(request.artifactIdentity(), false);

        AdapterLifecycleResult result = lifecycle.activate(
                request,
                port,
                approval("activate-token", request.activationActionId()),
                NOW,
                EmergencyMode.NORMAL);

        assertEquals(AdapterLifecycleStatus.ACTIVATED_VERIFIED, result.status());
        assertTrue(result.effectAttempted());
        assertTrue(result.executionVerified());
        assertTrue(result.active());
        assertTrue(result.authorityGranted());
        assertTrue(result.oneTimeApprovalConsumed());
        assertEquals(1, port.inspectCalls);
        assertEquals(1, port.activateCalls);
        assertEquals(0, port.detachCalls);
    }

    @Test
    void missingPointOfEffectApprovalBlocksBeforeActivation() {
        AdapterLifecycleRequest request = request();
        FakeActivationPort port = new FakeActivationPort(request.artifactIdentity(), false);

        AdapterLifecycleResult result = lifecycle.activate(
                request,
                port,
                null,
                NOW,
                EmergencyMode.NORMAL);

        assertEquals(AdapterLifecycleStatus.BLOCKED, result.status());
        assertFalse(result.effectAttempted());
        assertFalse(result.authorityGranted());
        assertEquals(0, port.activateCalls);
        assertTrue(result.reasons().stream().anyMatch(reason -> reason.contains("approval")));
    }

    @Test
    void inspectedArtifactIdentityMismatchFailsClosedBeforeAuthorityOrEffect() {
        AdapterLifecycleRequest request = request();
        AdapterArtifactIdentity differentArtifact = artifact(
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");
        FakeActivationPort port = new FakeActivationPort(differentArtifact, false);

        AdapterLifecycleResult result = lifecycle.activate(
                request,
                port,
                approval("identity-token", request.activationActionId()),
                NOW,
                EmergencyMode.NORMAL);

        assertEquals(AdapterLifecycleStatus.BLOCKED, result.status());
        assertEquals(List.of("ARTIFACT_IDENTITY_MISMATCH"), result.reasons());
        assertEquals(0, port.activateCalls);
    }

    @Test
    void forgedPromotionDecisionCannotReachActivationPort() {
        AdapterLifecycleRequest valid = request();
        AdapterPromotionDecision forged = new AdapterPromotionDecision(
                "experiment-9",
                CANDIDATE,
                AdapterPromotionStatus.HELD,
                java.util.Optional.empty(),
                false,
                true,
                false,
                false,
                "DETACH_ADAPTER",
                List.of("OWNER_APPROVAL_REQUIRED"));
        AdapterLifecycleRequest request = new AdapterLifecycleRequest(
                valid.plan(),
                valid.evidence(),
                valid.promotionPolicy(),
                forged,
                valid.artifactIdentity(),
                valid.activationActionId(),
                valid.rollbackActionId(),
                valid.actorId());
        FakeActivationPort port = new FakeActivationPort(valid.artifactIdentity(), false);

        assertThrows(
                IllegalArgumentException.class,
                () -> lifecycle.activate(
                        request,
                        port,
                        approval("forged-token", request.activationActionId()),
                        NOW,
                        EmergencyMode.NORMAL));
        assertEquals(0, port.inspectCalls);
        assertEquals(0, port.activateCalls);
    }

    @Test
    void rollbackUsesDistinctScopedAuthorityAndVerifiesDetach() {
        AdapterLifecycleRequest request = request();
        FakeActivationPort port = new FakeActivationPort(request.artifactIdentity(), false);

        AdapterLifecycleResult activated = lifecycle.activate(
                request,
                port,
                approval("activation-token", request.activationActionId()),
                NOW,
                EmergencyMode.NORMAL);
        AdapterLifecycleResult rolledBack = lifecycle.rollback(
                request,
                port,
                approval("rollback-token", request.rollbackActionId()),
                NOW.plusSeconds(1),
                EmergencyMode.NORMAL);

        assertEquals(AdapterLifecycleStatus.ACTIVATED_VERIFIED, activated.status());
        assertEquals(AdapterLifecycleStatus.ROLLED_BACK_VERIFIED, rolledBack.status());
        assertTrue(rolledBack.effectAttempted());
        assertTrue(rolledBack.executionVerified());
        assertFalse(rolledBack.active());
        assertTrue(rolledBack.oneTimeApprovalConsumed());
        assertEquals(1, port.detachCalls);
    }

    @Test
    void oneTimeStage33ApprovalCannotBeReplayedAgainstAnotherActivationAttempt() {
        AdapterLifecycleRequest request = request();
        AdapterActivationAuthorityBridge.ScopedOwnerApproval approval =
                approval("one-time-token", request.activationActionId());
        FakeActivationPort first = new FakeActivationPort(request.artifactIdentity(), false);
        FakeActivationPort second = new FakeActivationPort(request.artifactIdentity(), false);

        AdapterLifecycleResult firstResult = lifecycle.activate(
                request,
                first,
                approval,
                NOW,
                EmergencyMode.NORMAL);
        AdapterLifecycleResult replayResult = lifecycle.activate(
                request,
                second,
                approval,
                NOW.plusSeconds(1),
                EmergencyMode.NORMAL);

        assertEquals(AdapterLifecycleStatus.ACTIVATED_VERIFIED, firstResult.status());
        assertEquals(AdapterLifecycleStatus.BLOCKED, replayResult.status());
        assertEquals(0, second.activateCalls);
        assertTrue(replayResult.reasons().stream().anyMatch(reason -> reason.contains("already been consumed")));
    }

    @Test
    void postEffectIdentityDriftRemainsExplicitlyUnverified() {
        AdapterLifecycleRequest request = request();
        FakeActivationPort port = new FakeActivationPort(request.artifactIdentity(), false);
        port.activationObservationIdentity = artifact(
                "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd");

        AdapterLifecycleResult result = lifecycle.activate(
                request,
                port,
                approval("unverified-token", request.activationActionId()),
                NOW,
                EmergencyMode.NORMAL);

        assertEquals(AdapterLifecycleStatus.ACTIVATION_UNVERIFIED, result.status());
        assertTrue(result.effectAttempted());
        assertFalse(result.executionVerified());
        assertTrue(result.authorityGranted());
    }

    @Test
    void stage32EmergencyStopBlocksActivationEvenWithValidOwnerApproval() {
        AdapterLifecycleRequest request = request();
        FakeActivationPort port = new FakeActivationPort(request.artifactIdentity(), false);

        AdapterLifecycleResult result = lifecycle.activate(
                request,
                port,
                approval("emergency-token", request.activationActionId()),
                NOW,
                EmergencyMode.STOP);

        assertEquals(AdapterLifecycleStatus.BLOCKED, result.status());
        assertEquals(0, port.activateCalls);
        assertTrue(result.reasons().stream().anyMatch(reason -> reason.contains("emergency control")));
    }

    private AdapterLifecycleRequest request() {
        AdaptationExperimentPlan plan = plan();
        AdapterPromotionEvidence evidence = passingEvidence();
        AdapterPromotionPolicy policy = AdapterPromotionPolicy.conservativeDefault();
        AdapterPromotionDecision promotion = new SyntraAdapterPromotionGovernanceService().decide(
                plan,
                evidence,
                policy,
                true);
        return new AdapterLifecycleRequest(
                plan,
                evidence,
                policy,
                promotion,
                artifact(ARTIFACT_HASH),
                "phase13-slice9-activate",
                "phase13-slice9-detach",
                "owner");
    }

    private AdaptationExperimentPlan plan() {
        return new AdaptationExperimentPlan(
                "experiment-9",
                AdaptationMethod.QLORA,
                "qwen2.5-coder:7b",
                DATASET_HASH,
                3,
                1,
                CANDIDATE,
                true,
                true,
                false,
                false,
                "DETACH_ADAPTER");
    }

    private AdapterPromotionEvidence passingEvidence() {
        return new AdapterPromotionEvidence(
                "experiment-9",
                CANDIDATE,
                "qwen2.5-coder:7b",
                DATASET_HASH,
                observed("qwen2.5-coder:7b", 0.80, 220, 45, 0.01),
                observed(CANDIDATE, 0.86, 210, 47, 0.01),
                List.of(new InferenceEvaluationAssessment(
                        "candidate-structure",
                        true,
                        List.of(),
                        LocalInferenceResult.completed(
                                InferenceTask.CODING,
                                new ModelRouteSelection("ollama", CANDIDATE, ExecutionTarget.CPU, 0.95, "candidate evaluation"),
                                "observed output",
                                List.of(),
                                List.of(),
                                1))));
    }

    private ObservedModelEvaluation observed(
            String modelId,
            double quality,
            double latency,
            double throughput,
            double failureRate) {
        return new ObservedModelEvaluation(
                modelId,
                quality,
                latency,
                throughput,
                failureRate,
                4096,
                0,
                true,
                EvaluationEvidenceSource.OWNER_HARDWARE);
    }

    private AdapterArtifactIdentity artifact(String sha256) {
        return new AdapterArtifactIdentity(
                "experiment-9",
                CANDIDATE,
                PROMOTED,
                "qwen2.5-coder:7b",
                DATASET_HASH,
                sha256,
                "aetheris-adapter-artifact://sha256/" + sha256,
                true,
                false);
    }

    private AdapterActivationAuthorityBridge.ScopedOwnerApproval approval(
            String tokenId,
            String actionId) {
        return new AdapterActivationAuthorityBridge.ScopedOwnerApproval(
                tokenId,
                "owner",
                actionId,
                PROMOTED,
                NOW.minusSeconds(30),
                NOW.plusSeconds(300),
                true,
                false);
    }

    private static final class FakeActivationPort implements LocalAdapterActivationPort {
        private AdapterArtifactIdentity observedIdentity;
        private AdapterArtifactIdentity activationObservationIdentity;
        private boolean active;
        private int inspectCalls;
        private int activateCalls;
        private int detachCalls;

        private FakeActivationPort(AdapterArtifactIdentity observedIdentity, boolean active) {
            this.observedIdentity = observedIdentity;
            this.active = active;
        }

        @Override
        public AdapterArtifactObservation inspect(AdapterArtifactIdentity expectedIdentity) {
            inspectCalls++;
            return observation(observedIdentity, active, "inspect-" + inspectCalls);
        }

        @Override
        public AdapterArtifactObservation activate(AdapterArtifactIdentity expectedIdentity) {
            activateCalls++;
            active = true;
            AdapterArtifactIdentity postIdentity = activationObservationIdentity == null
                    ? observedIdentity
                    : activationObservationIdentity;
            return observation(postIdentity, true, "activate-" + activateCalls);
        }

        @Override
        public AdapterArtifactObservation detach(AdapterArtifactIdentity expectedIdentity) {
            detachCalls++;
            active = false;
            return observation(observedIdentity, false, "detach-" + detachCalls);
        }

        private AdapterArtifactObservation observation(
                AdapterArtifactIdentity identity,
                boolean activeState,
                String suffix) {
            return new AdapterArtifactObservation(
                    identity,
                    true,
                    activeState,
                    "fake-local-adapter-port",
                    "aetheris-adapter-observation://phase13-slice9/" + suffix);
        }
    }
}
