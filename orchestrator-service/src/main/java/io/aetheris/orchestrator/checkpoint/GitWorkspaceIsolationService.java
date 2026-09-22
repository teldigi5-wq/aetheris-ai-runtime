package io.aetheris.orchestrator.checkpoint;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.approval.*;
import io.aetheris.orchestrator.execution.*;
import io.aetheris.orchestrator.task.TaskService;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class GitWorkspaceIsolationService {
    private final CommandSandboxService commands; private final ExecutionCheckpointService checkpoints; private final ApprovalService approvals; private final TaskService tasks;
    public GitWorkspaceIsolationService(CommandSandboxService commands,ExecutionCheckpointService checkpoints,ApprovalService approvals,TaskService tasks){this.commands=commands;this.checkpoints=checkpoints;this.approvals=approvals;this.tasks=tasks;}
    public GitWorkspaceIsolationResult isolate(UUID taskId){tasks.getRequired(taskId);requirePassed(commands.execute(List.of("git","rev-parse","--is-inside-work-tree"),"",15),"Workspace is not a Git work tree");CommandExecutionResult status=commands.execute(List.of("git","status","--porcelain"),"",15);requirePassed(status,"Unable to inspect Git workspace");if(!status.output().isBlank())throw new IllegalStateException("Workspace has uncommitted changes; isolation refuses to hide or overwrite owner work");String base=output(commands.execute(List.of("git","rev-parse","HEAD"),"",15),"Unable to resolve Git HEAD");String branch="aetheris/task-"+taskId.toString().substring(0,8)+"-"+Instant.now().getEpochSecond();requirePassed(commands.execute(List.of("git","checkout","-b",branch),"",20),"Unable to create isolated task branch");ExecutionCheckpointEntity checkpoint=checkpoints.create(new CreateCheckpointRequest(taskId,"GIT_BRANCH","Pre-write Git isolation",base,"{\"branch\":\""+branch+"\",\"baseCommit\":\""+base+"\"}"));return new GitWorkspaceIsolationResult(checkpoint.getId(),branch,base,"Isolated branch created before agent writes");}
    public ApprovalEntity requestRollback(UUID checkpointId){ExecutionCheckpointEntity checkpoint=checkpoints.getRequired(checkpointId);return approvals.request(new CreateApprovalRequest(checkpoint.getTaskId(),action(checkpointId),"Rollback isolated workspace to checkpoint "+checkpointId,RiskLevel.HIGH));}
    public GitWorkspaceRollbackResult rollback(UUID checkpointId){ExecutionCheckpointEntity checkpoint=checkpoints.getRequired(checkpointId);if(!"GIT_BRANCH".equals(checkpoint.getType()))throw new IllegalArgumentException("Checkpoint is not a Git workspace checkpoint");if(!approvals.hasApproved(checkpoint.getTaskId(),action(checkpointId)))throw new IllegalStateException("Owner approval is required for this exact rollback checkpoint");String branch=output(commands.execute(List.of("git","branch","--show-current"),"",15),"Unable to resolve current Git branch");if(!branch.startsWith("aetheris/task-"))throw new IllegalStateException("Rollback is restricted to an isolated Aetheris task branch");requirePassed(commands.execute(List.of("git","reset","--hard",checkpoint.getReference()),"",30),"Git rollback failed");return new GitWorkspaceRollbackResult(checkpointId,branch,checkpoint.getReference(),"Approved rollback completed on isolated task branch");}
    private String action(UUID id){return "workspace.rollback:"+id;}
    private String output(CommandExecutionResult result,String error){requirePassed(result,error);return result.output().trim();}
    private void requirePassed(CommandExecutionResult result,String error){if(!result.passed())throw new IllegalStateException(error+": "+result.output());}
}
