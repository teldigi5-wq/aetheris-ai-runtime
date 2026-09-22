package io.aetheris.orchestrator.trading;
public record TradeDecisionResult(TradeSignalEntity signal,String executionState,String detail){}
