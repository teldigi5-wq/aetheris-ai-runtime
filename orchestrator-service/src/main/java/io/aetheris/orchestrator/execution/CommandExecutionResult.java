package io.aetheris.orchestrator.execution;

import java.util.List;

public record CommandExecutionResult(
        List<String> command,
        String workingDirectory,
        int exitCode,
        boolean timedOut,
        String output
) {
    public CommandExecutionResult {
        command = command == null ? List.of() : List.copyOf(command);
    }

    public boolean passed() {
        return !timedOut && exitCode == 0;
    }
}
