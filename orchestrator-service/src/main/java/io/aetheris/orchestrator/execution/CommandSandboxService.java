package io.aetheris.orchestrator.execution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
            @Value("${aetheris.execution.allowed-commands:git,mvn,npm,node,java,javac}") String allowedCommands,
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
            process = new ProcessBuilder(safeCommand)
                    .directory(directory.toFile())
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start allowlisted command", exception);
        }

        StringBuilder output = new StringBuilder();
        Thread reader = Thread.ofVirtual().name("aetheris-command-output").start(() -> drain(process, output));
        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) process.destroyForcibly();
            reader.join(2000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
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

    private List<String> normalizeCommand(List<String> command) {
        if (command == null || command.isEmpty()) throw new IllegalArgumentException("Command is required");
        List<String> normalized = new ArrayList<>();
        for (String part : command) {
            if (part == null) throw new IllegalArgumentException("Command arguments cannot be null");
            normalized.add(part);
        }
        String executable = Path.of(normalized.getFirst()).getFileName().toString().toLowerCase(Locale.ROOT);
        if (executable.endsWith(".exe")) executable = executable.substring(0, executable.length() - 4);
        if (!allowedCommands.contains(executable)) {
            throw new IllegalArgumentException("Command is not allowlisted: " + executable);
        }
        return List.copyOf(normalized);
    }

    private void drain(Process process, StringBuilder output) {
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
