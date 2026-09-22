package io.aetheris.orchestrator.checkpoint;

import io.aetheris.orchestrator.task.TaskService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ExecutionCheckpointService {
    private final ExecutionCheckpointRepository repository; private final TaskService tasks;
    public ExecutionCheckpointService(ExecutionCheckpointRepository repository, TaskService tasks){this.repository=repository;this.tasks=tasks;}
    public ExecutionCheckpointEntity create(CreateCheckpointRequest request){
        tasks.getRequired(request.taskId());
        return repository.save(new ExecutionCheckpointEntity(UUID.randomUUID(), request.taskId(), request.type().trim(), request.label().trim(),
                request.reference()==null?"":request.reference().trim(), request.metadataJson()==null||request.metadataJson().isBlank()?"{}":request.metadataJson()));
    }
    public List<ExecutionCheckpointEntity> forTask(UUID taskId){tasks.getRequired(taskId);return repository.findTop100ByTaskIdOrderByCreatedAtDesc(taskId);}
    public ExecutionCheckpointEntity getRequired(UUID id){return repository.findById(id).orElseThrow(()->new NoSuchElementException("Unknown checkpoint: "+id));}
}
