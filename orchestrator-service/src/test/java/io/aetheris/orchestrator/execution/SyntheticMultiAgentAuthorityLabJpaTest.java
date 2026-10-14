package io.aetheris.orchestrator.execution;

import io.aetheris.orchestrator.execution.SignedSandboxJobGate.JobClaims;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.Profile;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.SignedJob;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.TrustedContext;
import io.aetheris.orchestrator.task.TaskControlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** No Spring multi-agent service, tool or real process is registered. */
@SpringBootTest(properties = "aetheris.memory.retention-sweep-ms=3600000")
class SyntheticMultiAgentAuthorityLabJpaTest {
    @Autowired SyntheticAgentQuotaRepository quotas;
    @Autowired SyntheticAgentClaimRepository claims;
    @Autowired SandboxWorkerFenceRepository fences;
    @Autowired SandboxJobReservationRepository reservations;
    @Autowired PlatformTransactionManager transactions;
    @Autowired TaskControlService taskControl;

    private record Job(String worker, String agent, SignedJob signed) {}
    private static final class Fixture {
        final String owner="ciOwner"+UUID.randomUUID().toString().replace("-","");
        final String project="ciProject"+UUID.randomUUID().toString().replace("-","");
        final AtomicBoolean emergency = new AtomicBoolean(false);
        final Map<UUID,SyntheticMultiAgentAuthorityLab.AgentGrant> grants =
                new ConcurrentHashMap<>();
        final KeyPair signer;
        final DurableSandboxAdmissionLab admission;
        final SyntheticMultiAgentAuthorityLab lab;
        final SyntheticAgentQuotaRepository quotas;
        final SandboxWorkerFenceRepository fences;

        Fixture(SyntheticMultiAgentAuthorityLabJpaTest t) throws Exception {
            quotas=t.quotas;fences=t.fences;
            signer=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            quotas.saveAndFlush(new SyntheticAgentQuotaEntity(owner,project));
            admission=new DurableSandboxAdmissionLab(t.fences,t.reservations,
                    t.transactions,signer.getPublic(),Clock.systemUTC(),
                    task -> {
                        var grant=grants.get(task);
                        return grant==null?null:grant.trusted();
                    });
            var verifier=new WorkerSideFenceVerifierLab(t.fences,t.reservations,
                    t.transactions,signer.getPublic(),Clock.systemUTC(),
                    task -> {
                        var grant=grants.get(task);
                        return grant==null?null:grant.trusted();
                    });
            lab=new SyntheticMultiAgentAuthorityLab(
                    t.quotas,t.claims,t.fences,t.reservations,
                    verifier,t.transactions,Clock.systemUTC(),
                    grants::get,emergency::get);
        }
        Job prepare(String agent,String worker) throws Exception {
            if(fences.findById(worker).isEmpty()) {
                var f=new SandboxWorkerFenceEntity(worker);
                f.armSyntheticFixture();
                fences.saveAndFlush(f);
            }
            long now=Instant.now().getEpochSecond();
            var c=new JobClaims(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                    owner,project,worker,"approved_"+UUID.randomUUID().toString().replace("-",""),
                    Profile.SYNTHETIC_SLEEP,now-1,now+45,500,128,32);
            grants.put(c.taskId(),new SyntheticMultiAgentAuthorityLab.AgentGrant(
                    c.taskId(),agent,owner,project,worker,c.approvalGrantId()));
            var signature=Signature.getInstance("Ed25519");
            signature.initSign(signer.getPrivate());
            signature.update(SignedSandboxJobGate.canonical(c));
            SignedJob signed=new SignedJob(c,signature.sign());
            assertThat(admission.reserve(signed)).isEqualTo(c.jobId());
            return new Job(worker,agent,signed);
        }
        SyntheticAgentQuotaEntity quota() {
            return quotas.findById(SyntheticAgentQuotaEntity.key(owner,project)).orElseThrow();
        }
    }

