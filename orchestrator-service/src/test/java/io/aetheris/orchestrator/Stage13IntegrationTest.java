package io.aetheris.orchestrator;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.approval.*;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.stage11.Stage11RuntimeEvidenceService;
import io.aetheris.orchestrator.stage12.*;
import io.aetheris.orchestrator.stage13.*;
import io.aetheris.orchestrator.task.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "AETHERIS_SECRET_STAGE13_NOTIFY=test-fixture-only",
        "AETHERIS_SECRET_STAGE13_MARKET_A=test-fixture-only",
        "AETHERIS_SECRET_STAGE13_MARKET_B=test-fixture-only",
        "AETHERIS_SECRET_STAGE13_EXCHANGE=test-fixture-only"
})
class Stage13IntegrationTest {
    @Autowired Stage12ProviderRegistryService providers;
    @Autowired Stage13ProviderHealthEvidenceService health;
    @Autowired Stage13CredentialBindingService credentials;
    @Autowired Stage13OnboardingAutomationService onboarding;
    @Autowired Stage13RedundantMarketArbitrationService market;
    @Autowired Stage13TestnetExecutionJournalService testnet;
    @Autowired TaskService tasks;
    @Autowired ApprovalService approvals;
    @Autowired Stage11RuntimeEvidenceService stage11Evidence;
    @Autowired Stage12TargetAttestationService targetAttestations;

    @Test
    void providerOnboardingUsesDurableHealthVaultAliasAndOwnerApprovalWithoutExternalAction() {
        registerHealthyProvider("stage13-notify", "NOTIFICATION", "stage13-notify", Set.of("SEND_NOTIFICATION"), false);
        recordHealthyWindow("stage13-notify", "1");

        var binding = credentials.assess("stage13-notify");
        assertThat(binding.available()).isTrue();
        assertThat(binding.operatingSystemBacked()).isFalse();
        assertThat(binding.secretValueExposed()).isFalse();
        assertThat(health.assessWindow("stage13-notify").status()).isEqualTo("HEALTH_WINDOW_READY");

        var task = planningTask("Stage 13 provider onboarding");
        var plan = onboarding.create(new Stage13OnboardingAutomationService.CreateOnboardingRequest(task.getId(), "stage13-notify"));
        var assessment = onboarding.assess(plan.getId());
        assertThat(assessment.status()).isEqualTo("AWAITING_OWNER_APPROVAL");
        assertThat(assessment.externalActionAttempted()).isFalse();

        var approval = approve(task.getId(), Stage13OnboardingAutomationService.APPROVAL_ACTION);
        var authorized = onboarding.authorize(plan.getId(), approval.getId());
        assertThat(authorized.getStatus()).isEqualTo("CONTROLLED_TEST_AUTHORIZED_NOT_EXECUTED");
        assertThat(authorized.isExternalActionAttempted()).isFalse();

        var verified = onboarding.recordControlledTestEvidence(plan.getId(),
                new Stage13OnboardingAutomationService.ControlledTestEvidence("PASS", true, "a".repeat(64), true));
        assertThat(verified.getStatus()).isEqualTo("CONTROLLED_TEST_VERIFIED");
        assertThat(verified.isRollbackAvailable()).isTrue();
        assertThat(verified.isExternalActionAttempted()).isFalse();
    }

    @Test
    void healthAutomationFailsClosedWithoutSufficientIndependentSamples() {
        registerHealthyProvider("stage13-health-thin", "NOTIFICATION", "stage13-notify", Set.of("SEND_NOTIFICATION"), false);
        health.record(new Stage13ProviderHealthEvidenceService.HealthEvidenceRequest(
                "stage13-health-thin", "HEALTHY", 40, true, "b".repeat(64), Instant.now()));
        var window = health.assessWindow("stage13-health-thin");
        assertThat(window.status()).isEqualTo("BLOCKED");
        assertThat(window.blockers()).anyMatch(x -> x.contains("3 recent"));
        assertThat(window.externalActionAttempted()).isFalse();
    }

    @Test
    void redundantMarketArbitrationIsReadOnlyAndBlocksDivergence() {
        registerHealthyProvider("stage13-market-a", "MARKET_DATA", "stage13-market-a", Set.of("READ_MARKET"), true);
        registerHealthyProvider("stage13-market-b", "MARKET_DATA", "stage13-market-b", Set.of("READ_MARKET"), true);
        recordHealthyWindow("stage13-market-a", "2");
        recordHealthyWindow("stage13-market-b", "3");

        var consensus = market.arbitrate(new Stage13RedundantMarketArbitrationService.ArbitrationRequest("BTCUSDT", List.of(
                new Stage13RedundantMarketArbitrationService.MarketSample("stage13-market-a", new BigDecimal("100.00"), 500, true, "c".repeat(64)),
                new Stage13RedundantMarketArbitrationService.MarketSample("stage13-market-b", new BigDecimal("100.50"), 650, true, "d".repeat(64))
        )));
        assertThat(consensus.status()).isEqualTo("REDUNDANT_READ_ONLY_CONSENSUS");
        assertThat(consensus.readOnly()).isTrue();
        assertThat(consensus.tradingAuthority()).isFalse();
        assertThat(consensus.orderAuthority()).isFalse();

        var divergent = market.arbitrate(new Stage13RedundantMarketArbitrationService.ArbitrationRequest("BTCUSDT", List.of(
                new Stage13RedundantMarketArbitrationService.MarketSample("stage13-market-a", new BigDecimal("100.00"), 500, true, "e".repeat(64)),
                new Stage13RedundantMarketArbitrationService.MarketSample("stage13-market-b", new BigDecimal("110.00"), 650, true, "f".repeat(64))
        )));
        assertThat(divergent.status()).isEqualTo("BLOCKED");
    }

