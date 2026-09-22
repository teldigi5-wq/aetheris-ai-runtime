package io.aetheris.orchestrator.syntracore;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record LocalInferenceResult(
        InferenceStatus status,
        InferenceTask task,
        Optional<ModelRouteSelection> routeSelection,
        String output,
        List<String> evidenceAddresses,
        List<String> routeRejections,
        int emittedChunks,
        boolean terminalObserved) {

    public LocalInferenceResult {
        status = Objects.requireNonNull(status, "status");
        task = Objects.requireNonNull(task, "task");
        routeSelection = Objects.requireNonNull(routeSelection, "routeSelection");
        if (output == null) {
            throw new IllegalArgumentException("output must not be null");
        }
        evidenceAddresses = List.copyOf(Objects.requireNonNull(evidenceAddresses, "evidenceAddresses"));
        routeRejections = List.copyOf(Objects.requireNonNull(routeRejections, "routeRejections"));
        if (emittedChunks < 0) {
            throw new IllegalArgumentException("emittedChunks must not be negative");
        }
        for (String address : evidenceAddresses) {
            if (address == null || !address.startsWith("aetheris-memory://")) {
                throw new IllegalArgumentException("evidence address must use aetheris-memory:// scheme");
            }
        }
        if (new LinkedHashSet<>(evidenceAddresses).size() != evidenceAddresses.size()) {
            throw new IllegalArgumentException("evidenceAddresses must not contain duplicates");
        }
        switch (status) {
            case COMPLETED -> {
                if (routeSelection.isEmpty() || !terminalObserved) {
                    throw new IllegalArgumentException("completed inference requires a route and terminal frame");
                }
            }
            case CANCELLED -> {
                if (routeSelection.isEmpty() || terminalObserved) {
                    throw new IllegalArgumentException("cancelled inference requires a route without a terminal frame");
                }
            }
            case UNAVAILABLE -> {
                if (routeSelection.isPresent() || !output.isEmpty() || emittedChunks != 0 || terminalObserved) {
                    throw new IllegalArgumentException("unavailable inference must not report runtime output");
                }
            }
        }
    }

    public static LocalInferenceResult completed(
            InferenceTask task,
            ModelRouteSelection selection,
            String output,
            List<String> evidenceAddresses,
            List<String> routeRejections,
            int emittedChunks) {
        return new LocalInferenceResult(
                InferenceStatus.COMPLETED,
                task,
                Optional.of(selection),
                output,
                evidenceAddresses,
                routeRejections,
                emittedChunks,
                true);
    }

    public static LocalInferenceResult cancelled(
            InferenceTask task,
            ModelRouteSelection selection,
            String output,
            List<String> evidenceAddresses,
            List<String> routeRejections,
            int emittedChunks) {
        return new LocalInferenceResult(
                InferenceStatus.CANCELLED,
                task,
                Optional.of(selection),
                output,
                evidenceAddresses,
                routeRejections,
                emittedChunks,
                false);
    }

    public static LocalInferenceResult unavailable(
            InferenceTask task,
            List<String> evidenceAddresses,
            List<String> routeRejections) {
        return new LocalInferenceResult(
                InferenceStatus.UNAVAILABLE,
                task,
                Optional.empty(),
                "",
                evidenceAddresses,
                routeRejections,
                0,
                false);
    }
}
