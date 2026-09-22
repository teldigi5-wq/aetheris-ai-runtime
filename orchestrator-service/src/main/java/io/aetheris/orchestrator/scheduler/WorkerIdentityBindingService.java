package io.aetheris.orchestrator.scheduler;

import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.runtime.WorkItemEntity;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class WorkerIdentityBindingService {
    private final WorkerHeartbeatRepository workers;
    private final AgentCatalogService agents;

    public WorkerIdentityBindingService(WorkerHeartbeatRepository workers, AgentCatalogService agents) {
        this.workers = workers;
        this.agents = agents;
    }

    public String requireKnownAgent(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("Specialist agent id is required");
        }
        String normalized = agentId.trim();
        agents.getRequired(normalized);
        return normalized;
    }

    public String knownAgentOrNull(String agentId) {
        if (agentId == null || agentId.isBlank()) return null;
        String normalized = agentId.trim();
        try {
            agents.getRequired(normalized);
            return normalized;
        } catch (NoSuchElementException ignored) {
            return null;
        }
    }

    public void bind(WorkerHeartbeatEntity worker, String requestedAgentId) {
        if (requestedAgentId == null || requestedAgentId.isBlank()) return;
        worker.bindAgent(requireKnownAgent(requestedAgentId));
    }

    public boolean canExecute(WorkItemEntity item, String workerId) {
        if (item.getRequiredAgentId() == null) return true;
        if (workerId == null || workerId.isBlank()) return false;
        return workers.findById(workerId.trim())
                .map(worker -> item.getRequiredAgentId().equals(worker.getAgentId()))
                .orElse(false);
    }

    public void assertCanExecute(WorkItemEntity item, String workerId) {
        if (canExecute(item, workerId)) return;
        String boundAgent = workerId == null || workerId.isBlank()
                ? null
                : workers.findById(workerId.trim()).map(WorkerHeartbeatEntity::getAgentId).orElse(null);
        throw new IllegalStateException("Worker " + workerId
                + " is not bound to required specialist " + item.getRequiredAgentId()
                + " (boundAgent=" + (boundAgent == null ? "none" : boundAgent) + ")");
    }
}
