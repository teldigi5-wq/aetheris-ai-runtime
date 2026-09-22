package io.aetheris.workstation;

import java.awt.Desktop;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

public final class LeastPrivilegeDispatcher {
    private final AgentConfig config;

    public LeastPrivilegeDispatcher(AgentConfig config) { this.config = Objects.requireNonNull(config); }

    public void validate(HostCommandEnvelope envelope) {
        String capability = normalized(envelope.capability());
        String action = normalized(envelope.action());
        Map<String, Object> args = envelope.arguments();
        switch (capability) {
            case "system.telemetry" -> {
                requireAction(action, "snapshot"); requireKeys(args, Set.of());
            }
            case "process.status" -> {
                requireAction(action, "status"); requireKeys(args, Set.of("processName"));
                safeName(stringArg(args, "processName"), "processName");
            }
            case "app.launch" -> {
                requireAction(action, "launch"); requireKeys(args, Set.of("appAlias"));
                String alias = safeName(stringArg(args, "appAlias"), "appAlias").toLowerCase(Locale.ROOT);
                if (!config.allowedApps().containsKey(alias)) throw new SecurityException("Application alias is not allowlisted");
            }
            case "workspace.open" -> {
                requireAction(action, "open"); requireKeys(args, Set.of("path"));
                checkedWorkspacePath(stringArg(args, "path"), false);
            }
            default -> throw new SecurityException("Unsupported host capability");
        }
    }

    public Map<String, Object> execute(HostCommandEnvelope envelope) throws Exception {
        validate(envelope);
        return switch (normalized(envelope.capability())) {
            case "system.telemetry" -> telemetry();
            case "process.status" -> processStatus(stringArg(envelope.arguments(), "processName"));
            case "app.launch" -> launch(stringArg(envelope.arguments(), "appAlias"));
            case "workspace.open" -> openWorkspaceFile(stringArg(envelope.arguments(), "path"));
            default -> throw new SecurityException("Unsupported host capability");
        };
    }

    private Map<String, Object> telemetry() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("capability", "system.telemetry");
        result.put("logicalProcessors", runtime.availableProcessors());
        result.put("jvmTotalMemoryBytes", runtime.totalMemory());
        result.put("jvmFreeMemoryBytes", runtime.freeMemory());
        var base = ManagementFactory.getOperatingSystemMXBean();
        result.put("osName", base.getName());
        result.put("osVersion", base.getVersion());
        result.put("systemLoadAverage", base.getSystemLoadAverage());
        if (base instanceof com.sun.management.OperatingSystemMXBean os) {
            result.put("cpuLoad", os.getCpuLoad());
            result.put("totalMemoryBytes", os.getTotalMemorySize());
            result.put("freeMemoryBytes", os.getFreeMemorySize());
        }
        result.put("gpuTelemetry", "VENDOR_ADAPTER_NOT_CONNECTED");
        return result;
    }

    private Map<String, Object> processStatus(String requested) {
        String target = requested.toLowerCase(Locale.ROOT);
        List<Long> pids = new ArrayList<>();
        ProcessHandle.allProcesses().forEach(process -> {
            String command = process.info().command().orElse("");
            if (command.isBlank()) return;
            try {
                String name = Path.of(command).getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.equals(target) && pids.size() < 20) pids.add(process.pid());
            } catch (Exception ignored) {}
        });
        return Map.of("status", "OK", "processName", requested, "running", !pids.isEmpty(), "pids", List.copyOf(pids));
    }

    private Map<String, Object> launch(String aliasValue) throws Exception {
        String alias = aliasValue.toLowerCase(Locale.ROOT);
        Path executable = config.allowedApps().get(alias);
        if (executable == null || !Files.isRegularFile(executable)) throw new IllegalStateException("Allowlisted application executable is unavailable");
        Process process = new ProcessBuilder(executable.toString()).start();
        return Map.of("status", "LAUNCHED", "appAlias", alias, "pid", process.pid());
    }

    private Map<String, Object> openWorkspaceFile(String value) throws Exception {
        Path file = checkedWorkspacePath(value, true);
        if (!Files.isRegularFile(file)) throw new IllegalArgumentException("Workspace file does not exist or is not a regular file");
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            throw new IllegalStateException("Desktop file-open integration is unavailable");
        }
        Desktop.getDesktop().open(file.toFile());
        return Map.of("status", "OPENED", "path", file.toString());
    }

    private Path checkedWorkspacePath(String value, boolean resolveRealPath) {
        if (value == null || value.isBlank() || value.indexOf('\0') >= 0) throw new IllegalArgumentException("Workspace path is invalid");
        Path candidate = Path.of(value).toAbsolutePath().normalize();
        boolean inside = config.workspaceRoots().stream().anyMatch(root -> candidate.startsWith(root.toAbsolutePath().normalize()));
        if (!inside) throw new SecurityException("Workspace path is outside approved roots");
        if (!resolveRealPath) return candidate;
        try {
            Path real = candidate.toRealPath();
            boolean realInside = false;
            for (Path root : config.workspaceRoots()) {
                if (Files.exists(root) && real.startsWith(root.toRealPath())) { realInside = true; break; }
            }
            if (!realInside) throw new SecurityException("Workspace path escapes approved root through filesystem indirection");
            return real;
        } catch (SecurityException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("Unable to resolve workspace path", e); }
    }

    private void requireAction(String actual, String expected) {
        if (!actual.equals(expected)) throw new SecurityException("Unsupported host action");
    }

    private void requireKeys(Map<String, Object> args, Set<String> allowed) {
        if (!args.keySet().equals(allowed)) throw new SecurityException("Host command arguments do not match the capability contract");
    }

    private String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(key + " is required");
        return text.trim();
    }

    private String safeName(String value, String label) {
        if (!value.matches("[A-Za-z0-9._ -]{1,96}")) throw new IllegalArgumentException(label + " is invalid");
        String lower = value.toLowerCase(Locale.ROOT);
        for (String forbidden : List.of("powershell", "cmd.exe", "diskpart", "format", "runas", "schtasks", "reg.exe", "wmic")) {
            if (lower.contains(forbidden)) throw new SecurityException(label + " contains a forbidden executable/tool name");
        }
        return value;
    }

    private String normalized(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Host capability/action is required");
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
