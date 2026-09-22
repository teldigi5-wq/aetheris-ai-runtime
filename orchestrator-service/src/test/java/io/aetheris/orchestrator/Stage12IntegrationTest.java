package io.aetheris.orchestrator;

import io.aetheris.orchestrator.stage11.Stage11RuntimeEvidenceService;
import io.aetheris.orchestrator.stage12.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class Stage12IntegrationTest {
    @Autowired Stage12ProviderRegistryService providers;
    @Autowired Stage12TargetAttestationService attestations;
    @Autowired Stage12PrivateTransportService transport;
    @Autowired Stage12ExternalIntegrationService external;
    @Autowired Stage11RuntimeEvidenceService stage11Evidence;
    @Autowired Stage12ReleaseTrustService defaultReleaseTrust;

    @Test
    void providerRegistryFailsClosedAndNeverExposesSecretValues() {
        assertThatThrownBy(() -> providers.register(new Stage12ProviderRegistryService.ProviderRegistration(
                "live-exchange", "EXCHANGE_TESTNET", "https://api.exchange.example", "exchange-key",
                true, Set.of("TEST_ORDER"), false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("testnet");

        assertThatThrownBy(() -> providers.register(new Stage12ProviderRegistryService.ProviderRegistration(
                "forbidden-cap", "EXCHANGE_TESTNET", "https://testnet.exchange.example", "exchange-key",
                true, Set.of("LIVE_ORDER"), false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Forbidden");

        var provider = providers.register(new Stage12ProviderRegistryService.ProviderRegistration(
                "market-stage12-a", "MARKET_DATA", "https://market.example.test", "market-key-alias",
                true, Set.of("READ_MARKET"), true));
        assertThat(provider.status()).isEqualTo("CONFIGURED_PENDING_PROVIDER_EVIDENCE");
        assertThat(provider.credentialAlias()).isEqualTo("market-key-alias");
        assertThat(provider.credentialValueExposed()).isFalse();

        assertThatThrownBy(() -> providers.recordHealth("market-stage12-a",
                new Stage12ProviderRegistryService.ProviderHealthEvidence("HEALTHY", 40, false,
                        "a".repeat(64), Instant.now())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be certified by CI");
    }

    @Test
    void releasePromotionRequiresArtifactHashTrustedSignerAndValidEd25519Signature() throws Exception {
        var blocked = defaultReleaseTrust.verify(new Stage12ReleaseTrustService.ReleaseManifest(
                "12.0.0", "CANARY", "a".repeat(64), "a".repeat(64), "b".repeat(64), "ZmFrZQ=="));
        assertThat(blocked.status()).isEqualTo("BLOCKED");
        assertThat(blocked.signerTrusted()).isFalse();

        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair pair = generator.generateKeyPair();
        String artifact = sha256("stage12-artifact".getBytes(StandardCharsets.UTF_8));
        String fingerprint = sha256(pair.getPublic().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        String payload = Stage12ReleaseTrustService.canonicalPayload("12.0.0", artifact, "CANARY");
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());

        Stage12ReleaseTrustService trust = new Stage12ReleaseTrustService(fingerprint, publicKey);
        var verified = trust.verify(new Stage12ReleaseTrustService.ReleaseManifest(
                "12.0.0", "CANARY", artifact, artifact, fingerprint, signature));
        assertThat(verified.status()).isEqualTo("CRYPTOGRAPHICALLY_VERIFIED");
        assertThat(verified.artifactHashMatches()).isTrue();
        assertThat(verified.signerTrusted()).isTrue();
        assertThat(verified.signatureVerified()).isTrue();

        var tampered = trust.verify(new Stage12ReleaseTrustService.ReleaseManifest(
                "12.0.0", "CANARY", artifact, "f".repeat(64), fingerprint, signature));
        assertThat(tampered.status()).isEqualTo("BLOCKED");
        assertThat(tampered.artifactHashMatches()).isFalse();
    }

    @Test
    void targetAttestationMustMatchTheExactExpectedEvidenceHash() {
        stage11Evidence.record(new Stage11RuntimeEvidenceService.EvidenceRequest(
                "WORKSTATION", "PASS", "stage12-ci", false, null, "CI-only Stage 12 precursor"));
        var unrelated = attestations.assess("WORKSTATION", "9".repeat(64));
        assertThat(unrelated.status()).isEqualTo("EVIDENCE_REQUIRED");
        assertThat(unrelated.targetMeasured()).isFalse();
        assertThat(unrelated.attestationSha256()).isEqualTo("9".repeat(64));

        stage11Evidence.record(new Stage11RuntimeEvidenceService.EvidenceRequest(
                "WORKSTATION", "PASS", "owner-target", true, "c".repeat(64), "Owner target measurement evidence"));
        var wrongHash = attestations.assess("WORKSTATION", "8".repeat(64));
        assertThat(wrongHash.status()).isEqualTo("EVIDENCE_REQUIRED");

        var target = attestations.assess("WORKSTATION", "c".repeat(64));
        assertThat(target.status()).isEqualTo("TARGET_ATTESTATION_AVAILABLE");
        assertThat(target.targetMeasured()).isTrue();
        assertThat(target.attestationSha256()).isEqualTo("c".repeat(64));
    }

    @Test
    void privateTransportRequiresProviderHealthAndTargetEvidenceButNeverClaimsActiveProductionTunnel() {
        providers.register(new Stage12ProviderRegistryService.ProviderRegistration(
                "transport-stage12-a", "PRIVATE_TRANSPORT", "https://tunnel.example.test", "transport-cert-alias",
                true, Set.of("TUNNEL_STATUS"), true));
        providers.recordHealth("transport-stage12-a", new Stage12ProviderRegistryService.ProviderHealthEvidence(
                "HEALTHY", 30, true, "d".repeat(64), Instant.now()));

        var synthetic = transport.evaluate(new Stage12PrivateTransportService.TransportEvidence(
                "transport-stage12-a", "TLS1.3", true, true, true, false,
                "e".repeat(64), false, null));
        assertThat(synthetic.status()).isEqualTo("BLOCKED");
        assertThat(synthetic.blockers()).anyMatch(x -> x.contains("target transport evidence"));

        var eligible = transport.evaluate(new Stage12PrivateTransportService.TransportEvidence(
                "transport-stage12-a", "TLS1.3", true, true, true, false,
                "e".repeat(64), true, "f".repeat(64)));
        assertThat(eligible.status()).isEqualTo("ELIGIBLE_FOR_PRIVATE_TRANSPORT_TEST");
        assertThat(eligible.productionTunnelActive()).isFalse();
        assertThat(eligible.rawPublicAgentApiAllowed()).isFalse();
    }

    @Test
    void externalAdaptersRemainEvidenceDrivenAndExchangeIsTestnetOnly() {
        providers.register(new Stage12ProviderRegistryService.ProviderRegistration(
                "notify-stage12-a", "NOTIFICATION", "https://notify.example.test", "notify-key-alias",
                true, Set.of("SEND_NOTIFICATION"), false));
        providers.recordHealth("notify-stage12-a", new Stage12ProviderRegistryService.ProviderHealthEvidence(
                "HEALTHY", 35, true, "1".repeat(64), Instant.now()));
        var notification = external.notification("notify-stage12-a");
        assertThat(notification.status()).isEqualTo("READY_FOR_CONTROLLED_PROVIDER_TEST");
        assertThat(notification.externalActionAttempted()).isFalse();
        var delivered = external.evaluateNotificationDelivery("notify-stage12-a",
                new Stage12ExternalIntegrationService.DeliveryEvidence("DELIVERED", true, "2".repeat(64), false));
        assertThat(delivered.secretValueExposed()).isFalse();

        providers.register(new Stage12ProviderRegistryService.ProviderRegistration(
                "market-stage12-b", "MARKET_DATA", "https://feed.example.test", "market-key-b",
                true, Set.of("READ_MARKET"), true));
        providers.recordHealth("market-stage12-b", new Stage12ProviderRegistryService.ProviderHealthEvidence(
                "HEALTHY", 25, true, "3".repeat(64), Instant.now()));
        var market = external.evaluateMarketSnapshot("market-stage12-b",
                new Stage12ExternalIntegrationService.MarketEvidence("BTCUSDT", 900, true, "4".repeat(64), true));
        assertThat(market.readOnly()).isTrue();

        providers.register(new Stage12ProviderRegistryService.ProviderRegistration(
                "exchange-stage12-a", "EXCHANGE_TESTNET", "https://api.testnet.exchange.example", "exchange-testnet-key",
                true, Set.of("READ_ACCOUNT", "READ_MARKET", "TEST_ORDER", "CANCEL_TEST_ORDER"), false));
        providers.recordHealth("exchange-stage12-a", new Stage12ProviderRegistryService.ProviderHealthEvidence(
                "HEALTHY", 55, true, "5".repeat(64), Instant.now()));
        var exchange = external.exchangeTestnet("exchange-stage12-a");
        assertThat(exchange.status()).isEqualTo("TESTNET_PROVIDER_READY_FOR_CONTROLLED_TEST");
        assertThat(exchange.externalActionAttempted()).isFalse();
        assertThat(exchange.liveMoneyEnabled()).isFalse();
        assertThat(exchange.withdrawalOrTransferEnabled()).isFalse();
    }

    private String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
