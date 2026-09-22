package io.aetheris.orchestrator.scheduler;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record ScheduleWorkRequest(@NotNull UUID workItemId,int priority,String lane){}
