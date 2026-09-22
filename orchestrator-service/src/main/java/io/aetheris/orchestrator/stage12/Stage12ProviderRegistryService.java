package io.aetheris.orchestrator.stage12;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.*;

@Service
public class Stage12ProviderRegistryService {
    private static final Set<String> TYPES = Set.of("NOTIFICATION", "MARKET_DATA", "EXCHANGE_TESTNET", "PRIVATE_TRANSPORT");
    private static final Set<String> FORBIDDEN_CAPABILITIES = Set.of("LIVE_ORDER", "WITHDRAWAL", "TRANSFER", "ARBITRARY_SHELL", "ADMIN_BYPASS");
    private final Stage12ProviderRepository repository;

    public Stage12ProviderRegistryService(Stage12ProviderRepository repository) { this.repository = repository; }

    @Transactional
    public ProviderView register(ProviderRegistration request) {
        if (request == null) throw new IllegalArgumentException("Provider registration is required");
        String id = token(request.providerId(), "providerId", 80).toLowerCase(Locale.ROOT);
        String type = token(request.type(), "type", 32).toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw new IllegalArgumentException("Unsupported Stage 12 provider type: " + type);
        String endpoint = validateEndpoint(type, request.endpoint());
        String credentialAlias = optionalToken(request.credentialAlias(), "credentialAlias", 120);
        Set<String> capabilities = normalizeCapabilities(request.capabilities());
        boolean readOnly = request.readOnly();
        if ("MARKET_DATA".equals(type) && !readOnly) throw new IllegalArgumentException("Market-data providers must be read-only");
        if ("PRIVATE_TRANSPORT".equals(type) && capabilities.stream().anyMatch(c -> !Set.of("TUNNEL_STATUS", "PAIR_DEVICE", "REVOKE_DEVICE").contains(c))) {
            throw new IllegalArgumentException("Private-transport capabilities must remain narrow");
        }
        if ("EXCHANGE_TESTNET".equals(type) && capabilities.stream().anyMatch(c -> !Set.of("READ_ACCOUNT", "READ_MARKET", "TEST_ORDER", "CANCEL_TEST_ORDER").contains(c))) {
            throw new IllegalArgumentException("Exchange-testnet capabilities exceed Stage 12 boundary");
        }
        String status = request.enabled() ? "CONFIGURED_PENDING_PROVIDER_EVIDENCE" : "DISABLED";
        String csv = String.join(",", capabilities);
        Stage12ProviderEntity entity = repository.findByProviderId(id)
                .orElseGet(() -> new Stage12ProviderEntity(UUID.randomUUID(), id, type, endpoint, credentialAlias,
                        request.enabled(), status, csv, readOnly));
        if (!entity.getType().equals(type)) throw new IllegalArgumentException("Provider type cannot change for an existing providerId");
        entity.update(endpoint, credentialAlias, request.enabled(), status, csv, readOnly);
        return view(repository.save(entity));
    }

    @Transactional
    public ProviderView recordHealth(String providerId, ProviderHealthEvidence evidence) {
        Stage12ProviderEntity entity = repository.findByProviderId(require(providerId, "providerId").toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new NoSuchElementException("Unknown Stage 12 provider"));
        if (evidence == null) throw new IllegalArgumentException("Provider health evidence is required");
        if (!entity.isEnabled()) throw new IllegalStateException("Disabled provider cannot become healthy");
        if (!evidence.providerMeasured()) throw new IllegalArgumentException("Provider health cannot be certified by CI/configuration alone");
        String attestation = require(evidence.attestationSha256(), "attestationSha256").toLowerCase(Locale.ROOT);
        if (!attestation.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Provider health requires a SHA-256 attestation reference");
        if (evidence.latencyMs() < 0 || evidence.latencyMs() > 120_000) throw new IllegalArgumentException("Provider latency is invalid");
        String health = token(evidence.health(), "health", 24).toUpperCase(Locale.ROOT);
        if (!Set.of("HEALTHY", "DEGRADED", "UNAVAILABLE").contains(health)) throw new IllegalArgumentException("Unsupported provider health state");
        String status = "PROVIDER_REPORTED_" + health;
        entity.update(entity.getEndpoint(), entity.getCredentialAlias(), true, status, entity.getCapabilitiesCsv(), entity.isReadOnly());
        return view(repository.save(entity));
    }

    public List<ProviderView> list() {
        return repository.findAll().stream().sorted(Comparator.comparing(Stage12ProviderEntity::getProviderId)).map(this::view).toList();
    }

    public Optional<ProviderView> find(String providerId) {
        return repository.findByProviderId(require(providerId, "providerId").toLowerCase(Locale.ROOT)).map(this::view);
    }

    private ProviderView view(Stage12ProviderEntity e) {
        return new ProviderView(e.getId(), e.getProviderId(), e.getType(), e.getEndpoint(), e.getCredentialAlias().isBlank() ? null : e.getCredentialAlias(),
                e.isEnabled(), e.getStatus(), e.getCapabilitiesCsv().isBlank() ? Set.of() : Set.copyOf(Arrays.asList(e.getCapabilitiesCsv().split(","))),
                e.isReadOnly(), e.getUpdatedAt(), false);
    }

    private Set<String> normalizeCapabilities(Set<String> input) {
        TreeSet<String> out = new TreeSet<>();
        if (input != null) for (String raw : input) {
            String capability = token(raw, "capability", 48).toUpperCase(Locale.ROOT);
            if (FORBIDDEN_CAPABILITIES.contains(capability)) throw new IllegalArgumentException("Forbidden Stage 12 capability: " + capability);
            out.add(capability);
        }
        return Set.copyOf(out);
    }

    private String validateEndpoint(String type, String value) {
        String endpoint = require(value, "endpoint");
        if (endpoint.length() > 180) throw new IllegalArgumentException("endpoint is too long");
        URI uri;
        try { uri = URI.create(endpoint); } catch (Exception e) { throw new IllegalArgumentException("endpoint is invalid"); }
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Remote Stage 12 providers require HTTPS");
        if (uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null) throw new IllegalArgumentException("endpoint must not contain credentials and must have a host");
        String normalized = endpoint.toLowerCase(Locale.ROOT);
        if ("EXCHANGE_TESTNET".equals(type) && !(normalized.contains("testnet") || normalized.contains("sandbox"))) {
            throw new IllegalArgumentException("Exchange provider endpoint must be an explicit testnet/sandbox endpoint");
        }
        return endpoint;
    }

    private String optionalToken(String value, String label, int max) {
        if (value == null || value.isBlank()) return "";
        return token(value, label, max);
    }

    private String token(String value, String label, int max) {
        String v = require(value, label);
        if (v.length() > max || !v.matches("[A-Za-z0-9._:-]{1," + max + "}")) throw new IllegalArgumentException(label + " is invalid");
        return v;
    }

    private String require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }

    public record ProviderRegistration(String providerId, String type, String endpoint, String credentialAlias,
                                       boolean enabled, Set<String> capabilities, boolean readOnly) {}
    public record ProviderHealthEvidence(String health, long latencyMs, boolean providerMeasured,
                                         String attestationSha256, Instant observedAt) {}
    public record ProviderView(UUID id, String providerId, String type, String endpoint, String credentialAlias,
                               boolean enabled, String status, Set<String> capabilities, boolean readOnly,
                               Instant updatedAt, boolean credentialValueExposed) {}
}
