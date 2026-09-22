package io.aetheris.orchestrator.connector.action;

public record ConnectorActionPolicyView(
        boolean syntheticWritesEnabled,
        boolean liveWritesEnabled,
        boolean ownerApprovalRequired,
        String evidenceClass,
        String truthBoundary
) {
}
