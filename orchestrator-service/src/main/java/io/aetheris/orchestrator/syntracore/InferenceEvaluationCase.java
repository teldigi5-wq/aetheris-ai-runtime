package io.aetheris.orchestrator.syntracore;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record InferenceEvaluationCase(
        String caseId,
        InferenceTask task,
        InferenceStatus expectedStatus,
        List<String> expectedEvidenceAddresses,
        Optional<String> expectedProviderId,
        Optional<String> expectedModelId,
        boolean requireNonBlankOutput) {

    public InferenceEvaluationCase {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must not be blank");
        }
        caseId = caseId.trim();
        task = Objects.requireNonNull(task, "task");
        expectedStatus = Objects.requireNonNull(expectedStatus, "expectedStatus");
        expectedEvidenceAddresses = List.copyOf(Objects.requireNonNull(
                expectedEvidenceAddresses,
                "expectedEvidenceAddresses"));
        expectedProviderId = Objects.requireNonNull(expectedProviderId, "expectedProviderId")
                .map(String::trim);
        expectedModelId = Objects.requireNonNull(expectedModelId, "expectedModelId")
                .map(String::trim);

        if (expectedProviderId.isPresent() != expectedModelId.isPresent()) {
            throw new IllegalArgumentException("expected provider and model must be supplied together");
        }
        if (expectedProviderId.filter(String::isBlank).isPresent()
                || expectedModelId.filter(String::isBlank).isPresent()) {
            throw new IllegalArgumentException("expected provider/model must not be blank");
        }
        for (String address : expectedEvidenceAddresses) {
            if (address == null || !address.startsWith("aetheris-memory://")) {
                throw new IllegalArgumentException("expected evidence must use aetheris-memory:// scheme");
            }
        }
        if (new LinkedHashSet<>(expectedEvidenceAddresses).size() != expectedEvidenceAddresses.size()) {
            throw new IllegalArgumentException("expectedEvidenceAddresses must not contain duplicates");
        }
    }

    public static InferenceEvaluationCase structural(
            String caseId,
            InferenceTask task,
            InferenceStatus expectedStatus,
            List<String> expectedEvidenceAddresses,
            boolean requireNonBlankOutput) {
        return new InferenceEvaluationCase(
                caseId,
                task,
                expectedStatus,
                expectedEvidenceAddresses,
                Optional.empty(),
                Optional.empty(),
                requireNonBlankOutput);
    }

    public static InferenceEvaluationCase routed(
            String caseId,
            InferenceTask task,
            InferenceStatus expectedStatus,
            List<String> expectedEvidenceAddresses,
            String expectedProviderId,
            String expectedModelId,
            boolean requireNonBlankOutput) {
        return new InferenceEvaluationCase(
                caseId,
                task,
                expectedStatus,
                expectedEvidenceAddresses,
                Optional.ofNullable(expectedProviderId),
                Optional.ofNullable(expectedModelId),
                requireNonBlankOutput);
    }
}
