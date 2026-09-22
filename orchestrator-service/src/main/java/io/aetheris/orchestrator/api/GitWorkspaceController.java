package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.approval.ApprovalEntity;
import io.aetheris.orchestrator.checkpoint.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/workspace/git")
public class GitWorkspaceController {
    private final GitWorkspaceIsolationService git; public GitWorkspaceController(GitWorkspaceIsolationService git){this.git=git;}
    @PostMapping("/tasks/{taskId}/isolate") public GitWorkspaceIsolationResult isolate(@PathVariable UUID taskId){return git.isolate(taskId);}
    @PostMapping("/checkpoints/{checkpointId}/request-rollback") public ApprovalEntity requestRollback(@PathVariable UUID checkpointId){return git.requestRollback(checkpointId);}
    @PostMapping("/checkpoints/{checkpointId}/rollback") public GitWorkspaceRollbackResult rollback(@PathVariable UUID checkpointId){return git.rollback(checkpointId);}
}
