package io.aetheris.orchestrator.checkpoint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateCheckpointRequest(@NotNull UUID taskId, @NotBlank String type, @NotBlank String label, String reference, String metadataJson) {}
