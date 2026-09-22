package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.connector.oauth.*;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/connectors")
public class ConnectorOAuthController {
    private final ConnectorOAuthService service;

    public ConnectorOAuthController(ConnectorOAuthService service) {
        this.service = service;
    }

    @PostMapping("/connections/{connectionId}/oauth/authorizations")
    public OAuthAuthorizationView start(@PathVariable UUID connectionId,
                                        @RequestBody StartOAuthAuthorizationRequest request) {
        return service.start(connectionId, request);
    }

    @PostMapping("/oauth/authorizations/{sessionId}/complete")
    public ProviderCredentialView complete(@PathVariable UUID sessionId,
                                           @RequestBody CompleteOAuthAuthorizationRequest request) {
        return service.complete(sessionId, request);
    }

    @GetMapping("/connections/{connectionId}/oauth/credential")
    public ProviderCredentialView credential(@PathVariable UUID connectionId) {
        return service.credential(connectionId);
    }

    @PostMapping("/connections/{connectionId}/oauth/refresh")
    public ProviderCredentialView refresh(@PathVariable UUID connectionId) {
        return service.refresh(connectionId);
    }

    @GetMapping("/connections/{connectionId}/provider-health")
    public ProviderHealthView health(@PathVariable UUID connectionId) {
        return service.health(connectionId);
    }

    @DeleteMapping("/connections/{connectionId}/oauth/credential")
    public ProviderCredentialView disconnect(@PathVariable UUID connectionId) {
        return service.disconnect(connectionId);
    }
}
