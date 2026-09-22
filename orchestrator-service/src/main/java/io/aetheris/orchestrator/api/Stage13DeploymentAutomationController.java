package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.stage13.*;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/stage13")
public class Stage13DeploymentAutomationController {
    private final Stage13ProviderHealthEvidenceService health;
    private final Stage13CredentialBindingService credentials;
    private final Stage13OnboardingAutomationService onboarding;
    private final Stage13CanaryDeploymentService canary;
    private final Stage13RedundantMarketArbitrationService market;
    private final Stage13TestnetExecutionJournalService testnet;

    public Stage13DeploymentAutomationController(Stage13ProviderHealthEvidenceService health,
                                                 Stage13CredentialBindingService credentials,
                                                 Stage13OnboardingAutomationService onboarding,
                                                 Stage13CanaryDeploymentService canary,
                                                 Stage13RedundantMarketArbitrationService market,
                                                 Stage13TestnetExecutionJournalService testnet) {
        this.health = health;
        this.credentials = credentials;
        this.onboarding = onboarding;
        this.canary = canary;
        this.market = market;
        this.testnet = testnet;
    }

    @PostMapping("/provider-health/evidence")
    public Object recordHealth(@RequestBody Stage13ProviderHealthEvidenceService.HealthEvidenceRequest request) {
        return health.record(request);
    }

    @GetMapping("/provider-health/{providerId}")
    public Object healthWindow(@PathVariable String providerId) { return health.assessWindow(providerId); }

    @GetMapping("/credentials/{providerId}")
    public Object credentialBinding(@PathVariable String providerId) { return credentials.assess(providerId); }

    @PostMapping("/onboarding")
    public Object createOnboarding(@RequestBody Stage13OnboardingAutomationService.CreateOnboardingRequest request) {
        return onboarding.create(request);
    }

    @PostMapping("/onboarding/{planId}/assess")
    public Object assessOnboarding(@PathVariable UUID planId) { return onboarding.assess(planId); }

    @PostMapping("/onboarding/{planId}/authorize/{approvalId}")
    public Object authorizeOnboarding(@PathVariable UUID planId, @PathVariable UUID approvalId) {
        return onboarding.authorize(planId, approvalId);
    }

    @PostMapping("/onboarding/{planId}/evidence")
    public Object onboardingEvidence(@PathVariable UUID planId,
                                     @RequestBody Stage13OnboardingAutomationService.ControlledTestEvidence evidence) {
        return onboarding.recordControlledTestEvidence(planId, evidence);
    }

    @GetMapping("/onboarding")
    public Object onboardingPlans() { return onboarding.recent(); }

    @PostMapping("/canary/evaluate")
    public Object evaluateCanary(@RequestBody Stage13CanaryDeploymentService.CanaryRequest request) {
        return canary.evaluate(request);
    }

    @PostMapping("/market/arbitrate")
    public Object arbitrateMarket(@RequestBody Stage13RedundantMarketArbitrationService.ArbitrationRequest request) {
        return market.arbitrate(request);
    }

    @PostMapping("/testnet/intents")
    public Object createTestnetIntent(@RequestBody Stage13TestnetExecutionJournalService.TestnetIntentRequest request) {
        return testnet.createIntent(request);
    }

    @PostMapping("/testnet/{journalId}/evidence")
    public Object recordTestnetEvidence(@PathVariable UUID journalId,
                                        @RequestBody Stage13TestnetExecutionJournalService.TestnetProviderEvidence evidence) {
        return testnet.recordProviderEvidence(journalId, evidence);
    }

    @GetMapping("/testnet")
    public Object testnetJournal() { return testnet.recent(); }
}
