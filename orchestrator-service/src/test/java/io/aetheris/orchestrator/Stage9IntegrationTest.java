package io.aetheris.orchestrator;

import io.aetheris.orchestrator.evaluation.*;
import io.aetheris.orchestrator.ingestion.*;
import io.aetheris.orchestrator.memory.*;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.runtime.*;
import io.aetheris.orchestrator.scheduler.*;
import io.aetheris.orchestrator.stage9.*;
import io.aetheris.orchestrator.task.*;
import io.aetheris.orchestrator.trading.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class Stage9IntegrationTest {
    @Autowired EmbeddingAdapter stage8Embedding;
    @Autowired AdaptiveLocalEmbeddingAdapter stage9Embedding;
    @Autowired IncrementalKnowledgeIngestionService ingestion;
    @Autowired Stage9AdaptiveVectorIndexService stage9Vectors;
    @Autowired MissionDryRunService dryRuns;
    @Autowired BenchmarkService benchmarks;
    @Autowired TaskService tasks;
    @Autowired DurableWorkQueueService queue;
    @Autowired SchedulerService scheduler;
    @Autowired CapabilityAwareDispatchService capabilityDispatch;
    @Autowired MultiSourceMarketDataBook market;
    @Autowired AdvancedBacktestService advancedBacktests;
    @Autowired TradingIntelligenceService trading;
    @Autowired PaperRiskOfficerService paperRisk;
    @Autowired Stage9PaperTradingService stage9Paper;
    @Autowired PaperPortfolioAnalyticsService portfolio;

    @Test
    void stage8EmbeddingIdentityRemainsStableAndStage9AdaptiveFallbackIsMeasured(){
        assertThat(stage8Embedding.id()).isEqualTo("local-hash-v1");
        float[] vector=stage9Embedding.embed("Stage 9 local embedding compatibility test");
        assertThat(vector).hasSize(96);
        EmbeddingRuntimeSnapshot runtime=stage9Embedding.runtime();
        assertThat(runtime.adapterId()).isEqualTo("adaptive-local-v2");
        assertThat(runtime.fallbackUsed()).isTrue();
        assertThat(runtime.neuralUsed()).isFalse();
    }

    @Test
    void incrementalIngestionSkipsUnchangedContentAndTombstonesSupersededKnowledge(){
        String ns="stage9-memory-"+UUID.randomUUID();
        String source="owner-note-"+UUID.randomUUID();
        IngestKnowledgeRequest request=new IngestKnowledgeRequest("TEXT",source,"Stage 9 note","Aetheris Stage 9 uses incremental source hashes and tombstones for knowledge sync.",MemoryScope.PROJECT,ns,false,Set.of("stage9","sync"));
        IncrementalIngestionResult first=ingestion.sync(request);
        IncrementalIngestionResult second=ingestion.sync(request);
        assertThat(first.changed()).isTrue();
        assertThat(second.changed()).isFalse();
        assertThat(second.activeNodeIds()).containsExactlyElementsOf(first.activeNodeIds());
        assertThat(stage9Vectors.search("incremental tombstones",MemoryScope.PROJECT,ns,false,10)).isNotEmpty();
        ingestion.tombstone("TEXT",source,ns);
        assertThat(stage9Vectors.search("incremental tombstones",MemoryScope.PROJECT,ns,false,10)).isEmpty();
    }

    @Test
    void missionDryRunPredictsRiskWithoutMaterializingAndRejectsUnsafeCommands(){
        MissionDryRunResult safe=dryRuns.evaluate(new MissionDryRunRequest(OperationMode.ZERO_COST,List.of(
                new io.aetheris.orchestrator.planner.PlanStepRequest("research","Research","research and verify evidence","research-scientist",80,Set.of()),
                new io.aetheris.orchestrator.planner.PlanStepRequest("build","Build","implement code and run tests","backend-engineer",70,Set.of("research"))
        ),false,false,12000));
        assertThat(safe.valid()).isTrue();
        assertThat(safe.estimatedMaxCostUsd()).isZero();
        assertThat(safe.detail()).contains("no task");
        assertThatThrownBy(()->dryRuns.evaluate(new MissionDryRunRequest(OperationMode.BALANCED,List.of(
                new io.aetheris.orchestrator.planner.PlanStepRequest("bad","Bad","place live trade and bypass approval","quant-researcher",50,Set.of())
        ),false,false,1000))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsafe");
    }

    @Test
    void capabilityAwareDispatcherSelectsCompatibleWorker(){
        TaskEntity task=tasks.create(new CreateTaskRequest("Stage9 capability dispatch","compile code",OperationMode.BALANCED));
        WorkItemEntity item=queue.enqueue(new EnqueueWorkItemRequest(task.getId(),"STAGE9_CAPABILITY","{}",2));
        scheduler.schedule(new ScheduleWorkRequest(item.getId(),75,"code"));
        String coding="stage9-coding-"+UUID.randomUUID(),research="stage9-research-"+UUID.randomUUID();
        scheduler.heartbeat(new WorkerHeartbeatRequest(coding,1,0));
        scheduler.heartbeat(new WorkerHeartbeatRequest(research,1,0));
        capabilityDispatch.setWorker(coding,new WorkerCapabilityRequest(Set.of("coding","tests"),Set.of("code")));
        capabilityDispatch.setWorker(research,new WorkerCapabilityRequest(Set.of("research"),Set.of("research")));
        capabilityDispatch.setRequirement(item.getId(),new WorkRequirementRequest(Set.of("coding"),"code"));
        CapabilityDispatchResult result=capabilityDispatch.dispatch(30);
        assertThat(result.dispatched()).isTrue();
        assertThat(result.workerId()).isEqualTo(coding);
        assertThat(result.workItem().getId()).isEqualTo(item.getId());
    }

    @Test
    void benchmarkPackPersistsEvaluationEvidence(){
        BenchmarkRunResult result=benchmarks.record(new BenchmarkRunRequest("coding-core","agent","backend-engineer","implement-feature",true,0,420,0,94,0));
        assertThat(result.verdict()).isEqualTo("PASS");
        assertThat(result.evaluation().getOverallScore()).isGreaterThanOrEqualTo(65);
    }

    @Test
    void marketConsensusFailsClosedOnDisagreementAndAdvancedBacktestIncludesCosts(){
        List<MarketBar> bars=bars(90,100);
        market.ingest(new MarketDataSnapshot("S9CONSUSDT","source-a",Instant.now(),bars));
        market.ingest(new MarketDataSnapshot("S9CONSUSDT","source-b",Instant.now(),scale(bars,1.001)));
        MarketConsensus consensus=market.consensus("S9CONSUSDT",2,60,15,1.0);
        assertThat(consensus.sources()).containsExactlyInAnyOrder("source-a","source-b");
        assertThat(consensus.maxPriceDivergencePct()).isLessThan(1.0);

        market.ingest(new MarketDataSnapshot("S9BADUSDT","source-a",Instant.now(),bars));
        market.ingest(new MarketDataSnapshot("S9BADUSDT","source-b",Instant.now(),scale(bars,1.20)));
        assertThatThrownBy(()->market.consensus("S9BADUSDT",2,60,15,1.0)).isInstanceOf(IllegalStateException.class).hasMessageContaining("disagree");

        AdvancedBacktestResultEntity result=advancedBacktests.run(new AdvancedBacktestRequest("S9CONSUSDT",bars,4,12,10000,5,2,2,0.02,4));
        assertThat(result.getWalkForwardWindows()).isGreaterThanOrEqualTo(2);
        assertThat(result.getTotalCosts()).isGreaterThan(0);
        assertThat(result.getRegimeSummary()).contains("TREND_");
    }

    @Test
    void consensusPaperExecutionStillRequiresAcceptedAnalysisAndRiskOfficer(){
        String symbol="S9PAPERUSDT";
        List<MarketBar> bars=bars(80,120);
        market.ingest(new MarketDataSnapshot(symbol,"source-a",Instant.now(),bars));
        market.ingest(new MarketDataSnapshot(symbol,"source-b",Instant.now(),scale(bars,1.0005)));
        trading.setPolicy(new TradingRiskPolicyRequest(.5,2,3,1.5));
        paperRisk.update(new PaperRiskPolicyRequest(10,10,100,100,120));
        double current=bars.getLast().close();
        TradeSignalEntity signal=trading.analyze(new TradeAnalysisRequest(symbol,current,current*.98,current*1.06,95,90,1.0,90d,10000));
        PaperExecutionResult rejected=stage9Paper.openConsensus(signal.getId(),symbol,2,60,15,1.0);
        assertThat(rejected.executed()).isFalse();
        trading.accept(signal.getId());
        PaperExecutionResult opened=stage9Paper.openConsensus(signal.getId(),symbol,2,60,15,1.0);
        assertThat(opened.executed()).isTrue();
        assertThat(opened.mode()).isEqualTo("PAPER_ONLY");
        PaperEquitySnapshotEntity mark=portfolio.mark(2,60,15,1.0);
        assertThat(mark.getOpenPositions()).isPositive();
        assertThat(mark.getEquity()).isPositive();
        assertThat(TestnetExchangePlan.current().liveMoneyAllowed()).isFalse();
        assertThat(TestnetExchangePlan.current().withdrawalAllowed()).isFalse();
    }

    private List<MarketBar> bars(int count,double start){List<MarketBar> out=new ArrayList<>();Instant base=Instant.now().minusSeconds(count*60L);double price=start;for(int i=0;i<count;i++){price*=1+(i%9<6?.0015:-.0007);double open=price*.999,close=price,high=Math.max(open,close)*1.002,low=Math.min(open,close)*.998;out.add(new MarketBar(base.plusSeconds(i*60L),open,high,low,close,1000+i));}return List.copyOf(out);}
    private List<MarketBar> scale(List<MarketBar> bars,double factor){return bars.stream().map(b->new MarketBar(b.time(),b.open()*factor,b.high()*factor,b.low()*factor,b.close()*factor,b.volume())).toList();}
}
