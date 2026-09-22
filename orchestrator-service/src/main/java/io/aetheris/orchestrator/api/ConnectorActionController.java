package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.connector.action.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/connectors/actions")
public class ConnectorActionController {
    private final ConnectorActionService service;

    public ConnectorActionController(ConnectorActionService service) {
        this.service = service;
    }

    @GetMapping("/policy")
    public ConnectorActionPolicyView policy() {
        return service.policy();
    }

    @PostMapping
    public ConnectorActionView create(@Valid @RequestBody CreateConnectorActionRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<ConnectorActionView> recent() {
        return service.recent();
    }

    @GetMapping("/{id}")
    public ConnectorActionView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/execute")
    public ConnectorActionView execute(@PathVariable UUID id) {
        return service.execute(id);
    }
}
