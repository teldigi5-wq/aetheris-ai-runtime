package io.aetheris.orchestrator.execution;

import io.aetheris.orchestrator.execution.SignedSandboxJobGate.JobClaims;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.SignedJob;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.TrustedContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Stage 45.3 synthetic integration boundary; not registered with Spring,
 * HTTP, the agent registry, a real Docker daemon, or TaskControlService.
 *
 * The sole fixed profile is SYNTHETIC_SLEEP. Only a trusted backend
 * implementation can start work. This class offers synchronous admission
 * plus explicit STOP/reconciliation hooks for a disposable test harness.
 *
 * NOT a deployable worker: no authenticated transport, verified principal,
 * durable replay ledger, automatic STOP listener, crash recovery or secure
 * OS runner implementation is provided by this class.
 */
public final class SandboxWorkerLabCoordinator {

    /**
     * An external server-side, authoritative provider MUST resolve owner,
     * project, worker and previously issued approval by durable task ID.
     * Never implement this by echoing fields from the untrusted SignedJob.
     */
    @FunctionalInterface
    public interface TrustedAuthority {
        TrustedContext requireCurrentGrant(UUID taskId);
    }

    /**
     * Trust-boundary placeholder: the production implementation MUST reside
     * in a separate OS-confined worker. No arbitrary command or environment
     * variables are accepted by this interface.
     */
    public interface FixedProfileBackend {
        RunningHandle launchSyntheticSleep(UUID jobId);

        interface RunningHandle {
            void terminateTree();
            boolean isRunning();
        }
    }

    private record Active(JobClaims claims, FixedProfileBackend.RunningHandle handle) {}

    private final SignedSandboxJobGate gate;
    private final TrustedAuthority authority;
    private final FixedProfileBackend backend;
    private final BooleanSupplier emergencyStop;
    private final Map<UUID, Active> active = new LinkedHashMap<>();
    private boolean stopLatched;

    public SandboxWorkerLabCoordinator(SignedSandboxJobGate gate,
                                       TrustedAuthority authority,
                                       FixedProfileBackend backend,
                                       BooleanSupplier emergencyStop) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.authority = Objects.requireNonNull(authority, "authority");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.emergencyStop = Objects.requireNonNull(emergencyStop, "emergencyStop");
    }

    /**
     * Validation is completed before launching anything. Synchronized with
     * stopAll() within THIS JVM only, including the launch callback.
     */
    public synchronized UUID dispatch(SignedJob signed) {
        if (stopLatched || gate.isStopped()) throw new SecurityException("SANDBOX_STOP_LATCHED");
        if (emergencyStop.getAsBoolean()) {
            stopAll();
            throw new SecurityException("SANDBOX_STOP_SIGNAL_ACTIVE");
        }
        Objects.requireNonNull(signed, "signed");
        JobClaims claims = signed.claims();
        TrustedContext trusted = Objects.requireNonNull(
                authority.requireCurrentGrant(claims.taskId()), "authoritative approval is unavailable");
        UUID id = gate.verifyAndAdmit(signed, trusted);

        FixedProfileBackend.RunningHandle handle = null;
        try {
            if (stopLatched || emergencyStop.getAsBoolean()) {
                stopAll();
                throw new SecurityException("SANDBOX_STOP_SIGNAL_ACTIVE");
            }
            handle = Objects.requireNonNull(
                    backend.launchSyntheticSleep(id), "sandbox backend returned no handle");
            if (!handle.isRunning()) {
                throw new IllegalStateException("SYNTHETIC_BACKEND_NOT_RUNNING");
            }
            active.put(id, new Active(claims, handle));
            if (emergencyStop.getAsBoolean()) {
                stopAll();
                throw new SecurityException("SANDBOX_STOP_SIGNAL_ACTIVE");
            }
            return id;
        } catch (RuntimeException error) {
            gate.complete(id);
            if (handle != null) {
                try {
                    handle.terminateTree();
                    if (handle.isRunning()) {
                        stopAll();
                        throw new SecurityException("SANDBOX_KILL_NOT_CONFIRMED", error);
                    }
                } catch (RuntimeException cleanupError) {
                    stopAll();
                    throw new SecurityException("SANDBOX_CLEANUP_FAILED", cleanupError);
                }
            }
            throw error;
        }
    }

    /**
     * A manually called status reconciliation; NOT a background stop
     * subscription. Any observed revoked/missing grant kills its job.
     */
    public synchronized void reconcile() {
        if (stopLatched) return;
        if (gate.isStopped() || emergencyStop.getAsBoolean()) {
            stopAll();
            return;
        }
        for (var entry : Map.copyOf(active).entrySet()) {
            JobClaims c = entry.getValue().claims();
            boolean authorized;
            try {
                TrustedContext current = authority.requireCurrentGrant(c.taskId());
                authorized = current != null
                        && Objects.equals(current.taskId(), c.taskId())
                        && Objects.equals(current.ownerId(), c.ownerId())
                        && Objects.equals(current.projectId(), c.projectId())
                        && Objects.equals(current.workerId(), c.workerId())
                        && Objects.equals(current.approvalGrantId(), c.approvalGrantId());
            } catch (RuntimeException denied) {
                authorized = false;
            }
            if (!authorized) terminateSingle(entry.getKey());
        }
    }

    /**
     * No acceptance after STOP. The call *attempts* process-tree termination
     * through a trusted backend; it refuses to silently mark STOP successful
     * if any backend reports still alive or throws.
     */
    public synchronized void stopAll() {
        stopLatched = true;
        gate.engageStop();
        RuntimeException failure = null;
        for (UUID id : java.util.List.copyOf(active.keySet())) {
            try {
                terminateSingle(id);
            } catch (RuntimeException problem) {
                if (failure == null) failure = new SecurityException("SANDBOX_STOP_UNCONFIRMED", problem);
                else failure.addSuppressed(problem);
            }
        }
        if (failure != null) throw failure;
    }

    private void terminateSingle(UUID id) {
        Active job = active.get(id);
        if (job == null) return;
        job.handle().terminateTree();
        if (job.handle().isRunning()) {
            throw new SecurityException("SANDBOX_PROCESS_TREE_STILL_RUNNING");
        }
        active.remove(id);
        gate.complete(id);
    }

    public synchronized void completed(UUID id) {
        if (id == null) throw new IllegalArgumentException("job ID required");
        Active job = active.get(id);
        if (job == null) return;
        if (job.handle().isRunning()) {
            throw new SecurityException("SANDBOX_JOB_STILL_RUNNING");
        }
        active.remove(id);
        gate.complete(id);
    }

    public synchronized boolean isStopped() { return stopLatched || gate.isStopped(); }

    public synchronized int activeCount() { return active.size(); }
}
