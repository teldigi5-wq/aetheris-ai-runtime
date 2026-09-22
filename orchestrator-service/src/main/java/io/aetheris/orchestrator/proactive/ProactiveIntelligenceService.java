package io.aetheris.orchestrator.proactive;

import io.aetheris.orchestrator.approval.*;
import io.aetheris.orchestrator.model.*;
import io.aetheris.orchestrator.notification.*;
import io.aetheris.orchestrator.runtime.*;
import io.aetheris.orchestrator.task.*;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class ProactiveIntelligenceService {
 private final TaskService tasks; private final DurableWorkQueueService queue; private final ProviderReliabilityService providers; private final ApprovalService approvals; private final NotificationService notifications;
 public ProactiveIntelligenceService(TaskService tasks,DurableWorkQueueService queue,ProviderReliabilityService providers,ApprovalService approvals,NotificationService notifications){this.tasks=tasks;this.queue=queue;this.providers=providers;this.approvals=approvals;this.notifications=notifications;}
 public ProactiveScanResult scan(){int inspected=0,created=0;for(TaskEntity t:tasks.recent()){inspected++;if(t.getState()==TaskState.FAILED){notifications.publish("task-failed:"+t.getId(),"TASK_FAILED",NotificationSeverity.HIGH,"Task failed: "+t.getTitle(),"Aetheris recorded a failed task. Review evidence and retry/rollback policy before continuing.",t.getId().toString(),true);created++;}else if(t.getState()==TaskState.AWAITING_APPROVAL){notifications.publish("task-approval:"+t.getId(),"APPROVAL_REQUIRED",NotificationSeverity.WARNING,"Owner approval required: "+t.getTitle(),"A task is waiting for an owner decision.",t.getId().toString(),true);created++;}}
 for(WorkItemEntity w:queue.recent()){inspected++;if(w.getState()==WorkItemState.FAILED){notifications.publish("work-failed:"+w.getId(),"WORK_FAILED",NotificationSeverity.HIGH,"Background work exhausted retries","A durable work item failed after its retry policy.",w.getId().toString(),true);created++;}}
 for(ProviderHealthSnapshot p:providers.snapshots()){inspected++;if(p.circuitOpen()){notifications.publish("provider-circuit:"+p.providerId()+":"+p.circuitOpenUntil(),"PROVIDER_CIRCUIT",NotificationSeverity.WARNING,"Model provider circuit open: "+p.providerId(),"Adaptive routing will avoid this provider until its circuit cooldown expires.",p.providerId(),false);created++;}}
 for(ApprovalEntity a:approvals.pending()){inspected++;notifications.publish("approval:"+a.getId(),"APPROVAL_REQUIRED",NotificationSeverity.WARNING,"Pending owner approval: "+a.getActionType(),"A consequential action is paused until you approve or reject it.",a.getId().toString(),true);created++;}
 return new ProactiveScanResult(inspected,created,Instant.now());}
}
