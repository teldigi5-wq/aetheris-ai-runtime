package io.aetheris.orchestrator.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Durable per-worker STOP fence for Stage 45.4 lab only.
 * Missing row, stopped row, or unreadable DB always denies new reservations.
 *
 * A newly constructed fence is STOPPED. Only test fixture code in this
 * package may arm synthetic workers. No runtime service can release STOP.
 */
@Entity
@Table(name = "aetheris_sandbox_worker_fence")
public class SandboxWorkerFenceEntity {
    @Id
    @Column(name = "worker_id", nullable = false, length = 80)
    private String workerId;

    @Column(name = "stopped", nullable = false)
    private boolean stopped = true;

    @Column(name = "epoch", nullable = false)
    private long epoch = 1L;

    @Version
    @Column(name = "version")
    private Long version;

    protected SandboxWorkerFenceEntity() {}

    public SandboxWorkerFenceEntity(String workerId) {
        if (workerId == null || !workerId.matches("[A-Za-z0-9_-]{1,80}"))
            throw new IllegalArgumentException("Invalid worker identifier");
        this.workerId = workerId;
    }

    // Synthetic fixture setup ONLY, not part of an agent or application service.
    void armSyntheticFixture() {
        if (epoch != 1L || !stopped)
            throw new IllegalStateException("Synthetic fence can be armed once only");
        stopped = false;
    }

    public void latchStop() {
        if (!stopped) {
            stopped = true;
            epoch = Math.addExact(epoch, 1L);
        }
    }

    public String getWorkerId() { return workerId; }
    public boolean isStopped() { return stopped; }
    public long getEpoch() { return epoch; }
}
