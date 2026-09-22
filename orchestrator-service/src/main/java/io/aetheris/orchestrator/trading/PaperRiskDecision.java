package io.aetheris.orchestrator.trading;import java.util.List;public record PaperRiskDecision(boolean allowed,List<String> reasons,double projectedGrossExposure,double projectedCorrelatedExposure){}
