package io.aetheris.orchestrator.trading;public record PaperExecutionResult(boolean executed,String mode,String detail,PaperPositionEntity position,PaperRiskDecision riskDecision){}
