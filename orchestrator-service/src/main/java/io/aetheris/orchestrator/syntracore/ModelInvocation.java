package io.aetheris.orchestrator.syntracore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public record ModelInvocation(
        String modelId,
        String input,
        int maxOutputTokens,
        List<String> evidenceAddresses) {

    public ModelInvocation(String modelId, String input, int maxOutputTokens) {
        this(modelId, input, maxOutputTokens, List.of());
    }

    public ModelInvocation {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input must not be blank");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
        if (evidenceAddresses == null) {
            throw new IllegalArgumentException("evidenceAddresses must not be null");
        }

        List<String> normalizedAddresses = new ArrayList<>();
        for (String address : evidenceAddresses) {
            if (address == null || address.isBlank()) {
                throw new IllegalArgumentException("evidence address must not be blank");
            }
            String normalized = address.trim();
            if (!normalized.startsWith("aetheris-memory://")) {
                throw new IllegalArgumentException("evidence address must use aetheris-memory:// scheme");
            }
            normalizedAddresses.add(normalized);
        }
        if (new LinkedHashSet<>(normalizedAddresses).size() != normalizedAddresses.size()) {
            throw new IllegalArgumentException("evidenceAddresses must not contain duplicates");
        }
        evidenceAddresses = List.copyOf(normalizedAddresses);
    }
}
