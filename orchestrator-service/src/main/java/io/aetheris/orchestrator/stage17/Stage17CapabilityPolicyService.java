package io.aetheris.orchestrator.stage17;

import io.aetheris.orchestrator.stage15.Stage15ServiceCatalogService;
import io.aetheris.orchestrator.stage16.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Stage17CapabilityPolicyService {
    private final Stage17AdapterManifestService manifests;
    private final Stage16AdapterRegistryService adapters;

    public Stage17CapabilityPolicyService(Stage17AdapterManifestService manifests,
                                          Stage16AdapterRegistryService adapters) {
        this.manifests = manifests; this.adapters = adapters;
    }

    public CompiledPolicy compile(String adapterId, Set<String> requestedCapabilities) {
        Stage16AdapterEntity adapter = adapters.get(adapterId);
        Stage17AdapterManifestEntity manifest = manifests.latest(adapter.getId());
        if (requestedCapabilities == null || requestedCapabilities.isEmpty())
            throw new IllegalArgumentException("At least one requested capability is required");
        TreeSet<String> requested = new TreeSet<>();
        for (String value : requestedCapabilities) {
            if (value == null || value.isBlank() || !value.trim().matches("[A-Za-z0-9._:/-]{1,48}"))
                throw new IllegalArgumentException("Requested capability is invalid");
            requested.add(value.trim().toUpperCase(Locale.ROOT));
        }
        if (requested.stream().anyMatch(Stage15ServiceCatalogService.FORBIDDEN_ACTIONS::contains) ||
                !Stage15ServiceCatalogService.BOUNDED_ACTIONS.containsAll(requested))
            throw new IllegalArgumentException("Capability policy requests forbidden or unsupported authority");
        if (!adapter.getCapabilities().containsAll(requested))
            throw new IllegalArgumentException("Requested capability exceeds Stage 16 adapter scope");
        if (!manifest.getCapabilities().containsAll(requested))
            throw new IllegalArgumentException("Requested capability exceeds signed Stage 17 manifest scope");
        String canonical = adapter.getId() + "|" + manifest.getManifestSha256() + "|" + String.join(",", requested);
        return new CompiledPolicy("POLICY_COMPILED", adapter.getId(), manifest.getId(), Set.copyOf(requested),
                Stage17AdapterManifestService.shaText(canonical), true, false, false);
    }

    public record CompiledPolicy(String status, String adapterId, UUID manifestId, Set<String> effectiveCapabilities,
                                 String policySha256, boolean simulationOnly,
                                 boolean productionActivationAllowed, boolean externalActionAttempted) {}
}
