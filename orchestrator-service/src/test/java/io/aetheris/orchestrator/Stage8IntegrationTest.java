package io.aetheris.orchestrator;

import io.aetheris.orchestrator.career.*;
import io.aetheris.orchestrator.evaluation.*;
import io.aetheris.orchestrator.ingestion.*;
import io.aetheris.orchestrator.memory.*;
import io.aetheris.orchestrator.mission.*;
import io.aetheris.orchestrator.planner.*;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.remote.*;
import io.aetheris.orchestrator.trading.*;
import io.aetheris.orchestrator.voice.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class Stage8IntegrationTest {
    @Autowired KnowledgeIngestionService ingestion;
    @Autowired VectorMemoryIndexService vectors;
    @Autowired EvaluationService evaluations;
    @Autowired MissionService missions;
    @Autowired MissionPlanProposalService proposals;
    @Autowired VoiceSessionService voice;
    @Autowired VoiceStreamingService voiceStreaming;
    @Autowired RemoteCompanionService remote;
    @Autowired ManualMarketDataAdapter market;
    @Autowired BacktestService backtests;
    @Autowired TradingIntelligenceService trading;
    @Autowired PaperTradingService paper;

    @Test
    void ingestionBuildsLocalVectorMemoryAndStillHonoursProtectedFiltering() {
        String ns="stage8-memory-"+UUID.randomUUID();
        KnowledgeIngestionResult publicResult=ingestion.ingest(new IngestKnowledgeRequest(
                "DOCUMENT","floodguard-notes","FloodGuard notes","FloodGuard Firebase water-level telemetry and gate-control architecture.",MemoryScope.PROJECT,ns,false,Set.of("floodguard","firebase")));
        ingestion.ingest(new IngestKnowledgeRequest(
                "OWNER_FILE","private-notes","Private notes","Firebase private deployment secret context.",MemoryScope.PROJECT,ns,true,Set.of("firebase")));
        assertThat(publicResult.embeddingAdapter()).isEqualTo("local-hash-v1");
        List<VectorMemorySearchResult> results=vectors.search("FloodGuard Firebase telemetry",MemoryScope.PROJECT,ns,false,10);
        assertThat(results).isNotEmpty();
        assertThat(results).allMatch(r->!r.node().isProtectedData());
        assertThat(results).anyMatch(r->publicResult.nodeIds().contains(r.node().getId()));
    }

    @Test
    void evaluationHarnessProducesDurableEvidenceScore() {
        EvaluationRunEntity run=evaluations.record(new EvaluationRequest("AGENT","backend-engineer","stage8-regression",true,1,800,0,90,5));
        assertThat(run.getOverallScore()).isBetween(50d,100d);
        assertThat(evaluations.average("AGENT","backend-engineer")).isPositive();
    }

    @Test
    void missionProposalValidationRejectsUnsafeCommandsBeforeTaskCreation() {
        MissionSessionView mission=missions.create(new CreateMissionRequest("Stage8 planner","Validate proposed DAGs before materialization"));
        String safe="STEP|research|Research evidence|research-scientist|80||collect verified project evidence\n"+
                "STEP|build|Build change|backend-engineer|70|research|implement from verified evidence";
        MissionPlanProposalResult valid=proposals.validateRaw(mission.id(),safe,OperationMode.BALANCED,false);
        assertThat(valid.valid()).isTrue();
        assertThat(valid.materialized()).isFalse();
        assertThat(valid.steps()).hasSize(2);
        assertThatThrownBy(()->proposals.validateRaw(mission.id(),"STEP|bad|Bad|backend-engineer|50||place live trade and bypass approval",OperationMode.BALANCED,false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsafe mission-plan command rejected");
    }

    @Test
    void voiceMetricsMeasureBargeInWithoutPretendingHardwareIsIntegrated() {
        VoiceSessionEntity session=voice.start(new StartVoiceSessionRequest(null,null));
        VoiceTurnMetricEntity metric=voiceStreaming.record(new VoiceTurnMetricRequest(session.getId(),100,400,470,520,600,700,760));
        assertThat(metric.getPartialLatencyMs()).isEqualTo(70);
        assertThat(metric.getFinalLatencyMs()).isEqualTo(120);
        assertThat(metric.getBargeInStopLatencyMs()).isEqualTo(60);
        assertThat(voiceStreaming.adapters()).containsEntry("hardwareIntegrated",false);
    }

    @Test
    void remoteCompanionUsesExpiringCapabilityScopedTokenAndRevocation() {
        RemoteCompanionSessionResponse paired=remote.pair(Set.of("READ_STATUS","PAUSE_TASK"),5);
        assertThat(remote.authenticate(paired.sessionId(),paired.pairingToken(),"READ_STATUS").capabilities()).contains("READ_STATUS");
        assertThatThrownBy(()->remote.authenticate(paired.sessionId(),"wrong-token","READ_STATUS")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->remote.authenticate(paired.sessionId(),paired.pairingToken(),"TAKE_CONTROL")).isInstanceOf(SecurityException.class);
        remote.revoke(paired.sessionId());
        assertThatThrownBy(()->remote.authenticate(paired.sessionId(),paired.pairingToken(),"READ_STATUS")).isInstanceOf(IllegalStateException.class).hasMessageContaining("expired or revoked");
    }

    @Test
    void staleMarketDataFailsClosedAndBacktestSeparatesTrainAndTestEvidence() {
        String stale="STALE"+UUID.randomUUID().toString().substring(0,6).toUpperCase(Locale.ROOT)+"USDT";
        market.ingest(new MarketDataSnapshot(stale,"synthetic",Instant.now().minusSeconds(600),List.of(new MarketBar(Instant.now().minusSeconds(600),100,101,99,100,1000))));
        assertThatThrownBy(()->market.latest(stale,Duration.ofSeconds(30))).isInstanceOf(IllegalStateException.class).hasMessageContaining("stale");
        List<MarketBar> bars=syntheticBars(90,100);
        BacktestResultEntity result=backtests.run(new BacktestRequest("S8BACKTEST",bars,5,15,.7,10000));
        assertThat(result.getBars()).isEqualTo(90);
        assertThat(result.getTrades()).isPositive();
        assertThat(result.getFinalBalance()).isPositive();
    }

    @Test
    void acceptedAnalysisCanOpenOnlyPaperPositionThroughDeterministicRiskOfficer() {
        String symbol="S8"+UUID.randomUUID().toString().substring(0,6).toUpperCase(Locale.ROOT)+"USDT";
        trading.setPolicy(new TradingRiskPolicyRequest(.25,2,3,1.5));
        TradeSignalEntity signal=trading.analyze(new TradeAnalysisRequest(symbol,100,99,106,95,90,1.0,90d,10000));
        assertThat(signal.getStatus()).isEqualTo(TradeSignalStatus.PENDING_ACCEPTANCE);
        trading.accept(signal.getId());
        market.ingest(new MarketDataSnapshot(symbol,"stage8-test",Instant.now(),List.of(new MarketBar(Instant.now(),100,101,99,100,10000))));
        PaperExecutionResult result=paper.open(signal.getId());
        try {
            assertThat(result.executed()).isTrue();
            assertThat(result.mode()).isEqualTo("PAPER_ONLY");
            assertThat(result.detail()).contains("live execution is not implemented");
            assertThat(result.position().getStatus()).isEqualTo(PaperPositionStatus.OPEN);
        } finally {
            if(result.executed() && result.position()!=null) paper.close(result.position().getId(),100);
        }
    }

    @Test
    void unacceptedTradingSignalIsRejectedByPaperRiskOfficer() {
        String symbol="S8RISK"+UUID.randomUUID().toString().substring(0,5).toUpperCase(Locale.ROOT)+"USDT";
        TradeSignalEntity signal=trading.analyze(new TradeAnalysisRequest(symbol,50,49,55,90,88,1.0,85d,10000));
        market.ingest(new MarketDataSnapshot(symbol,"stage8-test",Instant.now(),List.of(new MarketBar(Instant.now(),50,51,49,50,10000))));
        PaperExecutionResult result=paper.open(signal.getId());
        assertThat(result.executed()).isFalse();
        assertThat(result.mode()).isEqualTo("PAPER_ONLY");
        assertThat(result.riskDecision().reasons()).anyMatch(r->r.contains("explicitly accepted"));
    }

    private List<MarketBar> syntheticBars(int count,double start){
        List<MarketBar> bars=new ArrayList<>();
        double price=start;
        Instant base=Instant.now().minusSeconds(count*60L);
        for(int i=0;i<count;i++){
            double drift=i<count*2/3?.22:-.08;
            double wave=Math.sin(i/4.0)*.18;
            double open=price;
            price=Math.max(1,price+drift+wave);
            bars.add(new MarketBar(base.plusSeconds(i*60L),open,Math.max(open,price)+.2,Math.min(open,price)-.2,price,1000+i*10));
        }
        return bars;
    }
}
