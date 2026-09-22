package io.aetheris.orchestrator.stage32;

import java.time.Instant;

public record SchedulerDecision(RuntimeState state, boolean runNow, String reason, Instant nextReviewAt) {}
