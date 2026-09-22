package io.aetheris.orchestrator.model;

import io.aetheris.orchestrator.agent.ModelClass;

import java.math.BigDecimal;
import java.util.Set;

public interface ModelProviderAdapter {
    String id();
    boolean local();
    boolean zeroCost();
    Set<ModelClass> supportedClasses();
    ModelProviderSnapshot snapshot();
    LocalGenerateResponse generate(LocalGenerateRequest request);

    default long dailyQuotaUnits() { return Long.MAX_VALUE; }
    default BigDecimal costPerThousandUnitsUsd() { return BigDecimal.ZERO; }
    default BigDecimal dailyBudgetUsd() { return BigDecimal.ZERO; }
}
