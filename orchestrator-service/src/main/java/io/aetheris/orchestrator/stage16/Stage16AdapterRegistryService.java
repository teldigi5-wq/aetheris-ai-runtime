package io.aetheris.orchestrator.stage16;

import io.aetheris.orchestrator.stage15.Stage15ServiceCatalogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class Stage16AdapterRegistryService {
    private static final Set<String> TYPES = Set.of("SIMULATED_WINDOWS", "SIMULATED_PROVIDER", "SIMULATED_CONTROL");
    private static final String SIMULATION_ATTESTATION = "CI_SIMULATED";
    private final Stage16AdapterRepository repository;

    public Stage16AdapterRegistryService(Stage16AdapterRepository repository) { this.repository = repository; }

    @Transactional
    public Stage16AdapterEntity register(AdapterRegistration request) {
        if (request == null) throw new IllegalArgumentException("Adapter registration is required");
        String id = token(request.id(), "id", 80).toLowerCase(Locale.ROOT);
        if (repository.existsById(id)) throw new IllegalStateException("Stage 16 adapter already exists");
        String name = text(request.displayName(), "displayName", 160);
        String type = token(request.adapterType(), "adapterType", 40).toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw new IllegalArgumentException("Only simulated Stage 16 adapter types are enabled before target validation");
        if (!request.simulationOnly()) throw new IllegalArgumentException("Stage 16 repository mode requires simulationOnly=true");
        String kind = token(request.attestationKind(), "attestationKind", 32).toUpperCase(Locale.ROOT);
        if (!SIMULATION_ATTESTATION.equals(kind)) throw new IllegalArgumentException("Repository/CI adapters must use CI_SIMULATED attestation");
        String attestation = sha(request.attestationSha256(), "attestationSha256");
        Instant attestedAt = request.attestedAt() == null ? Instant.now() : request.attestedAt();
        if (attestedAt.isAfter(Instant.now().plusSeconds(30))) throw new IllegalArgumentException("Adapter attestation cannot be future-dated");
        Set<String> capabilities = normalize(request.capabilities());
        if (capabilities.isEmpty()) throw new IllegalArgumentException("At least one adapter capability is required");
        if (!Stage15ServiceCatalogService.BOUNDED_ACTIONS.containsAll(capabilities))
            throw new IllegalArgumentException("Adapter exposes unsupported or forbidden recovery authority");
        if (capabilities.stream().anyMatch(Stage15ServiceCatalogService.FORBIDDEN_ACTIONS::contains))
            throw new IllegalArgumentException("Adapter exposes forbidden recovery authority");
        return repository.save(new Stage16AdapterEntity(id, name, type, capabilities, true, kind, attestation, attestedAt));
    }

    public Stage16AdapterEntity get(String id) {
        String key = token(id, "adapterId", 80).toLowerCase(Locale.ROOT);
        return repository.findById(key).orElseThrow(() -> new NoSuchElementException("Unknown Stage 16 adapter"));
    }

    public List<Stage16AdapterEntity> list() { return repository.findTop100ByOrderByUpdatedAtDesc(); }

    private Set<String> normalize(Set<String> values) {
        if (values == null) return Set.of();
        TreeSet<String> out = new TreeSet<>();
        for (String value : values) out.add(token(value, "capability", 48).toUpperCase(Locale.ROOT));
        return Set.copyOf(out);
    }
    private String token(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim();
        if (v.length() > max || !v.matches("[A-Za-z0-9._:/-]{1," + max + "}")) throw new IllegalArgumentException(label + " is invalid");
        return v;
    }
    private String text(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim();
        if (v.length() > max) throw new IllegalArgumentException(label + " is too long");
        return v;
    }
    private String sha(String value, String label) {
        if (value == null || !value.trim().toLowerCase(Locale.ROOT).matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException(label + " must be a SHA-256 value");
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record AdapterRegistration(String id, String displayName, String adapterType, Set<String> capabilities,
                                      boolean simulationOnly, String attestationKind, String attestationSha256,
                                      Instant attestedAt) {}
}
