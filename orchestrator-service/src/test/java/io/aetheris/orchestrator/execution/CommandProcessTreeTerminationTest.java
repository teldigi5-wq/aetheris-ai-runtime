package io.aetheris.orchestrator.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class CommandProcessTreeTerminationTest {
    @TempDir Path temp;

    @Test void timeoutAttemptsToKillSpawnedGrandchildBeforeReturning() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        WorkspaceSandboxService sandbox = new WorkspaceSandboxService(workspace.toString(), false, 1024);
        CommandSandboxService runner = new CommandSandboxService(sandbox, "java", 6, 4000);
        Path testClasses = Path.of(Spawner.class.getProtectionDomain().getCodeSource().getLocation().toURI());

        CommandExecutionResult result = runner.execute(
                List.of("java", "-cp", testClasses.toString(), Spawner.class.getName()), "", 5);

        assertTrue(result.timedOut(), "A long-running subprocess must time out");
        assertEquals(-1, result.exitCode());
        Matcher id = Pattern.compile("CHILD_PID=(\\d+)").matcher(result.output());
        assertTrue(id.find(), "Spawner must prove the child actually started: " + result.output());
        long childPid = Long.parseLong(id.group(1));

        // Process termination/reaping is asynchronous on supported host OSes.
        boolean ended = false;
        for (int attempt = 0; attempt < 30; attempt++) {
            if (ProcessHandle.of(childPid).isEmpty() || !ProcessHandle.of(childPid).orElseThrow().isAlive()) {
                ended = true;
                break;
            }
            Thread.sleep(100);
        }
        assertTrue(ended, "Best-effort process-tree cancellation must kill a running child");
    }

    public static class Spawner {
        public static void main(String[] args) throws Exception {
            String exe = Path.of(System.getProperty("java.home"), "bin",
                    File.separatorChar == '\\' ? "java.exe" : "java").toString();
            Process child = new ProcessBuilder(exe, "-cp", System.getProperty("java.class.path"),
                    Sleeper.class.getName()).start();
            System.out.println("CHILD_PID=" + child.pid());
            System.out.flush();
            Thread.sleep(Duration.ofMinutes(1));
        }
    }

    public static class Sleeper {
        public static void main(String[] args) throws Exception {
            Thread.sleep(Duration.ofMinutes(1));
        }
    }
}