    @Test
    void twoDistinctAgentsShareOneAtomicOwnerProjectResourceLimit() throws Exception {
        Fixture f=new Fixture(this);
        Job research=f.prepare("research_agent","workerA");
        Job cybersecurity=f.prepare("cyber_agent","workerB");
        Job third=f.prepare("design_agent","workerC");
        assertThat(f.lab.reserveForAgent(research.signed(),research.agent()))
                .isEqualTo(research.signed().claims().jobId());
        assertThat(f.lab.reserveForAgent(cybersecurity.signed(),cybersecurity.agent()))
                .isEqualTo(cybersecurity.signed().claims().jobId());
        assertThatThrownBy(() -> f.lab.reserveForAgent(third.signed(),third.agent()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("MULTIAGENT_SHARED_QUOTA_EXCEEDED");
        assertThat(f.quota().getJobs()).isEqualTo(2);
        assertThat(f.quota().getCpuMilli()).isEqualTo(1000);
        assertThat(f.quota().getMemoryMiB()).isEqualTo(256);
        assertThat(f.quota().getProcessCount()).isEqualTo(64);
        assertThat(f.lab.inspectExisting(research.signed().claims().jobId()).mustTerminate())
                .isFalse();
    }

    @Test
    void crossAgentImpersonationNeverConsumesResources() throws Exception {
        Fixture f=new Fixture(this);
        Job target=f.prepare("research_agent","workerA");
        assertThatThrownBy(() -> f.lab.reserveForAgent(target.signed(),"cyber_agent"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("MULTIAGENT_AGENT_OR_OWNER_SCOPE_DENIED");
        assertThat(f.quota().getJobs()).isZero();
        assertThat(claims.findById(target.signed().claims().jobId())).isEmpty();
        assertThat(f.lab.reserveForAgent(target.signed(),"research_agent"))
                .isEqualTo(target.signed().claims().jobId());
    }

    @Test
    void revokedTrustedAgentGrantAndForeignSignatureFailClosed() throws Exception {
        Fixture f=new Fixture(this);
        Job target=f.prepare("research_agent","workerA");
        var untrusted=new SignedJob(target.signed().claims(),new byte[64]);
        assertThatThrownBy(() -> f.lab.reserveForAgent(untrusted,"research_agent"))
                .isInstanceOf(SecurityException.class);
        assertThat(f.quota().getJobs()).isZero();
        f.lab.reserveForAgent(target.signed(),"research_agent");
        f.grants.remove(target.signed().claims().taskId());
        var review=f.lab.inspectExisting(target.signed().claims().jobId());
        assertThat(review.mustTerminate()).isTrue();
        assertThat(review.reason()).isEqualTo("MULTIAGENT_REVOKED_AGENT_GRANT");
    }

    @Test
    void sameSignedJobCannotDoubleChargeOrTransferAgentOwnership() throws Exception {
        Fixture f=new Fixture(this);
        Job job=f.prepare("research_agent","workerA");
        f.lab.reserveForAgent(job.signed(),job.agent());
        assertThatThrownBy(() -> f.lab.reserveForAgent(job.signed(),job.agent()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("MULTIAGENT_SINGLE_USE_CLAIM");
        assertThat(f.quota().getJobs()).isEqualTo(1);
        assertThat(claims.findById(job.signed().claims().jobId()).orElseThrow().getAgentId())
                .isEqualTo("research_agent");
    }

    @Test
    void sharedScopeStopPermanentlyFencesExistingAgentsAndNewClaims() throws Exception {
        Fixture f=new Fixture(this);
        Job a=f.prepare("research_agent","workerA");
        Job b=f.prepare("cyber_agent","workerB");
        f.lab.reserveForAgent(a.signed(),a.agent());
        f.lab.latchOwnerProjectStop(f.owner,f.project);
        assertThat(f.quota().isStopped()).isTrue();
        assertThat(f.lab.inspectExisting(a.signed().claims().jobId()).mustTerminate()).isTrue();
        assertThatThrownBy(() -> f.lab.reserveForAgent(b.signed(),b.agent()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("AGENT_SCOPE_STOP_LATCHED");
        assertThat(f.quota().getJobs()).isEqualTo(1); // still charged; no unverified release
    }

    @Test
    void durableWorkerStopAndVolatileEmergencyBothDenySyntheticAgentContinuation()
            throws Exception {
        Fixture f=new Fixture(this);
        Job a=f.prepare("research_agent","workerA");
        Job b=f.prepare("cyber_agent","workerB");
        f.lab.reserveForAgent(a.signed(),a.agent());
        f.emergency.set(true);
        assertThat(f.lab.inspectExisting(a.signed().claims().jobId()).mustTerminate()).isTrue();
        assertThatThrownBy(() -> f.lab.reserveForAgent(b.signed(),b.agent()))
                .isInstanceOf(SecurityException.class);
        f.emergency.set(false);
        assertThat(f.admission.stopWorker("workerA").epoch()).isEqualTo(2);
        assertThat(f.lab.inspectExisting(a.signed().claims().jobId()).mustTerminate()).isTrue();
        assertThat(f.quota().getJobs()).isEqualTo(1); // STOP is not proof of child exit
    }

    @Test
    @EnabledIfEnvironmentVariable(named="AETHERIS_STAGE45_MULTIAGENT_PG_CI",matches="true")
    void competingAgentClaimsCannotOverbookSharedPostgresQuota() throws Exception {
        Fixture f=new Fixture(this);
        Job a=f.prepare("research_agent","workerA");
        Job b=f.prepare("security_agent","workerB");
        Job c=f.prepare("design_agent","workerC");
        var pool=java.util.concurrent.Executors.newFixedThreadPool(3);
        try {
            var ready=new java.util.concurrent.CountDownLatch(3);
            var start=new java.util.concurrent.CountDownLatch(1);
            var futures=new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (Job j : java.util.List.of(a,b,c)) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(5,java.util.concurrent.TimeUnit.SECONDS))
                        throw new AssertionError("Synthetic concurrency barrier expired");
                    try {
                        f.lab.reserveForAgent(j.signed(),j.agent());
                        return true;
                    } catch (RuntimeException denied) { return false; }
                }));
            }
            assertThat(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int accepted=0;
            for (var future : futures)
                if (future.get(12,java.util.concurrent.TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isBetween(1,2);
            assertThat(f.quota().getJobs()).isEqualTo(accepted);
            assertThat(f.quota().getCpuMilli()).isEqualTo(accepted*500);
            assertThat(f.quota().getMemoryMiB()).isEqualTo(accepted*128);
            assertThat(f.quota().getProcessCount()).isEqualTo(accepted*32);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void existingTaskControlEmergencyStateCanBeReadWithoutEnablingExecution() throws Exception {
        Fixture f=new Fixture(this);
        Job job=f.prepare("research_agent","workerA");
        var realStopBridge=new SyntheticMultiAgentAuthorityLab(
                quotas,claims,fences,reservations,
                new WorkerSideFenceVerifierLab(fences,reservations,transactions,
                        f.signer.getPublic(),Clock.systemUTC(),
                        task -> {
                            var grant=f.grants.get(task);
                            return grant==null?null:grant.trusted();
                        }),
                transactions,Clock.systemUTC(),f.grants::get,
                taskControl::isEmergencyStopActive);
        try {
            taskControl.engage("synthetic security regression");
            assertThatThrownBy(() -> realStopBridge.reserveForAgent(job.signed(),job.agent()))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("MULTIAGENT_EMERGENCY_STOP");
        } finally {
            taskControl.release("restore test-only service flag");
        }
        assertThat(f.quota().getJobs()).isZero();
    }
}
