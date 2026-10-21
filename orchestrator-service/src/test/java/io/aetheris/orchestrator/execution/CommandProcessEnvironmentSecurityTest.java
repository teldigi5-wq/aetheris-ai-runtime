package io.aetheris.orchestrator.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CommandProcessEnvironmentSecurityTest {
    @TempDir Path temp;

    @Test void wipesCredentialAndInjectionVariablesFromChildEvenWhenOptedIn() throws Exception {
        Path workspace = Files.createDirectories(temp.resolve("workspace"));
        Path safe = Files.createDirectories(temp.resolve("bin"));
        Path inside = Files.createDirectories(workspace.resolve("bin"));
        Map<String,String> environment = new HashMap<>();
        environment.put("PATH", File.pathSeparator + inside + File.pathSeparator + safe + File.pathSeparator + "." + File.pathSeparator + "/nonexistent_path");
        environment.put("SPRING_DATASOURCE_PASSWORD", "DATABASE_SECRET_MUST_NOT_LEAK");
        environment.put("AETHERIS_GITHUB_TOKEN", "TOKEN_MUST_NOT_LEAK");
        environment.put("JAVA_TOOL_OPTIONS", "-Dsecret=FORBIDDEN");
        environment.put("_JAVA_OPTIONS", "-Dsecret=FORBIDDEN");
        environment.put("NODE_OPTIONS", "--require=malicious.js");
        environment.put("NPM_CONFIG_USERCONFIG", "/tmp/creds");
        environment.put("LD_PRELOAD", "/tmp/inject.so");
        environment.put("HOME", "/home/owner");
        environment.put("SystemRoot", "C:\\Windows");
        CommandSandboxService.isolateChildEnvironment(environment, workspace.toRealPath());
        assertFalse(environment.keySet().stream().anyMatch(key ->
                key.contains("PASSWORD") || key.contains("TOKEN") || key.contains("JAVA_OPTIONS")
                        || key.equals("NODE_OPTIONS") || key.equals("LD_PRELOAD")
                        || key.equals("NPM_CONFIG_USERCONFIG") || key.equals("HOME")));
        assertTrue(environment.keySet().stream().allMatch(
                key -> Set.of("PATH","PATHEXT","SystemRoot","WINDIR").contains(key)));
        assertEquals(safe.toRealPath().toString(), environment.get("PATH"));
    }

    @Test void realOptedInProcessBuilderHasOnlyMinimumEnvironment() throws Exception {
        Path workspace = Files.createDirectories(temp.resolve("my-workspace"));
        WorkspaceSandboxService sandbox = new WorkspaceSandboxService(workspace.toString(), false, 1024);
        CommandSandboxService command = new CommandSandboxService(sandbox, "java", 5, 1200);
        ProcessBuilder process = command.createIsolatedProcess(List.of("java", "-version"), sandbox.root());
        assertEquals(sandbox.root().toFile(), process.directory());
        assertTrue(process.environment().keySet().stream().allMatch(
                key -> Set.of("PATH","PATHEXT","SystemRoot","WINDIR").contains(key)));
        assertFalse(process.environment().containsKey("JAVA_TOOL_OPTIONS"));
        assertFalse(process.environment().containsKey("SPRING_DATASOURCE_PASSWORD"));
    }
}
