package io.aetheris.orchestrator.trading;
public record TradingRiskPolicyRequest(double riskPerTradePct,double maxDailyLossPct,int maxLeverage,double minRewardRisk){}
