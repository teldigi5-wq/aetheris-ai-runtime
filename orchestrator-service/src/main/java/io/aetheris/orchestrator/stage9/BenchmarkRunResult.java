package io.aetheris.orchestrator.stage9;
import io.aetheris.orchestrator.evaluation.EvaluationRunEntity;
public record BenchmarkRunResult(BenchmarkPack pack,EvaluationRunEntity evaluation,String verdict){}
