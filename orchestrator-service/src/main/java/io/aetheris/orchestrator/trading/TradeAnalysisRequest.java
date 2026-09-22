package io.aetheris.orchestrator.trading;
public record TradeAnalysisRequest(String symbol,double currentPrice,double support,double resistance,int trendScore,int momentumScore,double volatilityPct,Double confidenceHint,double accountBalance){}
