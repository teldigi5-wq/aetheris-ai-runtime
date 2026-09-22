package io.aetheris.orchestrator;

import io.aetheris.orchestrator.checkpoint.*;
import io.aetheris.orchestrator.host.*;
import io.aetheris.orchestrator.mission.*;
import io.aetheris.orchestrator.model.*;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.runtime.*;
import io.aetheris.orchestrator.task.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class Stage6IntegrationTest {
    @Autowired HostRegistryService hosts;
    @Autowired HostPairingService pairing;
    @Autowired HostCommandService hostCommands;
    @Autowired ProviderReliabilityService reliability;
    @Autowired ModelArenaService arena;
    @Autowired MissionService missions;
    @Autowired TaskService tasks;
    @Autowired ExecutionCheckpointService checkpoints;
    @Autowired GitWorkspaceIsolationService gitWorkspace;

    @Test
    void hostPairingRequiresPrivateKeyProofAndRevocationBlocksExecution() throws Exception {
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); KeyPair pair=generator.generateKeyPair();
        String fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pair.getPublic().getEncoded()));
        String pem="-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder(64,"\n".getBytes(StandardCharsets.UTF_8)).encodeToString(pair.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----";
        HostNodeEntity host=hosts.register(new HostRegistrationRequest("stage6-"+UUID.randomUUID(),"Stage6 simulated Windows host","windows",Set.of("PC_TELEMETRY","APP_LAUNCH"),fingerprint));
        assertThat(host.getStatus()).isEqualTo(HostStatus.UNPAIRED);
        HostPairingChallengeResponse challenge=pairing.begin(host.getId());
        String message=host.getId()+":"+challenge.challengeId()+":"+challenge.nonce();
        Signature signature=Signature.getInstance("SHA256withRSA"); signature.initSign(pair.getPrivate()); signature.update(message.getBytes(StandardCharsets.UTF_8));
        HostNodeEntity paired=pairing.complete(host.getId(),challenge.challengeId(),new CompleteHostPairingRequest(challenge.nonce(),pem,Base64.getEncoder().encodeToString(signature.sign())));
        assertThat(paired.getStatus()).isEqualTo(HostStatus.OFFLINE);
        HostNodeEntity online=pairing.heartbeat(host.getId()); assertThat(online.getStatus()).isEqualTo(HostStatus.ONLINE);
        HostCommandEnvelope envelope=hostCommands.issue(new HostCommandRequest(host.getId(),"APP_LAUNCH","open",Map.of("app","code")));
        assertThat(envelope.mode()).isEqualTo("SIMULATION");
        assertThat(hostCommands.simulate(envelope)).containsEntry("status","SIMULATED");
        pairing.revoke(host.getId());
        assertThatThrownBy(()->hostCommands.simulatedTelemetry(host.getId())).isInstanceOf(IllegalStateException.class).hasMessageContaining("not paired and online");
    }

    @Test
    void expiredWorkerLeaseCanBeRecoveredWithoutPretendingSuccess() {
        WorkItemEntity item=new WorkItemEntity(UUID.randomUUID(),UUID.randomUUID(),"stage6-test","{}",3);
        item.claim("worker-a",5);
        Instant future=Instant.now().plusSeconds(10);
        assertThat(item.leaseExpired(future)).isTrue();
        item.recoverExpiredLease(future);
        assertThat(item.getState()).isEqualTo(WorkItemState.RETRY_WAIT);
        assertThat(item.getLastError()).contains("lease expired");
        assertThat(item.getLeaseOwner()).isNull();
    }

    @Test
    void providerCircuitOpensAfterRepeatedFailuresAndArenaRecordsEvidence() {
        String provider="stage6-provider-"+UUID.randomUUID();
        reliability.failure(provider,"one"); reliability.failure(provider,"two"); reliability.failure(provider,"three");
        ProviderHealthSnapshot snapshot=reliability.snapshot(provider);
        assertThat(snapshot.circuitOpen()).isTrue(); assertThat(reliability.canAttempt(provider)).isFalse();
        ModelArenaMeasurementEntity measurement=arena.record(new ModelArenaRecordRequest(provider,"test-model","unit-benchmark",false,42,78.0,"synthetic benchmark"));
        assertThat(measurement.getQualityScore()).isEqualTo(78.0);
    }

    @Test
    void missionSessionLinksConversationTasksAndGlobalLiveStream() {
        TaskEntity task=tasks.create(new CreateTaskRequest("Stage6 mission task","Mission-linked work",OperationMode.BALANCED));
        MissionSessionView mission=missions.create(new CreateMissionRequest("Build Syntra shell","Coordinate Stage6 desktop-shell preparation"));
        mission=missions.attach(mission.id(),task.getId());
        missions.message(mission.id(),new MissionMessageRequest(MissionSpeaker.OWNER,"Keep execution simulation-only until the PC arrives",task.getId()));
        MissionSessionView paused=missions.status(mission.id(),MissionStatus.PAUSED);
        assertThat(paused.taskIds()).contains(task.getId());
        assertThat(paused.liveStreamPath()).isEqualTo("/api/orchestrator/live/events");
        assertThat(missions.messages(mission.id())).singleElement().satisfies(m->assertThat(m.getContent()).contains("simulation-only"));
    }

    @Test
    void gitRollbackFailsClosedWithoutExactOwnerApproval() {
        TaskEntity task=tasks.create(new CreateTaskRequest("Stage6 rollback gate","Validate rollback approval",OperationMode.BALANCED));
        ExecutionCheckpointEntity checkpoint=checkpoints.create(new CreateCheckpointRequest(task.getId(),"GIT_BRANCH","Synthetic Stage6 checkpoint","0123456789abcdef","{}"));
        assertThatThrownBy(()->gitWorkspace.rollback(checkpoint.getId())).isInstanceOf(IllegalStateException.class).hasMessageContaining("Owner approval");
    }
}
