package io.aetheris.orchestrator;

import io.aetheris.orchestrator.career.*;
import io.aetheris.orchestrator.host.*;
import io.aetheris.orchestrator.memory.*;
import io.aetheris.orchestrator.mission.*;
import io.aetheris.orchestrator.model.*;
import io.aetheris.orchestrator.notification.*;
import io.aetheris.orchestrator.planner.*;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.proactive.*;
import io.aetheris.orchestrator.runtime.*;
import io.aetheris.orchestrator.scheduler.*;
import io.aetheris.orchestrator.task.*;
import io.aetheris.orchestrator.trading.*;
import io.aetheris.orchestrator.vault.WindowsDpapiVaultPlan;
import io.aetheris.orchestrator.voice.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class Stage7IntegrationTest {
    @Autowired TaskService tasks; @Autowired DurableWorkQueueService queue; @Autowired SchedulerService scheduler;
    @Autowired MissionService missions; @Autowired MissionPlannerService planner; @Autowired SemanticMemoryService memory;
    @Autowired ProviderRateLimitService rateLimits; @Autowired ProactiveIntelligenceService proactive; @Autowired NotificationService notifications;
    @Autowired VoiceSessionService voice; @Autowired CareerIntelligenceService career; @Autowired TradingIntelligenceService trading;
    @Autowired HostRegistryService hosts; @Autowired HostPairingService pairing; @Autowired HostCommandService hostCommands; @Autowired HostDaemonProtocolService hostProtocol;

    @Test
    void schedulerEnforcesWorkerCapacityAndPriorityDispatch(){
        TaskEntity lowTask=tasks.create(new CreateTaskRequest("low priority","low",OperationMode.BALANCED));
        TaskEntity highTask=tasks.create(new CreateTaskRequest("high priority","high",OperationMode.BALANCED));
        WorkItemEntity low=queue.enqueue(new EnqueueWorkItemRequest(lowTask.getId(),"STAGE7_LOW","{}",2));
        WorkItemEntity high=queue.enqueue(new EnqueueWorkItemRequest(highTask.getId(),"STAGE7_HIGH","{}",2));
        scheduler.schedule(new ScheduleWorkRequest(low.getId(),10,"test")); scheduler.schedule(new ScheduleWorkRequest(high.getId(),90,"test"));
        String worker="stage7-worker-"+UUID.randomUUID();scheduler.heartbeat(new WorkerHeartbeatRequest(worker,1,0));
        SchedulerDispatchResult first=scheduler.dispatch(worker,30);
        assertThat(first.dispatched()).isTrue();assertThat(first.workItem().getId()).isEqualTo(high.getId());
        SchedulerDispatchResult second=scheduler.dispatch(worker,30);assertThat(second.dispatched()).isFalse();assertThat(second.reason()).contains("concurrency");
    }

    @Test
    void missionPlannerReleasesOnlyDependencyReadySteps(){
        MissionSessionView mission=missions.create(new CreateMissionRequest("Stage7 DAG","dependency-aware execution"));
        PlanMissionRequest request=new PlanMissionRequest(OperationMode.BALANCED,List.of(
                new PlanStepRequest("research","Research","collect evidence","research-scientist",80,Set.of()),
                new PlanStepRequest("build","Build","implement from evidence","backend-engineer",70,Set.of("research"))));
        planner.plan(mission.id(),request);List<MissionPlanNodeEntity> released=planner.releaseReady(mission.id());
        assertThat(released).filteredOn(n->n.getStepKey().equals("research")).singleElement().satisfies(n->assertThat(n.getState()).isEqualTo(MissionPlanNodeState.ENQUEUED));
        assertThat(released).filteredOn(n->n.getStepKey().equals("build")).singleElement().satisfies(n->assertThat(n.getState()).isEqualTo(MissionPlanNodeState.BLOCKED));
    }

    @Test
    void localSemanticMemoryHonoursProtectedFilter(){
        String ns="stage7-"+UUID.randomUUID();KnowledgeNodeEntity publicNode=memory.upsert(new UpsertKnowledgeRequest(MemoryScope.PROJECT,ns,"firebase-path","FloodGuard live data uses a Firebase realtime database path",Set.of("floodguard","firebase"),false));
        memory.upsert(new UpsertKnowledgeRequest(MemoryScope.PROJECT,ns,"private-note","private firebase deployment detail",Set.of("firebase"),true));
        List<MemorySearchResult> results=memory.search("firebase floodguard",MemoryScope.PROJECT,ns,false,10);
        assertThat(results).extracting(r->r.node().getId()).contains(publicNode.getId());assertThat(results).allMatch(r->!r.node().isProtectedData());
    }

    @Test
    void providerRateLimitCreatesExplicitBackpressureWindow(){
        String provider="stage7-rate-"+UUID.randomUUID();ProviderRateLimitSnapshot snapshot=rateLimits.mark(provider,120,0L,null,"429 synthetic rate limit");
        assertThat(snapshot.blocked()).isTrue();assertThat(rateLimits.canAttempt(provider)).isFalse();rateLimits.clear(provider);assertThat(rateLimits.canAttempt(provider)).isTrue();
    }

    @Test
    void proactiveScanCreatesActionableFailureNotification(){
        TaskEntity task=tasks.create(new CreateTaskRequest("Stage7 proactive failure","fail safely",OperationMode.BALANCED));
        tasks.transition(task.getId(),new TaskTransitionRequest(TaskState.PLANNING,"stage7","planning"));tasks.transition(task.getId(),new TaskTransitionRequest(TaskState.RUNNING,"stage7","running"));tasks.transition(task.getId(),new TaskTransitionRequest(TaskState.FAILED,"stage7","synthetic failure"));
        ProactiveScanResult scan=proactive.scan();assertThat(scan.signalsInspected()).isPositive();assertThat(notifications.recent()).anyMatch(n->n.getFingerprint().equals("task-failed:"+task.getId()));
    }

    @Test
    void deterministicVoicePauseDoesNotDependOnModelInference(){
        TaskEntity task=tasks.create(new CreateTaskRequest("Stage7 voice control","voice pause",OperationMode.BALANCED));tasks.transition(task.getId(),new TaskTransitionRequest(TaskState.PLANNING,"stage7","planning"));tasks.transition(task.getId(),new TaskTransitionRequest(TaskState.RUNNING,"stage7","running"));
        VoiceSessionEntity session=voice.start(new StartVoiceSessionRequest(null,task.getId()));VoiceCommandResult result=voice.priority(session.getId(),VoicePriorityCommand.PAUSE);
        assertThat(result.accepted()).isTrue();assertThat(tasks.getRequired(task.getId()).getState()).isEqualTo(TaskState.PAUSED);
    }

    @Test
    void careerAuditProducesRecruiterReadinessEvidence(){
        CareerAuditResult result=career.audit(new CareerAuditRequest("AI Software Engineer",true,true,true,true,3,4,3,3,20));assertThat(result.audit().getReadinessScore()).isGreaterThanOrEqualTo(80);
    }

    @Test
    void highScoreTradeSignalStillRequiresOwnerAcceptanceAndNeverExecutes(){
        trading.setPolicy(new TradingRiskPolicyRequest(.5,2,3,1.5));TradeSignalEntity signal=trading.analyze(new TradeAnalysisRequest("BTCUSDT",100,99,106,95,90,1.0,90d,10000));
        assertThat(signal.getSide()).isEqualTo(TradeSide.LONG);assertThat(signal.getStatus()).isEqualTo(TradeSignalStatus.PENDING_ACCEPTANCE);assertThat(signal.getTakeProfit1()).isGreaterThan(signal.getEntryPrice());assertThat(signal.getStopLoss()).isLessThan(signal.getEntryPrice());assertThat(signal.getLeverage()).isBetween(1,3);
        TradeDecisionResult accepted=trading.accept(signal.getId());assertThat(accepted.signal().getStatus()).isEqualTo(TradeSignalStatus.ACCEPTED_ANALYSIS);assertThat(accepted.executionState()).isEqualTo("NOT_EXECUTED");assertThat(accepted.detail()).contains("no live execution adapter");
    }

    @Test
    void hostDaemonProtocolRejectsReplayOfAcknowledgedEnvelope() throws Exception {
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);KeyPair pair=generator.generateKeyPair();
        String fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pair.getPublic().getEncoded()));String pem="-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder(64,"\n".getBytes(StandardCharsets.UTF_8)).encodeToString(pair.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----";
        HostNodeEntity host=hosts.register(new HostRegistrationRequest("stage7-"+UUID.randomUUID(),"Stage7 simulated host","windows",Set.of("APP_LAUNCH"),fingerprint));HostPairingChallengeResponse challenge=pairing.begin(host.getId());String msg=host.getId()+":"+challenge.challengeId()+":"+challenge.nonce();Signature signature=Signature.getInstance("SHA256withRSA");signature.initSign(pair.getPrivate());signature.update(msg.getBytes(StandardCharsets.UTF_8));pairing.complete(host.getId(),challenge.challengeId(),new CompleteHostPairingRequest(challenge.nonce(),pem,Base64.getEncoder().encodeToString(signature.sign())));pairing.heartbeat(host.getId());
        HostCommandEnvelope envelope=hostCommands.issue(new HostCommandRequest(host.getId(),"APP_LAUNCH","open",Map.of("app","code")));HostCommandReceiptEntity receipt=hostProtocol.simulateOnce(envelope);assertThat(receipt.getStatus()).isEqualTo(HostCommandReceiptStatus.ACKNOWLEDGED);assertThatThrownBy(()->hostProtocol.simulateOnce(envelope)).isInstanceOf(IllegalStateException.class).hasMessageContaining("replay");
    }

    @Test
    void windowsDpapiRemainsAnExplicitFutureHardwareBoundary(){assertThat(WindowsDpapiVaultPlan.planned().implemented()).isFalse();assertThat(WindowsDpapiVaultPlan.planned().requiresPairedWindowsHost()).isTrue();}
}
