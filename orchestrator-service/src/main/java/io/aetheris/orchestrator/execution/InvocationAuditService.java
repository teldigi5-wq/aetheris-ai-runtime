package io.aetheris.orchestrator.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class InvocationAuditService {

    private final InvocationAuditRepository repository;
    private final ObjectMapper objectMapper;

    public InvocationAuditService(InvocationAuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InvocationAuditEntity start(UUID taskId, String agentId, InvocationKind kind, String targetId, Map<String, ?> metadata) {
        return repository.save(new InvocationAuditEntity(
                UUID.randomUUID(), taskId, agentId, kind, targetId, serialize(metadata)));
    }

    @Transactional
    public InvocationAuditEntity finish(UUID id, InvocationStatus status, String detail, Map<String, ?> metadata) {
        InvocationAuditEntity entry = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Unknown invocation audit entry: " + id));
        entry.finish(status, detail, serialize(metadata));
        return repository.save(entry);
    }

    public List<InvocationAuditEntity> recent() {
        return repository.findTop100ByOrderByStartedAtDesc();
    }

    public List<InvocationAuditEntity> forTask(UUID taskId) {
        return repository.findTop100ByTaskIdOrderByStartedAtDesc(taskId);
    }

    private String serialize(Map<String, ?> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize invocation audit metadata", exception);
        }
    }
}
