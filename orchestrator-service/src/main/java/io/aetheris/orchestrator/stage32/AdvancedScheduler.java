package io.aetheris.orchestrator.stage32;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class AdvancedScheduler {
    private static final Duration DEADLINE_WINDOW = Duration.ofMinutes(5);

    public SchedulerDecision decide(AutomationWork work, ResourceBudget budget, RetryBudget retry,
                                    EmergencyMode emergencyMode, Instant now) {
        Objects.requireNonNull(work); Objects.requireNonNull(budget); Objects.requireNonNull(retry);
        Objects.requireNonNull(emergencyMode); Objects.requireNonNull(now);

        if (emergencyMode == EmergencyMode.STOP) return new SchedulerDecision(RuntimeState.CANCELLED, false, "emergency-stop", now);
        if ((emergencyMode == EmergencyMode.PAUSE || emergencyMode == EmergencyMode.TAKE_CONTROL) && !work.emergencyWork()) {
            return new SchedulerDecision(RuntimeState.PAUSED, false, "owner-emergency-control", now.plusSeconds(30));
        }
        if (retry.exhausted()) return new SchedulerDecision(RuntimeState.BLOCKED, false, "retry-budget-exhausted", now.plusSeconds(60));
        if (work.estimatedCpuPercent() > budget.maxCpuPercent() || work.estimatedMemoryPercent() > budget.maxMemoryPercent()) {
            return new SchedulerDecision(RuntimeState.BLOCKED, false, "hard-resource-budget-exceeded", now.plusSeconds(60));
        }

        boolean deadlineImminent = work.hardDeadline() != null && !work.hardDeadline().isAfter(now.plus(DEADLINE_WINDOW));
        boolean userBusy = budget.currentUserLoadPercent() > budget.maxUserLoadPercent();
        boolean deferForEnergy = budget.energySaver() && work.urgency() < 8;
        if (!work.emergencyWork() && !deadlineImminent && (userBusy || deferForEnergy)) {
            return new SchedulerDecision(RuntimeState.WAITING, false,
                    userBusy ? "deferred-for-user-load" : "deferred-for-energy-policy", now.plusSeconds(60));
        }
        return new SchedulerDecision(RuntimeState.RUNNING, true,
                deadlineImminent ? "hard-deadline-priority" : "within-resource-and-control-budgets", now.plusSeconds(30));
    }

    public SchedulerDecision checkpoint(AutomationWork work, EmergencyMode mode, Instant startedAt, Instant now) {
        if (mode == EmergencyMode.STOP) return new SchedulerDecision(RuntimeState.CANCELLED, false, "emergency-stop", now);
        if ((mode == EmergencyMode.PAUSE || mode == EmergencyMode.TAKE_CONTROL) && !work.emergencyWork()) {
            return new SchedulerDecision(RuntimeState.PAUSED, false, "owner-emergency-control", now);
        }
        if (Duration.between(startedAt, now).compareTo(work.maxRuntime()) > 0) {
            return new SchedulerDecision(RuntimeState.CANCELLED, false, "max-runtime-exceeded", now);
        }
        return new SchedulerDecision(RuntimeState.RUNNING, true, "checkpoint-clear", now.plusSeconds(30));
    }
}
