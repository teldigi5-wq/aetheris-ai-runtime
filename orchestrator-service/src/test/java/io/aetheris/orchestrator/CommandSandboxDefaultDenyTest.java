package io.aetheris.orchestrator;

import io.aetheris.orchestrator.execution.CommandSandboxService;
import io.aetheris.orchestrator.execution.WorkspaceSandboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandSandboxDefaultDenyTest {
    @TempDir Path tmp;

    private CommandSandboxService commands(String allowed) {
        WorkspaceSandboxService workspace = new WorkspaceSandboxService(tmp.toString(), false, 1024);
        return new CommandSandboxService(workspace, allowed, 5, 2000);
    }

    @Test void blankAllowlistDeniesCodeRunnersByDefault() {
        CommandSandboxService disabled = commands("");
        for (String name : List.of("java", "node", "npm", "git", "mvn", "python", "sh")) {
            IllegalArgumentException denied = assertThrows(IllegalArgumentException.class,
                    () -> disabled.execute(List.of(name, "-version"), "", 1), name);
            assertTrue(denied.getMessage().contains("not allowlisted"), name);
        }
    }

    @Test void bareCommandOptInCannotBeReplacedByAbsoluteOrRelativeExecutable() {
        CommandSandboxService enabled = commands("java");
        for (String name : List.of("./java", "../java", "/usr/bin/java",
                "/tmp/attacker/java", "C:\\attack\\java.exe", ".\\java.exe",
                "java/../java", "")) {
            assertThrows(IllegalArgumentException.class,
                    () -> enabled.execute(List.of(name, "-version"), "", 1), name);
        }
    }

    @Test void explicitAllowlistStillRejectsOtherCommands() {
        CommandSandboxService enabled = commands("java");
        assertThrows(IllegalArgumentException.class,
                () -> enabled.execute(List.of("node", "-e", "process.exit(0)"), "", 1));
    }
}
