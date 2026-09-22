package io.aetheris.orchestrator.stage31;

public record UpdateTransitionRequest(
        UpdateState current,
        UpdateEvent event,
        boolean healthEvidenceVerified) {}
