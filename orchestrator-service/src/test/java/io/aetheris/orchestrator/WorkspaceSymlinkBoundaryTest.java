package io.aetheris.orchestrator;

import io.aetheris.orchestrator.execution.WorkspaceSandboxService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceSymlinkBoundaryTest {
    @TempDir Path temp;

    private WorkspaceSandboxService service(Path workspace, boolean writable) {
        return new WorkspaceSandboxService(workspace.toString(), writable, 1024);
    }

    private void link(Path at, Path target) {
        try {
            Files.createSymbolicLink(at, target);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Symbolic links unsupported in this test environment");
        }
    }

    @Test void blocksOutsideReadsAndWritesThroughDirectoryAndFileLinks() throws Exception {
        Path home = Files.createDirectory(temp.resolve("home"));
        Path outside = Files.createDirectory(temp.resolve("outside"));
        Files.writeString(outside.resolve("private.txt"), "DO_NOT_READ");
        link(home.resolve("escape"), outside);
        link(home.resolve("private-link"), outside.resolve("private.txt"));
        WorkspaceSandboxService workspace = service(home, true);

        assertThrows(IllegalArgumentException.class, () -> workspace.read("escape/private.txt"));
        assertThrows(IllegalArgumentException.class, () -> workspace.read("private-link"));
        assertThrows(IllegalArgumentException.class, () -> workspace.write("escape/new-file.txt", "data"));
        assertThrows(IllegalArgumentException.class, () -> workspace.write("private-link", "overwritten"));
        assertThrows(IllegalArgumentException.class, () -> workspace.resolveDirectory("escape"));

        assertEquals("DO_NOT_READ", Files.readString(outside.resolve("private.txt")));
        assertFalse(Files.exists(outside.resolve("new-file.txt")));
    }

    @Test void allowsValidInternalDirectoryLinkButNotLinkedWriteTarget() throws Exception {
        Path home = Files.createDirectory(temp.resolve("home"));
        Path inside = Files.createDirectory(home.resolve("inside"));
        Files.writeString(inside.resolve("old.txt"), "safe");
        link(home.resolve("internal-dir"), inside);
        link(home.resolve("internal-file"), inside.resolve("old.txt"));
        WorkspaceSandboxService workspace = service(home, true);

        assertEquals("safe", workspace.read("internal-dir/old.txt").content());
        assertEquals("safe", workspace.read("internal-file").content());
        workspace.write("internal-dir/new.txt", "new");
        assertEquals("new", Files.readString(inside.resolve("new.txt")));
        assertThrows(IllegalArgumentException.class, () -> workspace.write("internal-file", "forbidden"));
        assertEquals(home.toRealPath().resolve("inside"), workspace.resolveDirectory("internal-dir").toRealPath());
    }

    @Test void rejectsBrokenLinksAndLexicalTraversalWithoutCreatingOutsideData() throws Exception {
        Path home = Files.createDirectory(temp.resolve("home"));
        link(home.resolve("broken"), temp.resolve("missing-outside-file"));
        WorkspaceSandboxService workspace = service(home, true);

        assertThrows(IllegalArgumentException.class, () -> workspace.read("broken"));
        assertThrows(IllegalArgumentException.class, () -> workspace.write("broken", "data"));
        assertThrows(IllegalArgumentException.class, () -> workspace.write("../escape.txt", "data"));
        assertThrows(IllegalArgumentException.class, () -> workspace.read(temp.resolve("outside").toString()));
        assertFalse(Files.exists(temp.resolve("escape.txt")));
    }

    @Test void keepsOrdinaryReadsWritesAndDisabledWritesWorking() throws Exception {
        Path home = Files.createDirectory(temp.resolve("home"));
        WorkspaceSandboxService writable = service(home, true);
        writable.write("nested/new.txt", "created");
        assertEquals("created", writable.read("nested/new.txt").content());
        assertEquals(home.toRealPath().resolve("nested"), writable.resolveDirectory("nested"));
        assertThrows(IllegalStateException.class, () -> service(home, false).write("nested/new.txt", "blocked"));
    }
}
