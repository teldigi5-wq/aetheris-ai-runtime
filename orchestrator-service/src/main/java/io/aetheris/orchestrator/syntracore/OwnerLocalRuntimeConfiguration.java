package io.aetheris.orchestrator.syntracore;

import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Opt-in Spring wiring for the certified owner-local Syntra/Ollama runtime path.
 * No provider call occurs while the application context is being created.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "aetheris.syntra.owner-runtime",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(OwnerLocalRuntimeProperties.class)
public class OwnerLocalRuntimeConfiguration {

    @Bean
    LocalRuntimeEndpoint ownerLocalRuntimeEndpoint(OwnerLocalRuntimeProperties properties) {
        properties.validateEnabledConfiguration();
        return properties.localEndpoint();
    }

    @Bean
    LocalRuntimeBounds ownerLocalRuntimeBounds() {
        return LocalRuntimeBounds.safeDefaults();
    }

    @Bean
    OllamaAdapterRuntimeBridge ownerLocalRuntimeBridge(
            OwnerLocalRuntimeProperties properties,
            LocalRuntimeEndpoint ownerLocalRuntimeEndpoint,
            LocalRuntimeBounds ownerLocalRuntimeBounds) {
        return new OllamaAdapterRuntimeBridge(
                ownerLocalRuntimeEndpoint,
                ownerLocalRuntimeBounds,
                properties.baseCandidates(),
                properties.adapterBindings());
    }

    @Bean
    OwnerLocalRuntimeReadinessService ownerLocalRuntimeReadinessService(
            OwnerLocalRuntimeProperties properties,
            OllamaAdapterRuntimeBridge ownerLocalRuntimeBridge) {
        return new OwnerLocalRuntimeReadinessService(
                ownerLocalRuntimeBridge,
                properties.expectedBaseModelIds(),
                properties.expectedAdapterAliases());
    }

    @Bean
    HealthIndicator ownerLocalRuntimeHealthIndicator(
            OwnerLocalRuntimeReadinessService ownerLocalRuntimeReadinessService) {
        return new OwnerLocalRuntimeHealthIndicator(ownerLocalRuntimeReadinessService);
    }
}
