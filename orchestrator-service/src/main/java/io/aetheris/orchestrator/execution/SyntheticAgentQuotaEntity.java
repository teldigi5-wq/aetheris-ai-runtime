package io.aetheris.orchestrator.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Stage 45.5.16 synthetic-only owner/project resource ledger.
 * No API, Spring service, command tool or production execution enablement.
 * Caller must hold the pessimistic DB row lock during every mutation.
 */
@Entity
@Table(name = "aetheris_ci_agent_quota")
public class SyntheticAgentQuotaEntity {
    public static final int MAX_JOBS = 2;
    public static final int MAX_CPU_MILLI = 1000;
    public static final int MAX_MEMORY_MIB = 256;
    public static final int MAX_PROCESSES = 64;

    @Id @Column(name = "scope_key", nullable = false, length = 170)
    private String scopeKey;
    @Column(name = "owner_id", nullable = false, length = 80)
    private String ownerId;
    @Column(name = "project_id", nullable = false, length = 80)
    private String projectId;
    @Column(name = "jobs", nullable = false)
    private int jobs;
    @Column(name = "cpu_milli", nullable = false)
    private int cpuMilli;
    @Column(name = "memory_mib", nullable = false)
    private int memoryMiB;
    @Column(name = "process_count", nullable = false)
    private int processCount;
    @Column(name = "stopped", nullable = false)
    private boolean stopped;
    @Version
    private Long version;

    protected SyntheticAgentQuotaEntity() {}

    public SyntheticAgentQuotaEntity(String ownerId, String projectId) {
        this.scopeKey=key(ownerId,projectId);
        this.ownerId=ownerId;
        this.projectId=projectId;
    }

    public static String key(String owner,String project) {
        if (owner==null || !owner.matches("[A-Za-z0-9_-]{1,80}")
                || project==null || !project.matches("[A-Za-z0-9_-]{1,80}"))
            throw new IllegalArgumentException("Invalid scoped quota identifier");
        return owner.length()+":"+owner+":"+project;
    }

    void claim(int cpu,int memory,int pids) {
        if(stopped)throw new SecurityException("AGENT_SCOPE_STOP_LATCHED");
        if(cpu!=500 || memory!=128 || pids!=32)
            throw new SecurityException("AGENT_PROFILE_NOT_FIXED");
        if(jobs>=MAX_JOBS || cpuMilli>MAX_CPU_MILLI-cpu
                || memoryMiB>MAX_MEMORY_MIB-memory
                || processCount>MAX_PROCESSES-pids)
            throw new SecurityException("AGENT_SHARED_QUOTA_EXCEEDED");
        jobs=Math.addExact(jobs,1);
        cpuMilli=Math.addExact(cpuMilli,cpu);
        memoryMiB=Math.addExact(memoryMiB,memory);
        processCount=Math.addExact(processCount,pids);
    }

    void latchStop() {stopped=true;}

    public boolean isStopped() {return stopped;}
    public int getJobs() {return jobs;}
    public int getCpuMilli() {return cpuMilli;}
    public int getMemoryMiB() {return memoryMiB;}
    public int getProcessCount() {return processCount;}
    public String getOwnerId(){return ownerId;}
    public String getProjectId(){return projectId;}
}
