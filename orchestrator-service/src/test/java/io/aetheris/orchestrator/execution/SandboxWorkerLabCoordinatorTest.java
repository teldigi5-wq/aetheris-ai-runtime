package io.aetheris.orchestrator.execution;

import io.aetheris.orchestrator.execution.SignedSandboxJobGate.JobClaims;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.Profile;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.SignedJob;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.TrustedContext;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** No Docker, host commands, network or real approvals. Synthetic unit lab. */
class SandboxWorkerLabCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-10-14T09:00:00Z");
    private static final Clock TIME = Clock.fixed(NOW, ZoneOffset.UTC);

    private static KeyPair signingKeys() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static JobClaims claims(String owner) {
        long now = NOW.getEpochSecond();
        return new JobClaims(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                owner, "projectA", "workerA", "approvalA", Profile.SYNTHETIC_SLEEP,
                now - 1, now + 20, 500, 128, 32);
    }

    private static TrustedContext trusted(JobClaims c) {
        return new TrustedContext(c.taskId(), c.ownerId(), c.projectId(),
                c.workerId(), c.approvalGrantId());
    }

    private static SignedJob signed(KeyPair pair, JobClaims c) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(pair.getPrivate());
        signature.update(SignedSandboxJobGate.canonical(c));
        return new SignedJob(c, signature.sign());
    }

    private static final class FakeHandle implements SandboxWorkerLabCoordinator.FixedProfileBackend.RunningHandle {
        boolean alive = true;
        boolean refuseStop;
        int terminationCalls;

        @Override public void terminateTree() {
            terminationCalls++;
            if (!refuseStop) alive = false;
        }
        @Override public boolean isRunning() { return alive; }
    }

    private static final class Fixture {
        final KeyPair issuer;
        final AtomicBoolean stopSignal = new AtomicBoolean();
        final Map<UUID, TrustedContext> authorizations = new HashMap<>();
        final Map<UUID, FakeHandle> running = new HashMap<>();
        final AtomicInteger launches = new AtomicInteger();
        final SignedSandboxJobGate gate;
        final SandboxWorkerLabCoordinator coordinator;

        Fixture() throws Exception {
            this(signingKeys());
        }

        Fixture(KeyPair pair) {
            issuer = pair;
            gate = new SignedSandboxJobGate(pair.getPublic(), TIME);
            coordinator = new SandboxWorkerLabCoordinator(
                    gate,
                    taskId -> {
                        TrustedContext ctx = authorizations.get(taskId);
                        if (ctx == null) throw new SecurityException("APPROVAL_NOT_FOUND");
                        return ctx;
                    },
                    id -> {
                        launches.incrementAndGet();
                        FakeHandle h = new FakeHandle();
                        running.put(id, h);
                        return h;
                    },
                    stopSignal::get);
        }

        SignedJob approved(JobClaims c) throws Exception {
            authorizations.put(c.taskId(), trusted(c));
            return signed(issuer, c);
        }
    }

    @Test
    void validOwnerBoundSignedGrantDispatchesExactlyOneFixedLabProfile() throws Exception {
        var f = new Fixture();
        var c = claims("ownerA");
        var job = f.approved(c);
        assertThat(f.coordinator.dispatch(job)).isEqualTo(c.jobId());
        assertThat(f.coordinator.activeCount()).isEqualTo(1);
        assertThat(f.launches.get()).isEqualTo(1);
        assertThatThrownBy(() -> f.coordinator.dispatch(job))
                .isInstanceOf(SecurityException.class).hasMessageContaining("REPLAY");
        assertThat(f.launches.get()).isEqualTo(1);

        assertThatThrownBy(() -> f.coordinator.completed(c.jobId()))
                .isInstanceOf(SecurityException.class).hasMessageContaining("STILL_RUNNING");
        f.running.get(c.jobId()).alive = false;
        f.coordinator.completed(c.jobId());
        assertThat(f.coordinator.activeCount()).isZero();
        assertThatThrownBy(() -> f.coordinator.dispatch(job))
                .isInstanceOf(SecurityException.class).hasMessageContaining("REPLAY");
    }

    @Test
    void alteredWorkerOwnerProjectOrGrantNeverLaunchesBackend() throws Exception {
        var f = new Fixture();
        var c = claims("ownerA");
        f.authorizations.put(c.taskId(), new TrustedContext(
                c.taskId(), "ownerB", c.projectId(), c.workerId(), c.approvalGrantId()));
        var request = signed(f.issuer, c);
        assertThatThrownBy(() -> f.coordinator.dispatch(request))
                .isInstanceOf(SecurityException.class).hasMessageContaining("OWNER_SCOPE_DENIED");
        assertThat(f.launches.get()).isZero();
        f.authorizations.put(c.taskId(), new TrustedContext(
                c.taskId(), c.ownerId(), "differentProject", c.workerId(), c.approvalGrantId()));
        assertThatThrownBy(() -> f.coordinator.dispatch(request))
                .isInstanceOf(SecurityException.class);
        f.authorizations.put(c.taskId(), new TrustedContext(
                c.taskId(), c.ownerId(), c.projectId(), "differentWorker", c.approvalGrantId()));
        assertThatThrownBy(() -> f.coordinator.dispatch(request))
                .isInstanceOf(SecurityException.class);
        f.authorizations.put(c.taskId(), new TrustedContext(
                c.taskId(), c.ownerId(), c.projectId(), c.workerId(), "withdrawnApproval"));
        assertThatThrownBy(() -> f.coordinator.dispatch(request))
                .isInstanceOf(SecurityException.class);
        assertThat(f.launches.get()).isZero();
    }

    @Test
    void revokedApprovalDuringReconciliationKillsAffectedJobOnly() throws Exception {
        var f = new Fixture();
        var a = claims("ownerA");
        var b = claims("ownerB");
        f.coordinator.dispatch(f.approved(a));
        f.coordinator.dispatch(f.approved(b));
        assertThat(f.coordinator.activeCount()).isEqualTo(2);

        f.authorizations.remove(a.taskId());
        f.coordinator.reconcile();
        assertThat(f.running.get(a.jobId()).alive).isFalse();
        assertThat(f.running.get(a.jobId()).terminationCalls).isEqualTo(1);
        assertThat(f.running.get(b.jobId()).alive).isTrue();
        assertThat(f.coordinator.activeCount()).isEqualTo(1);
        assertThat(f.coordinator.isStopped()).isFalse();
    }

    @Test
    void emergencyStopRevokesAdmissionsAndTerminatesEntireKnownJobSet() throws Exception {
        var f = new Fixture();
        var a = claims("ownerA");
        var b = claims("ownerB");
        f.coordinator.dispatch(f.approved(a));
        f.coordinator.dispatch(f.approved(b));
        f.stopSignal.set(true);
        f.coordinator.reconcile();
        assertThat(f.coordinator.isStopped()).isTrue();
        assertThat(f.coordinator.activeCount()).isZero();
        assertThat(f.running.get(a.jobId()).alive).isFalse();
        assertThat(f.running.get(b.jobId()).alive).isFalse();
        f.stopSignal.set(false);
        assertThatThrownBy(() -> f.coordinator.dispatch(f.approved(claims("ownerA"))))
                .isInstanceOf(SecurityException.class).hasMessageContaining("STOP_LATCHED");
    }

    @Test
    void backendRefusingToDieNeverReportsSuccessfulStop() throws Exception {
        var f = new Fixture();
        var c = claims("ownerA");
        f.coordinator.dispatch(f.approved(c));
        f.running.get(c.jobId()).refuseStop = true;
        assertThatThrownBy(f.coordinator::stopAll)
                .isInstanceOf(SecurityException.class).hasMessageContaining("STOP_UNCONFIRMED");
        assertThat(f.coordinator.isStopped()).isTrue();
        assertThat(f.coordinator.activeCount()).isEqualTo(1);
        assertThat(f.running.get(c.jobId()).alive).isTrue();
        f.running.get(c.jobId()).refuseStop = false;
        f.coordinator.stopAll();
        assertThat(f.coordinator.activeCount()).isZero();
        assertThat(f.running.get(c.jobId()).alive).isFalse();
    }

    @Test
    void stopAlreadyActiveBeforeDispatchNeverConsumesGrantOrLaunches() throws Exception {
        var f = new Fixture();
        var c = claims("ownerA");
        var job = f.approved(c);
        f.stopSignal.set(true);
        assertThatThrownBy(() -> f.coordinator.dispatch(job))
                .isInstanceOf(SecurityException.class).hasMessageContaining("STOP");
        assertThat(f.launches.get()).isZero();
        assertThat(f.coordinator.isStopped()).isTrue();
    }

    @Test
    void forgedSignatureFailsBeforeInvokingBackend() throws Exception {
        var f = new Fixture();
        var badSigner = signingKeys();
        var c = claims("ownerA");
        f.authorizations.put(c.taskId(), trusted(c));
        assertThatThrownBy(() -> f.coordinator.dispatch(signed(badSigner, c)))
                .isInstanceOf(SecurityException.class).hasMessageContaining("SIGNATURE");
        assertThat(f.launches.get()).isZero();
    }
}
