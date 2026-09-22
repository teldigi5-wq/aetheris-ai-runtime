package io.aetheris.orchestrator.syntracore;

import java.time.Duration;
import java.util.Objects;

public record LocalRuntimeBounds(
        int maxInputCharacters,
        int maxOutputTokens,
        int maxStreamChunks,
        int maxChunkCharacters,
        Duration requestTimeout) {

    public LocalRuntimeBounds {
        if (maxInputCharacters <= 0) {
            throw new IllegalArgumentException("maxInputCharacters must be positive");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
        if (maxStreamChunks <= 0) {
            throw new IllegalArgumentException("maxStreamChunks must be positive");
        }
        if (maxChunkCharacters <= 0) {
            throw new IllegalArgumentException("maxChunkCharacters must be positive");
        }
        requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
    }

    public static LocalRuntimeBounds safeDefaults() {
        return new LocalRuntimeBounds(
                64_000,
                4_096,
                8_192,
                32_768,
                Duration.ofMinutes(2));
    }

    public void validate(ModelInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        if (invocation.input().length() > maxInputCharacters) {
            throw new IllegalArgumentException("input exceeds configured local-runtime bound");
        }
        if (invocation.maxOutputTokens() > maxOutputTokens) {
            throw new IllegalArgumentException("maxOutputTokens exceeds configured local-runtime bound");
        }
    }
}
