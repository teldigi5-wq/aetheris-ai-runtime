package io.aetheris.orchestrator.connector.adapter;

public record ConfigureConnectorAdapterRequest(
        ConnectorAdapterType adapterType,
        String secretReference,
        Integer maxClockSkewSeconds
) {}
