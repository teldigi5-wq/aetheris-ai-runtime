package io.aetheris.orchestrator.model;
import java.math.BigDecimal;
public record TaskModelObjectiveRequest(long maxLatencyMs,double minQualityScore,BigDecimal maxCostUsdPerRequest,boolean preferLocal,boolean allowPaid){}
