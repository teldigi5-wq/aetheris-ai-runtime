package io.aetheris.orchestrator.syntracore;

public record ModelStreamChunk(
        long sequence,
        String text,
        boolean terminal) {

    public ModelStreamChunk {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be non-negative");
        }
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
    }
}
