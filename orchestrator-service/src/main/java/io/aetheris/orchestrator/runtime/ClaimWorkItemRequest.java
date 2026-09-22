package io.aetheris.orchestrator.runtime;
public record ClaimWorkItemRequest(String workerId, Integer leaseSeconds) {}
