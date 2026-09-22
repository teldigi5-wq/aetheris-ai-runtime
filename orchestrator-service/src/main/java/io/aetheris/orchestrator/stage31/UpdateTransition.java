package io.aetheris.orchestrator.stage31;

public record UpdateTransition(UpdateState from, UpdateState to, String reason) {}