    @Test
    void signedCanaryRequiresOwnerApprovalExactTargetProofAndRollbackButDoesNotDeploy() throws Exception {
        var task = planningTask("Stage 13 signed canary");
        var approval = approve(task.getId(), Stage13CanaryDeploymentService.APPROVAL_ACTION);
        String targetHash = sha256("stage13-target-proof".getBytes(StandardCharsets.UTF_8));
        stage11Evidence.record(new Stage11RuntimeEvidenceService.EvidenceRequest(
                "WORKSTATION", "PASS", "stage13-target", true, targetHash, "Stage 13 target proof fixture"));

        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair pair = generator.generateKeyPair();
        String artifact = sha256("stage13-candidate".getBytes(StandardCharsets.UTF_8));
        String rollback = sha256("stage13-rollback".getBytes(StandardCharsets.UTF_8));
        String fingerprint = sha256(pair.getPublic().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(Stage12ReleaseTrustService.canonicalPayload("13.0.0", artifact, "CANARY").getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());
        Stage12ReleaseTrustService trust = new Stage12ReleaseTrustService(fingerprint, publicKey);
        Stage13CanaryDeploymentService canary = new Stage13CanaryDeploymentService(trust, approvals, targetAttestations);

        var decision = canary.evaluate(new Stage13CanaryDeploymentService.CanaryRequest(task.getId(), approval.getId(),
                new Stage12ReleaseTrustService.ReleaseManifest("13.0.0", "CANARY", artifact, artifact, fingerprint, signature),
                "WORKSTATION", targetHash, rollback));
        assertThat(decision.status()).isEqualTo("CANARY_ELIGIBLE_NOT_EXECUTED");
        assertThat(decision.externalActionAttempted()).isFalse();
        assertThat(decision.productionPromoted()).isFalse();
        assertThat(decision.rollbackRequired()).isTrue();
    }

    @Test
    void testnetJournalRequiresApprovedIntentAndNeverEnablesLiveMoneyOrTransfers() {
        registerHealthyProvider("stage13-exchange", "EXCHANGE_TESTNET", "stage13-exchange",
                Set.of("READ_ACCOUNT", "READ_MARKET", "TEST_ORDER", "CANCEL_TEST_ORDER"), false,
                "https://api.testnet.exchange.example");
        var task = planningTask("Stage 13 testnet intent");
        var approval = approve(task.getId(), Stage13TestnetExecutionJournalService.APPROVAL_ACTION);

        var intent = testnet.createIntent(new Stage13TestnetExecutionJournalService.TestnetIntentRequest(
                task.getId(), approval.getId(), "stage13-exchange", "stage13-order-001", "BTCUSDT",
                "BUY", "LIMIT", new BigDecimal("0.01000000"), "9".repeat(64)));
        assertThat(intent.getStatus()).isEqualTo("TESTNET_INTENT_AUTHORIZED_NOT_EXECUTED");
        assertThat(intent.isExternalActionAttempted()).isFalse();
        assertThat(intent.isLiveMoneyEnabled()).isFalse();
        assertThat(intent.isWithdrawalOrTransferEnabled()).isFalse();

        var evidence = testnet.recordProviderEvidence(intent.getId(),
                new Stage13TestnetExecutionJournalService.TestnetProviderEvidence("FILLED", true, "8".repeat(64)));
        assertThat(evidence.getStatus()).isEqualTo("TESTNET_PROVIDER_REPORTED_FILLED");
        assertThat(evidence.isLiveMoneyEnabled()).isFalse();
        assertThat(evidence.isWithdrawalOrTransferEnabled()).isFalse();
        assertThat(evidence.isExternalActionAttempted()).isFalse();
    }

    private void registerHealthyProvider(String id, String type, String alias, Set<String> capabilities, boolean readOnly) {
        registerHealthyProvider(id, type, alias, capabilities, readOnly, "https://" + id + ".example.test");
    }

    private void registerHealthyProvider(String id, String type, String alias, Set<String> capabilities,
                                         boolean readOnly, String endpoint) {
        providers.register(new Stage12ProviderRegistryService.ProviderRegistration(id, type, endpoint, alias, true, capabilities, readOnly));
        providers.recordHealth(id, new Stage12ProviderRegistryService.ProviderHealthEvidence(
                "HEALTHY", 35, true, sha(id + "-stage12-health"), Instant.now()));
    }

    private void recordHealthyWindow(String providerId, String seed) {
        for (int i = 0; i < 3; i++) {
            health.record(new Stage13ProviderHealthEvidenceService.HealthEvidenceRequest(providerId, "HEALTHY",
                    30 + i, true, sha(seed + "-" + i + "-" + providerId), Instant.now().minusSeconds(3L - i)));
        }
    }

    private TaskEntity planningTask(String title) {
        TaskEntity task = tasks.create(new CreateTaskRequest(title, "Stage 13 controlled automation test", OperationMode.PRIVATE));
        return tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.PLANNING, "release-manager", "Prepare owner-controlled Stage 13 action"));
    }

    private ApprovalEntity approve(UUID taskId, String action) {
        ApprovalEntity approval = approvals.request(new CreateApprovalRequest(taskId, action,
                "Owner approval fixture for " + action, RiskLevel.HIGH));
        return approvals.decide(approval.getId(), new ApprovalDecisionRequest(true, "Approved for controlled Stage 13 test fixture"));
    }

    private String sha(String value) {
        try { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
