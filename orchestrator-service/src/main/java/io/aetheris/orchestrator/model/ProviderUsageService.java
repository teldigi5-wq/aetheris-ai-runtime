package io.aetheris.orchestrator.model;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class ProviderUsageService {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private final ProviderUsageRepository repository;

    public ProviderUsageService(ProviderUsageRepository repository) {
        this.repository = repository;
    }

    public ProviderBudgetSnapshot check(ModelProviderAdapter provider, long requestedUnits) {
        long safeRequestedUnits = Math.max(1, requestedUnits);
        List<ProviderUsageEntity> today = repository.findByProviderIdAndCreatedAtAfter(
                provider.id(), LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC));
        long usedUnits = today.stream().mapToLong(ProviderUsageEntity::getUnits).sum();
        BigDecimal usedCost = today.stream()
                .map(ProviderUsageEntity::getEstimatedCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long quota = provider.dailyQuotaUnits();
        BigDecimal budget = provider.dailyBudgetUsd();
        BigDecimal requestedCost = estimateCost(provider, safeRequestedUnits);

        if (quota > 0 && quota != Long.MAX_VALUE && usedUnits + safeRequestedUnits > quota) {
            return new ProviderBudgetSnapshot(provider.id(), usedUnits, quota, usedCost, budget, false,
                    "Provider daily quota would be exceeded");
        }
        if (!provider.zeroCost()) {
            if (budget == null || budget.signum() <= 0) {
                return new ProviderBudgetSnapshot(provider.id(), usedUnits, quota, usedCost, BigDecimal.ZERO, false,
                        "Paid provider has no explicit daily budget configured");
            }
            if (usedCost.add(requestedCost).compareTo(budget) > 0) {
                return new ProviderBudgetSnapshot(provider.id(), usedUnits, quota, usedCost, budget, false,
                        "Provider daily cost budget would be exceeded");
            }
        }
        return new ProviderBudgetSnapshot(provider.id(), usedUnits, quota, usedCost,
                budget == null ? BigDecimal.ZERO : budget, true, "Provider usage is within configured limits");
    }

    public ProviderUsageEntity record(ModelProviderAdapter provider, UUID taskId, long units) {
        long safeUnits = Math.max(1, units);
        return repository.save(new ProviderUsageEntity(
                UUID.randomUUID(), provider.id(), taskId, safeUnits, estimateCost(provider, safeUnits)));
    }

    public ProviderBudgetSnapshot snapshot(ModelProviderAdapter provider) {
        return check(provider, 0);
    }

    private BigDecimal estimateCost(ModelProviderAdapter provider, long units) {
        if (provider.zeroCost()) return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        BigDecimal rate = provider.costPerThousandUnitsUsd();
        if (rate == null || rate.signum() <= 0) return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        return rate.multiply(BigDecimal.valueOf(units))
                .divide(THOUSAND, 8, RoundingMode.HALF_UP);
    }
}
