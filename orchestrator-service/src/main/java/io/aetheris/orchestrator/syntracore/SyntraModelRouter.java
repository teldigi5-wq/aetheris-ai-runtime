package io.aetheris.orchestrator.syntracore;

import io.aetheris.orchestrator.stage31.ModelBenchmarkEngine;
import io.aetheris.orchestrator.stage31.ModelBenchmarkSample;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class SyntraModelRouter {
    private static final double LOCAL_PREFERENCE_BONUS = 0.03;
    private static final double CPU_FALLBACK_PENALTY = 0.01;

    private final ModelBenchmarkEngine benchmarkEngine;

    public SyntraModelRouter() {
        this(new ModelBenchmarkEngine());
    }

    SyntraModelRouter(ModelBenchmarkEngine benchmarkEngine) {
        this.benchmarkEngine = Objects.requireNonNull(benchmarkEngine, "benchmarkEngine");
    }

    public ModelRouteDecision route(
            ModelRouteRequest request,
            HardwareCapacity hardware,
            List<ModelRuntimeCandidate> candidates) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(hardware, "hardware");
        Objects.requireNonNull(candidates, "candidates");

        if (candidates.isEmpty()) {
            return ModelRouteDecision.unavailable(List.of("NO_CANDIDATES"));
        }

        List<String> rejections = new ArrayList<>();
        List<RankedCandidate> ranked = new ArrayList<>();

        for (ModelRuntimeCandidate candidate : candidates) {
            Objects.requireNonNull(candidate, "candidate");
            String rejection = reject(request, hardware, candidate);
            if (rejection != null) {
                rejections.add(candidate.key() + ":" + rejection);
                continue;
            }

            ExecutionTarget target = executionTarget(request, hardware, candidate);
            double baseScore = benchmarkEngine.rank(
                            List.of(toBenchmarkSample(candidate)),
                            request.zeroCostMode(),
                            request.privateMode())
                    .getFirst()
                    .score();
            double score = adjustedScore(baseScore, request, candidate, target);
            ranked.add(new RankedCandidate(candidate, target, score));
        }

        if (ranked.isEmpty()) {
            if (rejections.isEmpty()) {
                rejections.add("NO_COMPATIBLE_MODEL");
            }
            return ModelRouteDecision.unavailable(rejections);
        }

        ranked.sort(Comparator
                .comparingDouble(RankedCandidate::score)
                .reversed()
                .thenComparing(rankedCandidate -> rankedCandidate.candidate().key()));

        RankedCandidate selected = ranked.getFirst();
        ModelRuntimeCandidate candidate = selected.candidate();
        return ModelRouteDecision.selected(
                new ModelRouteSelection(
                        candidate.providerId(),
                        candidate.modelId(),
                        selected.target(),
                        selected.score(),
                        routeReason(request, candidate, selected.target())),
                rejections);
    }

    private static String reject(
            ModelRouteRequest request,
            HardwareCapacity hardware,
            ModelRuntimeCandidate candidate) {
        if (!candidate.healthy()) {
            return "UNHEALTHY";
        }
        if (!candidate.capabilities().containsAll(request.requiredCapabilities())) {
            return "CAPABILITY_MISMATCH";
        }
        if (candidate.contextWindowTokens() < request.requiredContextTokens()) {
            return "CONTEXT_WINDOW_TOO_SMALL";
        }
        if (request.requireStreaming() && !candidate.streaming()) {
            return "STREAMING_REQUIRED";
        }
        if (request.privateMode() && !candidate.local()) {
            return "PRIVATE_MODE_REQUIRES_LOCAL";
        }
        if (request.zeroCostMode() && candidate.estimatedCostUsd() != 0.0) {
            return "ZERO_COST_MODE_BLOCKED_COST";
        }
        if (candidate.local() && hardware.ramMb() < candidate.requiredRamMb()) {
            return "INSUFFICIENT_RAM";
        }
        if (candidate.local()
                && candidate.gpuPreferred()
                && (!hardware.gpuAvailable() || hardware.vramMb() < candidate.requiredVramMb())
                && !(request.allowCpuFallback() && candidate.cpuFallbackSupported())) {
            return "INSUFFICIENT_VRAM";
        }
        return null;
    }

    private static ExecutionTarget executionTarget(
            ModelRouteRequest request,
            HardwareCapacity hardware,
            ModelRuntimeCandidate candidate) {
        if (!candidate.local()) {
            return ExecutionTarget.REMOTE;
        }
        if (!candidate.gpuPreferred()) {
            return ExecutionTarget.CPU;
        }
        if (hardware.gpuAvailable() && hardware.vramMb() >= candidate.requiredVramMb()) {
            return ExecutionTarget.GPU;
        }
        if (request.allowCpuFallback() && candidate.cpuFallbackSupported()) {
            return ExecutionTarget.CPU;
        }
        throw new IllegalStateException("candidate passed validation without an execution target");
    }

    private static ModelBenchmarkSample toBenchmarkSample(ModelRuntimeCandidate candidate) {
        return new ModelBenchmarkSample(
                candidate.key(),
                candidate.qualityScore(),
                candidate.firstTokenLatencyMs(),
                candidate.tokensPerSecond(),
                candidate.failureRate(),
                candidate.observedVramMb(),
                candidate.estimatedCostUsd(),
                candidate.local());
    }

    private static double adjustedScore(
            double baseScore,
            ModelRouteRequest request,
            ModelRuntimeCandidate candidate,
            ExecutionTarget target) {
        double adjusted = baseScore;
        if (request.preferLocal() && candidate.local()) {
            adjusted += LOCAL_PREFERENCE_BONUS;
        }
        if (target == ExecutionTarget.CPU && candidate.gpuPreferred()) {
            adjusted -= CPU_FALLBACK_PENALTY;
        }
        adjusted = Math.max(0.0, Math.min(1.0, adjusted));
        return Math.round(adjusted * 10000.0) / 10000.0;
    }

    private static String routeReason(
            ModelRouteRequest request,
            ModelRuntimeCandidate candidate,
            ExecutionTarget target) {
        if (target == ExecutionTarget.CPU && candidate.gpuPreferred()) {
            return "LOCAL_CPU_FALLBACK";
        }
        if (target == ExecutionTarget.GPU) {
            return request.preferLocal() ? "LOCAL_GPU_PREFERRED" : "LOCAL_GPU_SELECTED";
        }
        if (target == ExecutionTarget.CPU) {
            return request.preferLocal() ? "LOCAL_CPU_PREFERRED" : "LOCAL_CPU_SELECTED";
        }
        return "REMOTE_PROVIDER_SELECTED";
    }

    private record RankedCandidate(
            ModelRuntimeCandidate candidate,
            ExecutionTarget target,
            double score) {
    }
}
