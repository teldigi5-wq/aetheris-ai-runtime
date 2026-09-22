package io.aetheris.orchestrator.syntracore;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.Objects;

/** Actuator projection of read-only owner-runtime readiness evidence. */
public final class OwnerLocalRuntimeHealthIndicator implements HealthIndicator {
    private final OwnerLocalRuntimeReadinessService readiness;

    public OwnerLocalRuntimeHealthIndicator(OwnerLocalRuntimeReadinessService readiness) {
        this.readiness = Objects.requireNonNull(readiness, "readiness");
    }

    @Override
    public Health health() {
        OwnerLocalRuntimeReadiness report = readiness.check();
        Health.Builder builder = report.ready() ? Health.up() : Health.down();
        builder.withDetail("provider", OllamaLocalRuntime.PROVIDER_ID)
                .withDetail("providerReachable", report.providerReachable())
                .withDetail("visibleModelIds", report.visibleModelIds())
                .withDetail("missingBaseModelIds", report.missingBaseModelIds())
                .withDetail("missingAdapterAliases", report.missingAdapterAliases())
                .withDetail("observedAt", report.observedAt().toString())
                .withDetail("evidenceRef", report.evidenceRef());
        if (report.providerErrorCode() != null) {
            builder.withDetail("providerErrorCode", report.providerErrorCode());
        }
        return builder.build();
    }
}
