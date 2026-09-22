package io.aetheris.orchestrator.stage31;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record PcDigitalTwin(
        String hostId,
        String hardwareProfile,
        Set<String> drivers,
        Set<String> applications,
        Set<String> services,
        Set<String> failedServices,
        String networkProfile,
        double diskFreePercent,
        double memoryUsedPercent,
        double temperatureC,
        String knownGoodConfigId,
        TwinEvidence evidence,
        boolean physicalEvidenceVerified,
        Instant updatedAt) {

    public PcDigitalTwin {
        requireText(hostId, "hostId");
        requireText(hardwareProfile, "hardwareProfile");
        requireText(networkProfile, "networkProfile");
        requireText(knownGoodConfigId, "knownGoodConfigId");
        drivers = immutable(drivers);
        applications = immutable(applications);
        services = immutable(services);
        failedServices = immutable(failedServices);
        requirePercent(diskFreePercent, "diskFreePercent");
        requirePercent(memoryUsedPercent, "memoryUsedPercent");
        if (!Double.isFinite(temperatureC) || temperatureC < -50.0 || temperatureC > 150.0) {
            throw new IllegalArgumentException("temperatureC is outside the supported diagnostic range");
        }
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (physicalEvidenceVerified && evidence != TwinEvidence.VERIFIED_PHYSICAL) {
            throw new IllegalArgumentException("physical verification requires VERIFIED_PHYSICAL evidence");
        }
        if (!physicalEvidenceVerified && evidence == TwinEvidence.VERIFIED_PHYSICAL) {
            throw new IllegalArgumentException("VERIFIED_PHYSICAL evidence requires physicalEvidenceVerified=true");
        }
        if (evidence == TwinEvidence.SYNTHETIC && physicalEvidenceVerified) {
            throw new IllegalArgumentException("synthetic data can never claim physical verification");
        }
    }

    private static Set<String> immutable(Set<String> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    private static void requirePercent(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0 || value > 100.0) {
            throw new IllegalArgumentException(field + " must be between 0 and 100");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
