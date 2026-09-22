package io.aetheris.orchestrator.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TaskEventJournalService {

    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {};

    private final TaskEventJournalRepository repository;
    private final ObjectMapper objectMapper;

    public TaskEventJournalService(TaskEventJournalRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void append(TaskEvent event) {
        repository.save(new TaskEventJournalEntity(
                UUID.randomUUID(),
                event.taskId(),
                event.timestamp(),
                event.state(),
                event.agentId(),
                event.message(),
                serialize(event.metadata())));
    }

    public List<TaskEvent> history(UUID taskId) {
        List<TaskEventJournalEntity> rows = new ArrayList<>(repository.findTop200ByTaskIdOrderByEventAtDesc(taskId));
        java.util.Collections.reverse(rows);
        return rows.stream().map(this::toEvent).toList();
    }

    private TaskEvent toEvent(TaskEventJournalEntity row) {
        return new TaskEvent(
                row.getTaskId(),
                row.getEventAt(),
                row.getState(),
                row.getAgentId(),
                row.getMessage(),
                deserialize(row.getMetadataJson()));
    }

    private String serialize(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize task-event metadata", exception);
        }
    }

    private Map<String, Object> deserialize(String json) {
        try {
            return objectMapper.readValue(json, METADATA_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize task-event metadata", exception);
        }
    }
}
