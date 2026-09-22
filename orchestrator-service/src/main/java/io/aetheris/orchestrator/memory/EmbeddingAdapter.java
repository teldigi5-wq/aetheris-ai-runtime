package io.aetheris.orchestrator.memory;

public interface EmbeddingAdapter {
    String id();
    int dimensions();
    float[] embed(String text);
}
