package io.aetheris.orchestrator.execution;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Defensive regression, not an OS-enforced container or a race-free proof.
 * Symlink tests run on Linux CI; Windows environments without symlink
 * privileges skip only the link-specific assertions.
 */
class WorkspaceContainmentHardeningTest {
    @TempDir Path sandboxParent;

    private WorkspaceSandboxService sandbox(boolean writeEnabled) {
        return new WorkspaceSandboxService(sandboxParent.resolve("workspace").toString(),
                writeEnabled, 4096);
    }

    @Test
    void permitsOrdinaryBoundedReadAndExplicitOptInWrite() {
        var workspace = sandbox(true);
        workspace.write("nested/notes.txt", "safe\n");
        var read = workspace.read("nested/notes.txt");
        assertThat(read.content()).isEqualTo("safe\n");
        assertThat(read.size()).isEqualTo(5);
        assertThat(workspace.resolveDirectory("nested")).isDirectory();
        assertThatThrownBy(() -> workspace.read("../unrelated.txt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workspace.read("/etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesMutatingFilesOrRunningSubprocessesByDefault() {
        var workspace = sandbox(false);
        assertThatThrownBy(() -> workspace.write("notes.txt", "secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
        var commands = new CommandSandboxService(workspace, "node,java,git", 2, 1000, false);
        assertThatThrownBy(() -> commands.execute(
                List.of("node", "-e", "console.log('should never run')"), "", 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void rejectsSymlinkedFileAndDirectoryReadWriteAndCwd() throws Exception {
        var workspace = sandbox(true);
        Path outside = Files.createDirectory(sandboxParent.resolve("sensitive-outside"));
        Path secret = outside.resolve("private.txt");
        Files.writeString(secret, "PRIVATE_DO_NOT_EXPOSE", StandardCharsets.UTF_8);
        Path root = workspace.root();
        Path linkToFile = root.resolve("linked-secret.txt");
        Path linkToDir = root.resolve("linked-folder");
        try {
            Files.createSymbolicLink(linkToFile, secret);
            Files.createSymbolicLink(linkToDir, outside);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException ex) {
            Assumptions.assumeTrue(false, "Filesystem cannot create symlink for this test");
        }
        assertThatThrownBy(() -> workspace.read("linked-secret.txt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workspace.write("linked-secret.txt", "replaced"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workspace.read("linked-folder/private.txt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workspace.write("linked-folder/new.txt", "unsafe"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workspace.resolveDirectory("linked-folder"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readString(secret)).isEqualTo("PRIVATE_DO_NOT_EXPOSE");
        assertThat(Files.exists(outside.resolve("new.txt"))).isFalse();
    }

    @Test
    void refusesSymlinkedConfiguredRoot() throws Exception {
        Path outside=Files.createDirectory(sandboxParent.resolve("external-real-root"));
        Path link=sandboxParent.resolve("linked-root");
        try {
            Files.createSymbolicLink(link,outside);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException ex) {
            Assumptions.assumeTrue(false, "Filesystem cannot create symlink for this test");
        }
        assertThatThrownBy(() -> new WorkspaceSandboxService(link.toString(), false, 4096))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Symbolic");
    }

    @Test
    void fileSizeLimitsAlsoRejectExcessiveContent() throws Exception {
        var workspace = sandbox(true);
        assertThatThrownBy(() -> workspace.write("huge.txt", "x".repeat(5000)))
                .isInstanceOf(IllegalArgumentException.class);
        Files.writeString(workspace.root().resolve("preexisting.txt"), "x".repeat(5000));
        assertThatThrownBy(() -> workspace.read("preexisting.txt"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
