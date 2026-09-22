package io.aetheris.orchestrator.model;

import java.math.BigDecimal;

public record ProviderBudgetSnapshot(
        String providerId,
        long unitsToday,
        long dailyQuotaUnits,
        BigDecimal estimatedCostUsdToday,
        BigDecimal dailyBudgetUsd,
        boolean allowed,
        String detail
) {
}
