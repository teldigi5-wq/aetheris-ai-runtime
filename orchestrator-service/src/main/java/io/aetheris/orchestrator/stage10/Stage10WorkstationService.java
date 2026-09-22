package io.aetheris.orchestrator.stage10;

import io.aetheris.orchestrator.host.HostCommandEnvelope;
import io.aetheris.orchestrator.host.HostCommandRequest;
import io.aetheris.orchestrator.host.HostCommandService;
import io.aetheris.orchestrator.vault.OperatingSystemCredentialVault;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Stage10WorkstationService {
    private static final Set<String> HEAVY_WORKLOADS = Set.of("LOCAL_LLM_HEAVY", "EMBED_REBUILD", "BACKTEST_BATCH", "MODEL_ARENA_BATCH");
    private final HostCommandService hostCommands;
    private final Optional<OperatingSystemCredentialVault> osVault;
    private final Set<String> allowedApps;
    private final List<String> allowedWorkspaceRoots;

    public Stage10WorkstationService(
            HostCommandService hostCommands,
            Optional<OperatingSystemCredentialVault> osVault,
            @Value("${aetheris.stage10.windows.allowed-apps:}") String allowedApps,
            @Value("${aetheris.stage10.windows.allowed-workspace-roots:}") String allowedWorkspaceRoots) {
        this.hostCommands = hostCommands;
        this.osVault = osVault;
        this.allowedApps = parseCsv(allowedApps);
        this.allowedWorkspaceRoots = parseRoots(allowedWorkspaceRoots);
    }

    public BootstrapPlan bootstrapPlan() {
        return new BootstrapPlan(
                "PACKAGE_READY_HARDWARE_PENDING",
                "AetherisHostAgent",
                "per-user least-privilege service; elevation only for the explicit installer step",
                List.of(
                        "verify signed Stage 10 host package and recorded checksum",
                        "install into an owner-selected application directory",
                        "register the host agent with least-privilege service identity",
                        "pair the host using the existing challenge/response protocol",
                        "grant only the initial Stage 10 capability allowlist",
                        "run health checks before enabling automatic startup"),
                List.of(
                        "host heartbeat is current",
                        "signed command envelope verification succeeds",
                        "replay protection rejects duplicate command ids",
                        "telemetry handler responds without admin rights",
                        "emergency stop remains reachable"),
                List.of(
                        "stop and disable the host service",
                        "revoke the paired host identity",
                        "remove the installed Stage 10 package",
                        "retain audit receipts and owner-created data",
                        "restore the previous approved package if rollback was requested"));
    }

    public WorkstationReadiness assess(WorkstationProbe probe) {
        if (probe == null) throw new IllegalArgumentException("Workstation probe is required");
        List<String> blockers = new ArrayList<>();
        boolean windows = probe.osName() != null && probe.osName().toLowerCase(Locale.ROOT).contains("windows");
        if (!windows) blockers.add("Target operating system is not Windows");
        if (probe.cpuCores() < 4) blockers.add("At least 4 logical CPU cores are required for the Stage 10 host profile");
        if (probe.ramMb() < 8192) blockers.add("At least 8 GB RAM is required for the Stage 10 host profile");
        boolean hostEligible = windows && probe.cpuCores() >= 4 && probe.ramMb() >= 8192;
        boolean speechEligible = hostEligible && probe.microphoneDetected() && probe.speakerDetected();
        boolean localInferenceEligible = hostEligible && probe.vramMb() >= 4096;
        String status = blockers.isEmpty() ? "READY_FOR_HARDWARE_VALIDATION" : "NOT_READY";
        return new WorkstationReadiness(status, List.copyOf(blockers), hostEligible, speechEligible,
                localInferenceEligible, windows && hostEligible,
                "Assessment only: no software was installed and no hardware capability is marked validated until the host agent reports from the target PC");
    }

    public CommandGuardDecision validateLeastPrivilegeCommand(LeastPrivilegeCommand request) {
        if (request == null || request.hostId() == null) throw new IllegalArgumentException("hostId is required");
        String capability = normalized(request.capability());
        String action = normalized(request.action());
        Map<String, Object> args = request.arguments() == null ? Map.of() : request.arguments();
        rejectDangerousArgumentText(args);
        switch (capability) {
            case "system.telemetry" -> {
                requireAction(action, "snapshot");
                if (!args.isEmpty()) throw new IllegalArgumentException("system.telemetry does not accept arbitrary arguments");
            }
            case "process.status" -> {
                requireAction(action, "status");
                String name = stringArg(args, "processName");
                if (!name.matches("[A-Za-z0-9_.-]{1,96}")) throw new IllegalArgumentException("processName is not a safe process identifier");
            }
            case "app.launch" -> {
                requireAction(action, "launch");
                String alias = stringArg(args, "appAlias").toLowerCase(Locale.ROOT);
                if (!alias.matches("[a-z0-9._-]{1,64}")) throw new IllegalArgumentException("appAlias is invalid");
                if (allowedApps.isEmpty() || !allowedApps.contains(alias)) throw new IllegalArgumentException("Application alias is not Stage 10 allowlisted: " + alias);
                if (args.size() != 1) throw new IllegalArgumentException("app.launch accepts only appAlias; arbitrary command-line arguments are not allowed");
            }
            case "workspace.open" -> {
                requireAction(action, "open");
                String path = normalizePath(stringArg(args, "path"));
                if (allowedWorkspaceRoots.isEmpty() || allowedWorkspaceRoots.stream().noneMatch(root -> underRoot(path, root))) {
                    throw new IllegalArgumentException("Workspace path is outside the configured Stage 10 owner roots");
                }
                if (args.size() != 1) throw new IllegalArgumentException("workspace.open accepts only the approved path argument");
            }
            default -> throw new IllegalArgumentException("Stage 10 Windows capability is not allowed: " + capability);
        }
        return new CommandGuardDecision(true, capability, action, "Least-privilege Stage 10 command validated; shell/admin/UAC bypass paths are not exposed");
    }

    public HostCommandEnvelope issueLeastPrivilegeCommand(LeastPrivilegeCommand request) {
        CommandGuardDecision guard = validateLeastPrivilegeCommand(request);
        return hostCommands.issue(new HostCommandRequest(request.hostId(), guard.capability(), guard.action(),
                request.arguments() == null ? Map.of() : Map.copyOf(request.arguments())));
    }

    public ResourceDecision govern(ResourceSnapshot snapshot, WorkloadRequest workload) {
        if (snapshot == null || workload == null) throw new IllegalArgumentException("Resource snapshot and workload are required");
        validatePercent(snapshot.cpuPct(), "cpuPct");
        validatePercent(snapshot.memoryPct(), "memoryPct");
        validatePercent(snapshot.vramPct(), "vramPct");
        String type = normalized(workload.type()).toUpperCase(Locale.ROOT);
        if (type.equals("PRIORITY_CONTROL")) return new ResourceDecision("ALLOW", 1, "Priority STOP/PAUSE/TAKE_CONTROL path is never queued behind model work");
        boolean criticalPressure = snapshot.cpuPct() >= 97 || snapshot.memoryPct() >= 96 || snapshot.vramPct() >= 97;
        if (criticalPressure) return new ResourceDecision(workload.deferrable() ? "DEFER" : "REJECT", 0, "Critical CPU/RAM/VRAM pressure");
        if (snapshot.speechActive() && HEAVY_WORKLOADS.contains(type)) return new ResourceDecision("DEFER", 0, "Active speech session has priority over heavy local inference/batch work");
        if (snapshot.uiInteractive() && HEAVY_WORKLOADS.contains(type) && snapshot.cpuPct() >= 75) return new ResourceDecision("DEFER", 0, "Interactive UI responsiveness is protected under elevated CPU load");
        if (snapshot.vramPct() >= 88 && type.startsWith("LOCAL_LLM")) return new ResourceDecision("DEFER", 0, "VRAM headroom is insufficient for another local-model workload");
        int cap = snapshot.cpuPct() >= 75 || snapshot.memoryPct() >= 82 || snapshot.vramPct() >= 82 ? 1 : 2;
        return new ResourceDecision("ALLOW", cap, "Workload fits the current Stage 10 resource envelope");
    }

    public SpeechBenchmarkResult evaluateSpeech(SpeechBenchmarkSample sample) {
        if (sample == null) throw new IllegalArgumentException("Speech benchmark sample is required");
        List<String> failures = new ArrayList<>();
        if (!sample.measuredOnTargetHardware()) failures.add("sample is not marked as measured on the target workstation");
        if (sample.captureToPartialMs() > 450) failures.add("capture-to-partial exceeds 450 ms target");
        if (sample.captureToFinalMs() > 1200) failures.add("capture-to-final exceeds 1200 ms target");
        if (sample.ttsFirstAudioMs() > 500) failures.add("TTS first-audio exceeds 500 ms target");
        if (sample.bargeInStopMs() > 250) failures.add("barge-in stop exceeds 250 ms target");
        if (sample.cpuPct() > 90) failures.add("speech benchmark CPU exceeds 90%");
        if (sample.gpuPct() > 97) failures.add("speech benchmark GPU exceeds 97%");
        if (sample.vramPct() > 95) failures.add("speech benchmark VRAM exceeds 95%");
        String verdict = !sample.measuredOnTargetHardware() ? "EVIDENCE_REQUIRED" : failures.isEmpty() ? "VERIFIED" : "FAIL";
        return new SpeechBenchmarkResult(verdict, List.copyOf(failures), sample);
    }

    public VaultReadiness vaultReadiness() {
        if (osVault.isEmpty()) return new VaultReadiness("HARDWARE_PENDING", "none", false,
                "No OperatingSystemCredentialVault implementation is active; environment/test vaults do not count as DPAPI validation");
        OperatingSystemCredentialVault vault = osVault.get();
        boolean backed = vault.hardwareBacked();
        return new VaultReadiness(backed ? "VERIFIED" : "CONNECTED_NOT_HARDWARE_BACKED", vault.backendName(), backed,
                backed ? "OS-backed vault reports hardware/OS protection" : "OS vault adapter is connected but has not proven hardware/OS-backed protection");
    }

    private void rejectDangerousArgumentText(Map<String, Object> args) {
        String value = args.toString().toLowerCase(Locale.ROOT);
        for (String token : List.of("powershell", "cmd.exe", "diskpart", "format ", "reg add", "runas", "schtasks", "wmic process call create", "bypass")) {
            if (value.contains(token)) throw new IllegalArgumentException("Stage 10 command contains a forbidden shell/admin token: " + token.trim());
        }
    }

    private void requireAction(String actual, String expected) {
        if (!actual.equals(expected)) throw new IllegalArgumentException("Capability requires action " + expected);
    }

    private String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null || String.valueOf(value).isBlank()) throw new IllegalArgumentException(key + " is required");
        return String.valueOf(value).trim();
    }

    private String normalized(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Capability/action/type is required");
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePercent(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 100) throw new IllegalArgumentException(name + " must be between 0 and 100");
    }

    private Set<String> parseCsv(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        Set<String> out = new HashSet<>();
        for (String item : raw.split(",")) if (!item.isBlank()) out.add(item.trim().toLowerCase(Locale.ROOT));
        return Set.copyOf(out);
    }

    private List<String> parseRoots(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> roots = new ArrayList<>();
        for (String item : raw.split(",")) if (!item.isBlank()) roots.add(normalizePath(item));
        return List.copyOf(roots);
    }

    private String normalizePath(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("path is required");
        String path = raw.trim().replace('\\', '/').replaceAll("/+", "/");
        if (path.equals("/") || path.matches("(?i)^[a-z]:/$")) throw new IllegalArgumentException("Filesystem root cannot be an owner workspace root");
        if (path.contains("/../") || path.endsWith("/..") || path.startsWith("../")) throw new IllegalArgumentException("Parent traversal is not allowed");
        while (path.endsWith("/") && path.length() > 3) path = path.substring(0, path.length() - 1);
        return path.toLowerCase(Locale.ROOT);
    }

    private boolean underRoot(String path, String root) {
        return path.equals(root) || path.startsWith(root + "/");
    }

    public record BootstrapPlan(String status, String serviceName, String privilegeModel, List<String> installSteps,
                                List<String> healthChecks, List<String> rollbackSteps) {}
    public record WorkstationProbe(String osName, int cpuCores, long ramMb, long vramMb, boolean nvidiaGpu,
                                   boolean microphoneDetected, boolean speakerDetected) {}
    public record WorkstationReadiness(String status, List<String> blockers, boolean windowsHostEligible,
                                       boolean speechEligible, boolean localInferenceEligible, boolean dpapiEligible,
                                       String detail) {}
    public record LeastPrivilegeCommand(UUID hostId, String capability, String action, Map<String, Object> arguments) {}
    public record CommandGuardDecision(boolean allowed, String capability, String action, String detail) {}
    public record ResourceSnapshot(double cpuPct, double memoryPct, double vramPct, boolean speechActive, boolean uiInteractive) {}
    public record WorkloadRequest(String type, int priority, boolean deferrable) {}
    public record ResourceDecision(String decision, int concurrencyCap, String reason) {}
    public record SpeechBenchmarkSample(long captureToPartialMs, long captureToFinalMs, long ttsFirstAudioMs,
                                        long bargeInStopMs, double cpuPct, double gpuPct, double vramPct,
                                        boolean measuredOnTargetHardware) {}
    public record SpeechBenchmarkResult(String verdict, List<String> failures, SpeechBenchmarkSample sample) {}
    public record VaultReadiness(String status, String backend, boolean hardwareBacked, String detail) {}
}
