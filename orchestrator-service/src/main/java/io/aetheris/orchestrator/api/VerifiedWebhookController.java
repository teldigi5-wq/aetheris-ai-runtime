package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.connector.ConnectorIngestResult;
import io.aetheris.orchestrator.connector.adapter.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/connectors")
public class VerifiedWebhookController {
    private final VerifiedWebhookAdapterService service;

    public VerifiedWebhookController(VerifiedWebhookAdapterService service) {
        this.service = service;
    }

    @PutMapping("/connections/{id}/adapter")
    public ConnectorAdapterView configure(@PathVariable UUID id,
                                          @RequestBody ConfigureConnectorAdapterRequest request) {
        return service.configure(id, request);
    }

    @GetMapping("/connections/{id}/adapter")
    public ConnectorAdapterView adapter(@PathVariable UUID id) {
        return service.adapter(id);
    }

    @PostMapping(value = "/webhooks/generic/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ConnectorIngestResult generic(
            @PathVariable UUID id,
            @RequestHeader(name = "X-Aetheris-Event-Id", required = false) String eventId,
            @RequestHeader(name = "X-Aetheris-Timestamp", required = false) String timestamp,
            @RequestHeader(name = "X-Aetheris-Signature", required = false) String signature,
            @RequestBody byte[] body) {
        return service.ingestGeneric(id, eventId, timestamp, signature, body);
    }

    @PostMapping(value = "/webhooks/github/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ConnectorIngestResult github(
            @PathVariable UUID id,
            @RequestHeader(name = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(name = "X-GitHub-Event", required = false) String eventName,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] body) {
        return service.ingestGitHub(id, deliveryId, eventName, signature, body);
    }
}
