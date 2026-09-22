package io.aetheris.orchestrator.model;

import jakarta.validation.constraints.NotBlank;

public record LocalGenerateRequest(
        @NotBlank String prompt,
        String model
) {
}
