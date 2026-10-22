package io.aetheris.orchestrator.execution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.File;
import java.util.Map;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class CommandSandboxService {

    private final WorkspaceSandboxService workspace;
    private final Set<String> allowedCommands;
    private final Duration defaultTimeout;
    private final int maxOutputChars;

    public CommandSandboxService(
            WorkspaceSandboxService workspace,
            @Value("${aetheris.execution.allowed-commands:}") String allowedCommands,
            @Value("${aetheris.execution.command-timeout-seconds:30}") long timeoutSeconds,
            @Value("${aetheris.execution.max-command-output-chars:12000}") int maxOutputChars) {
        this.workspace = workspace;
        this.allowedCommands = parseCsv(allowedCommands);
        this.defaultTimeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.maxOutputChars = Math.max(1000, maxOutputChars);
    }

    public CommandExecutionResult execute(List<String> command, String workingDirectory, Integer timeoutSeconds) {
        List<String> safeCommand = normalizeCommand(command);
        Path directory = workspace.resolveDirectory(workingDirectory);
        Duration timeout = timeoutSeconds == null
                ? defaultTimeout
                : Duration.ofSeconds(Math.max(1, Math.min(timeoutSeconds, 120)));

        Process process;
        try {
            process = createIsolatedProcess(safeCommand, directory).start();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start allowlisted command", exception);
        }

        StringBuffer output = new StringBuffer();
        Thread reader = Thread.ofVirtual().name("aetheris-command-output").start(() -> drain(process, output));
        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                terminateProcessTree(process);
                // Give the OS a bounded opportunity to reap the killed parent.
                process.waitFor(1500, TimeUnit.MILLISECONDS);
            }
            reader.join(2000);
        } catch (InterruptedException exception) {
            terminateProcessTree(process);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Command execution interrupted", exception);
        }

        int exitCode = finished ? process.exitValue() : -1;
        return new CommandExecutionResult(
                safeCommand,
                workspace.root().relativize(directory).toString(),
                exitCode,
                !finished,
                output.toString());
    }


    // The orchestrator may hold API keys, model credentials, database passwords
    // and worker control secrets. None may be inherited by a command subprocess.
    // Restrict both inherited environment and PATH lookup, even for opted-in tools.
    ProcessBuilder createIsolatedProcess(List<String> command, Path directory) {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true);
        isolateChildEnvironment(builder.environment(), workspace.root());
        return builder;
    }

    static void isolateChildEnvironment(Map<String, String> environment, Path workspaceRoot) {
        String path = lookupEnvironment(environment, "PATH");
        String pathExt = lookupEnvironment(environment, "PATHEXT");
        String systemRoot = lookupEnvironment(environment, "SystemRoot");
        String windir = lookupEnvironment(environment, "WINDIR");
        environment.clear();

        if (path != null && !path.isBlank()) {
            StringBuilder safePath = new StringBuilder();
            for (String component : path.split(java.util.regex.Pattern.quote(File.pathSeparator), -1)) {
                if (component.isBlank()) continue; // Empty/relative entries can select the current working directory.
                try {
                    Path directory = Path.of(component);
                    if (!directory.isAbsolute() || !Files.isDirectory(directory)) continue;
                    // Strip workspace-controlled executable directories, including symlinks.
                    if (directory.toRealPath().startsWith(workspaceRoot)) continue;
                    if (!safePath.isEmpty()) safePath.append(File.pathSeparator);
                    safePath.append(directory.toRealPath());
                } catch (RuntimeException | IOException ignored) {
                    // Invalid/unknown/unreadable lookup directories are not trusted.
                }
            }
            if (!safePath.isEmpty()) environment.put("PATH", safePath.toString());
        }
        // Windows requires SystemRoot for reliable process creation; PATHEXT can
        // be required for explicit operator-approved native commands.
        if (File.separatorChar == '\\') {
            if (systemRoot != null && !systemRoot.isBlank()) environment.put("SystemRoot", systemRoot);
            if (windir != null && !windir.isBlank()) environment.put("WINDIR", windir);
            if (pathExt != null && !pathExt.isBlank()) environment.put("PATHEXT", pathExt);
        }
    }

    private static String lookupEnvironment(Map<String, String> environment, String name) {
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return null;
    }

    private List<String> normalizeCommand(List<String> command) {
        if (command == null || command.isEmpty()) throw new IllegalArgumentException("Command is required");
        List<String> normalized = new ArrayList<>();
        for (String part : command) {
            if (part == null) throw new IllegalArgumentException("Command arguments cannot be null");
            normalized.add(part);
        }
        String requested = normalized.getFirst();
        // Reject path-based executable impersonation, including Windows separators on Linux.
        // This is a host process launcher, not an OS sandbox: default is no executable.
        if (requested.isBlank() || !requested.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("Command executable must be an unqualified allowlisted name");
        }
        String executable = requested.toLowerCase(Locale.ROOT);
        if (executable.endsWith(".exe")) executable = executable.substring(0, executable.length() - 4);
        if (!allowedCommands.contains(executable)) {
            throw new IllegalArgumentException("Command is not allowlisted: " + executable);
        }
        return List.copyOf(normalized);
    }

    /**
     * Best-effort termination for the observed process tree. A privileged OS-isolated
     * worker is still necessary: detached children and concurrently spawned new
     * descendants may escape a userspace ProcessHandle snapshot.
     */
    static void terminateProcessTree(Process process) {
        List<ProcessHandle> children;
        try (java.util.stream.Stream<ProcessHandle> discovered = process.toHandle().descendants()) {
            children = discovered.toList();
        } catch (RuntimeException exception) {
            children = List.of(); // Still terminate the direct process on enumeration failure.
        }
        process.destroyForcibly(); // Prevent the original process from launching more children.
        for (ProcessHandle child : children) {
            try {
                if (child.isAlive()) child.destroyForcibly();
            } catch (RuntimeException ignored) {
                // OS controls and process exit races can prevent terminating a descendant.
                // A container/VM-level PID namespace and cgroup remain the release gate.
            }
        }
    }

    private void drain(Process process, StringBuffer output) {
        try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() < maxOutputChars) {
                    int remaining = maxOutputChars - output.length();
                    String addition = line + System.lineSeparator();
                    output.append(addition, 0, Math.min(remaining, addition.length()));
                }
            }
        } catch (IOException ignored) {
            // Process termination can close the stream while the reader drains it.
        }
    }

    private Set<String> parseCsv(String csv) {
        Set<String> values = new HashSet<>();
        for (String value : csv.split(",")) {
            if (!value.isBlank()) values.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(values);
    }
}
