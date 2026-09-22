package io.aetheris.orchestrator.connector.action;

import io.aetheris.orchestrator.connector.ConnectorProvider;

public enum ConnectorActionKind {
    GMAIL_SEND_EMAIL(ConnectorProvider.GMAIL),
    CALENDAR_CREATE_EVENT(ConnectorProvider.CALENDAR),
    GITHUB_CREATE_ISSUE(ConnectorProvider.GITHUB);

    private final ConnectorProvider provider;

    ConnectorActionKind(ConnectorProvider provider) {
        this.provider = provider;
    }

    public ConnectorProvider provider() {
        return provider;
    }
}
