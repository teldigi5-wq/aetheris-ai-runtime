package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.checkpoint.CreateCheckpointRequest;
import io.aetheris.orchestrator.checkpoint.ExecutionCheckpointEntity;
import io.aetheris.orchestrator.checkpoint.ExecutionCheckpointService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/checkpoints")
public class CheckpointController {
    private final ExecutionCheckpointService checkpoints;
    public CheckpointController(ExecutionCheckpointService checkpoints){this.checkpoints=checkpoints;}
    @PostMapping public ExecutionCheckpointEntity create(@Valid @RequestBody CreateCheckpointRequest request){return checkpoints.create(request);}
    @GetMapping("/task/{taskId}") public List<ExecutionCheckpointEntity> forTask(@PathVariable UUID taskId){return checkpoints.forTask(taskId);}
}
