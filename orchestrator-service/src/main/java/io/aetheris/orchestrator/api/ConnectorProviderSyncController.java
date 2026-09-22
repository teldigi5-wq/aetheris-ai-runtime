package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.connector.sync.ProviderReadSyncService;
import io.aetheris.orchestrator.connector.sync.ProviderSyncResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/connectors")
public class ConnectorProviderSyncController {
    private final ProviderReadSyncService service;

    public ConnectorProviderSyncController(ProviderReadSyncService service) {
        this.service = service;
    }

    @PostMapping("/connections/{connectionId}/sync")
    public ProviderSyncResult sync(@PathVariable UUID connectionId) {
        return service.sync(connectionId);
    }
}
