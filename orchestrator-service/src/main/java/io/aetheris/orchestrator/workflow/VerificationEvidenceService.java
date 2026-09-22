package io.aetheris.orchestrator.workflow;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class VerificationEvidenceService {

    private final VerificationEvidenceRepository repository;

    public VerificationEvidenceService(VerificationEvidenceRepository repository) {
        this.repository = repository;
    }

    public VerificationEvidenceEntity record(UUID workflowId, UUID taskId, String phase, String agentId, String type, boolean passed, String summary, String detail) {
        return repository.save(new VerificationEvidenceEntity(
                UUID.randomUUID(), workflowId, taskId, phase, agentId, type, passed, summary, detail == null ? "" : detail));
    }

    public List<VerificationEvidenceEntity> forWorkflow(UUID workflowId) {
        return repository.findTop100ByWorkflowIdOrderByCreatedAtAsc(workflowId);
    }

    public boolean satisfies(UUID workflowId, List<String> requestedCriteria) {
        List<String> criteria = requestedCriteria == null || requestedCriteria.isEmpty()
                ? List.of("source-readable", "qa-command-passed")
                : requestedCriteria;
        List<VerificationEvidenceEntity> evidence = forWorkflow(workflowId);
        for (String criterion : criteria) {
            boolean satisfied = switch (criterion) {
                case "source-readable" -> evidence.stream().anyMatch(item -> item.isPassed() && item.getEvidenceType().equals("SOURCE_READ"));
                case "qa-command-passed" -> evidence.stream().anyMatch(item -> item.isPassed() && item.getEvidenceType().equals("QA_COMMAND"));
                default -> false;
            };
            if (!satisfied) return false;
        }
        return true;
    }
}
