package io.aetheris.orchestrator.syntracore;

@FunctionalInterface
public interface LocalInferenceEvaluationHook {
    void evaluate(LocalInferenceResult result);
}
