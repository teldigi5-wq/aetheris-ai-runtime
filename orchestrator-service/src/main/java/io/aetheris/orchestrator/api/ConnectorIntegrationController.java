package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.connector.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/connectors")
public class ConnectorIntegrationController {
    private final ConnectorIntegrationService service;

    public ConnectorIntegrationController(ConnectorIntegrationService service) {
        this.service = service;
    }

    @PostMapping("/connections")
    public ConnectorConnectionView register(@RequestBody RegisterConnectorRequest request) {
        return service.register(request);
    }

    @GetMapping("/connections")
    public List<ConnectorConnectionView> connections() {
        return service.recentConnections();
    }

    @PatchMapping("/connections/{id}/status")
    public ConnectorConnectionView updateStatus(@PathVariable UUID id,
                                                @RequestBody UpdateConnectorStatusRequest request) {
        return service.updateStatus(id, request);
    }

    @PostMapping("/events")
    public ConnectorIngestResult ingest(@RequestBody ConnectorInboundEvent event) {
        return service.ingest(event);
    }

    @GetMapping("/receipts")
    public List<ConnectorEventReceiptEntity> receipts() {
        return service.recentReceipts();
    }
}
