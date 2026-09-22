package io.aetheris.orchestrator.stage32;

import java.time.Instant;

public record AuditQuery(Instant from, Instant to, String subsystem, String actionClass, String actor,
                         String outcome, Integer minimumRisk, String evidenceContains) {}
