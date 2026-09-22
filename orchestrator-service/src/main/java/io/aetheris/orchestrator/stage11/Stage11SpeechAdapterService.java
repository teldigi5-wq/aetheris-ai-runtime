package io.aetheris.orchestrator.stage11;

import io.aetheris.orchestrator.stage10.Stage10WorkstationService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class Stage11SpeechAdapterService {
    private static final Set<String> ROLES = Set.of("VAD", "STT", "TTS");
    private final Stage10WorkstationService workstation;
    private final Map<String, LocalSpeechAdapterSpec> adapters = new ConcurrentHashMap<>();

    public Stage11SpeechAdapterService(Stage10WorkstationService workstation) {
        this.workstation = workstation;
    }

    public LocalSpeechAdapterSpec register(LocalSpeechAdapterSpec spec) {
        if (spec == null) throw new IllegalArgumentException("Speech adapter specification is required");
        String role = normalize(spec.role()).toUpperCase(Locale.ROOT);
        if (!ROLES.contains(role)) throw new IllegalArgumentException("Speech role must be VAD, STT or TTS");
        String engineId = normalize(spec.engineId()).toLowerCase(Locale.ROOT);
        if (!engineId.matches("[a-z0-9._-]{1,64}")) throw new IllegalArgumentException("engineId is invalid");
        String commandAlias = normalize(spec.commandAlias()).toLowerCase(Locale.ROOT);
        if (!commandAlias.matches("[a-z0-9._-]{1,64}")) throw new IllegalArgumentException("commandAlias must be an owner-approved local alias");
        if (!spec.localOnly()) throw new IllegalArgumentException("Stage 11 speech adapters must be local-only");
        String modelRef = spec.modelRef() == null ? "" : spec.modelRef().trim();
        if (modelRef.startsWith("http://") || modelRef.startsWith("https://")) {
            throw new IllegalArgumentException("Stage 11 local speech modelRef cannot be a remote URL");
        }
        if (role.equals("STT") && !spec.streaming()) throw new IllegalArgumentException("Stage 11 STT must support streaming partials");
        if (role.equals("TTS") && !spec.supportsInterruption()) throw new IllegalArgumentException("Stage 11 TTS must support interruption/barge-in");
        LocalSpeechAdapterSpec normalized = new LocalSpeechAdapterSpec(engineId, role, commandAlias, modelRef,
                true, spec.streaming(), spec.supportsInterruption(), Instant.now());
        adapters.put(role, normalized);
        return normalized;
    }

    public PipelineReadiness evaluatePipeline(PipelineEvidence evidence) {
        List<String> blockers = new ArrayList<>();
        for (String role : ROLES) if (!adapters.containsKey(role)) blockers.add(role + " adapter is not configured");
        Stage10WorkstationService.SpeechBenchmarkResult benchmark = null;
        if (evidence == null || evidence.benchmark() == null) {
            blockers.add("target speech benchmark evidence is required");
        } else {
            benchmark = workstation.evaluateSpeech(evidence.benchmark());
            if (!benchmark.verdict().equals("VERIFIED")) blockers.addAll(benchmark.failures());
            if (benchmark.verdict().equals("EVIDENCE_REQUIRED")) blockers.add("speech pipeline has not been measured on target hardware");
            if (!evidence.priorityControlBypassesInference()) blockers.add("priority STOP/PAUSE control must bypass model inference");
        }
        boolean targetMeasured = evidence != null && evidence.benchmark() != null && evidence.benchmark().measuredOnTargetHardware();
        String status = blockers.isEmpty() ? "TARGET_PIPELINE_VERIFIED" : !targetMeasured ? "EVIDENCE_REQUIRED" : "BLOCKED";
        return new PipelineReadiness(status, List.copyOf(blockers), adapters(), benchmark,
                blockers.isEmpty() ? "Local VAD/STT/TTS adapter contract and target benchmark evidence passed"
                        : "Speech remains hardware-gated until every local adapter and latency/resource requirement passes");
    }

    public List<LocalSpeechAdapterSpec> adapters() {
        return adapters.values().stream().sorted(Comparator.comparing(LocalSpeechAdapterSpec::role)).toList();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Speech adapter field is required");
        return value.trim();
    }

    public record LocalSpeechAdapterSpec(String engineId, String role, String commandAlias, String modelRef,
                                         boolean localOnly, boolean streaming, boolean supportsInterruption,
                                         Instant configuredAt) {}
    public record PipelineEvidence(Stage10WorkstationService.SpeechBenchmarkSample benchmark,
                                   boolean priorityControlBypassesInference) {}
    public record PipelineReadiness(String status, List<String> blockers, List<LocalSpeechAdapterSpec> adapters,
                                    Stage10WorkstationService.SpeechBenchmarkResult benchmark, String detail) {}
}
