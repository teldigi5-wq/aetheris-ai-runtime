package io.aetheris.orchestrator.scheduler;
import io.aetheris.orchestrator.runtime.WorkItemEntity;
public record SchedulerDispatchResult(String workerId,boolean dispatched,String reason,SchedulerTicketEntity ticket,WorkItemEntity workItem){}
