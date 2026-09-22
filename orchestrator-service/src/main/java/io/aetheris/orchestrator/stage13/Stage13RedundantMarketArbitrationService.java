package io.aetheris.orchestrator.stage13;

import io.aetheris.orchestrator.stage12.Stage12ProviderRegistryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class Stage13RedundantMarketArbitrationService {
    private static final BigDecimal MAX_DIVERGENCE_PCT = new BigDecimal("1.50");
    private final Stage12ProviderRegistryService providers;
    private final Stage13ProviderHealthEvidenceService health;

    public Stage13RedundantMarketArbitrationService(Stage12ProviderRegistryService providers,
                                                    Stage13ProviderHealthEvidenceService health) {
        this.providers = providers;
        this.health = health;
    }

    public ArbitrationResult arbitrate(ArbitrationRequest request) {
        if (request == null || request.symbol() == null || request.symbol().isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }
        if (request.samples() == null || request.samples().size() < 2) {
            return blocked(request == null ? null : request.symbol(), List.of("at least two independent market sources are required"));
        }
        List<String> blockers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<BigDecimal> prices = new ArrayList<>();
        for (MarketSample sample : request.samples()) {
            if (sample == null) { blockers.add("market sample is required"); continue; }
            String providerId = sample.providerId() == null ? "" : sample.providerId().trim().toLowerCase(Locale.ROOT);
            if (!seen.add(providerId)) { blockers.add("duplicate market provider: " + providerId); continue; }
            var provider = providers.find(providerId).orElse(null);
            if (provider == null) { blockers.add("unknown market provider: " + providerId); continue; }
            if (!"MARKET_DATA".equals(provider.type())) blockers.add(providerId + " is not a MARKET_DATA provider");
            if (!provider.readOnly()) blockers.add(providerId + " is not read-only");
            var window = health.assessWindow(providerId);
            if (!"HEALTH_WINDOW_READY".equals(window.status())) blockers.add(providerId + " health window is not ready");
            if (!sample.providerMeasured()) blockers.add(providerId + " sample is not provider-measured");
            if (sample.ageMs() < 0 || sample.ageMs() > 5_000) blockers.add(providerId + " sample is stale");
            if (sample.price() == null || sample.price().signum() <= 0) blockers.add(providerId + " price is invalid");
            if (sample.attestationSha256() == null || !sample.attestationSha256().trim().toLowerCase(Locale.ROOT).matches("[a-f0-9]{64}")) {
                blockers.add(providerId + " sample lacks SHA-256 evidence");
            }
            if (sample.price() != null && sample.price().signum() > 0) prices.add(sample.price());
        }
        if (!blockers.isEmpty() || prices.size() < 2) return blocked(request.symbol(), blockers);
        prices.sort(BigDecimal::compareTo);
        BigDecimal median = prices.size() % 2 == 1 ? prices.get(prices.size() / 2)
                : prices.get(prices.size() / 2 - 1).add(prices.get(prices.size() / 2)).divide(BigDecimal.valueOf(2), 12, RoundingMode.HALF_UP);
        BigDecimal maxDeviationPct = prices.stream()
                .map(price -> price.subtract(median).abs().multiply(BigDecimal.valueOf(100)).divide(median, 8, RoundingMode.HALF_UP))
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        if (maxDeviationPct.compareTo(MAX_DIVERGENCE_PCT) > 0) {
            return new ArbitrationResult("BLOCKED", request.symbol().trim().toUpperCase(Locale.ROOT), median,
                    maxDeviationPct, seen.size(), List.of("source price divergence exceeds 1.50%"), true,
                    false, false, "Read-only source disagreement blocks downstream market use");
        }
        return new ArbitrationResult("REDUNDANT_READ_ONLY_CONSENSUS", request.symbol().trim().toUpperCase(Locale.ROOT), median,
                maxDeviationPct, seen.size(), List.of(), true, false, false,
                "Independent healthy read-only sources agree within the deterministic divergence threshold");
    }

    private ArbitrationResult blocked(String symbol, List<String> blockers) {
        return new ArbitrationResult("BLOCKED", symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT), null,
                null, 0, List.copyOf(blockers), true, false, false,
                "No trading authority is granted by market arbitration");
    }

    public record ArbitrationRequest(String symbol, List<MarketSample> samples) {}
    public record MarketSample(String providerId, BigDecimal price, long ageMs, boolean providerMeasured,
                               String attestationSha256) {}
    public record ArbitrationResult(String status, String symbol, BigDecimal consensusPrice,
                                    BigDecimal maxDeviationPct, int sourceCount, List<String> blockers,
                                    boolean readOnly, boolean tradingAuthority, boolean orderAuthority, String detail) {}
}
