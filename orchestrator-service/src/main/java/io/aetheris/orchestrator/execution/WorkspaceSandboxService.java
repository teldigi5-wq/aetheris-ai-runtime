package io.aetheris.orchestrator.execution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Service
public class WorkspaceSandboxService {

    // Canonicalize the operator-selected root exactly once, then validate all
    // existing path components against it before filesystem access.
    private final Path root;
    private final boolean writeEnabled;
    private final long maxFileBytes;

    public WorkspaceSandboxService(
            @Value("${aetheris.execution.workspace-root:./workspace}") String workspaceRoot,
            @Value("${aetheris.execution.workspace-write-enabled:false}") boolean writeEnabled,
            @Value("${aetheris.execution.max-file-bytes:1048576}") long maxFileBytes) {
        Path configuredRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
        this.writeEnabled = writeEnabled;
        this.maxFileBytes = maxFileBytes;
        try {
            Files.createDirectories(configuredRoot);
            this.root = configuredRoot.toRealPath();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize Aetheris workspace sandbox", exception);
        }
    }

    public Path root() {
        return root;
    }

    public WorkspaceFileResult read(String relativePath) {
        Path path = resolveFile(relativePath);
        try {
            if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Workspace file does not exist: " + relativePath);
            long size = Files.size(path);
            if (size > maxFileBytes) throw new IllegalArgumentException("Workspace file exceeds the configured read limit");
            // Revalidate immediately before the read. This is defense in depth,
            // not an atomic guarantee against concurrent symlink replacement.
            validateExistingComponents(path);
            return new WorkspaceFileResult(root.relativize(path).toString(), size, Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read workspace file", exception);
        }
    }

    public WorkspaceWriteResult write(String relativePath, String content) {
        if (!writeEnabled) throw new IllegalStateException("Workspace writes are disabled by configuration");
        Path path = resolveFile(relativePath);
        byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxFileBytes) throw new IllegalArgumentException("Workspace write exceeds the configured file limit");
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            validateExistingComponents(path);
            // Never overwrite a final-component symlink, even if it currently
            // resolves within the workspace. Intermediate links must stay inside.
            if (Files.isSymbolicLink(path)) throw new IllegalArgumentException("Workspace write target cannot be a symbolic link");
            Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            return new WorkspaceWriteResult(root.relativize(path).toString(), bytes.length);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write workspace file", exception);
        }
    }

    public Path resolveDirectory(String relativeDirectory) {
        Path path = relativeDirectory == null || relativeDirectory.isBlank() ? root : resolve(relativeDirectory);
        if (!Files.isDirectory(path)) throw new IllegalArgumentException("Working directory does not exist inside sandbox: " + relativeDirectory);
        validateExistingComponents(path);
        return path;
    }

    private Path resolveFile(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) throw new IllegalArgumentException("Workspace path is required");
        return resolve(relativePath);
    }

    private Path resolve(String rawPath) {
        Path relative = Path.of(rawPath);
        if (relative.isAbsolute()) throw new IllegalArgumentException("Absolute paths are not allowed in the workspace sandbox");
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("Path escapes the configured workspace sandbox");
        validateExistingComponents(resolved);
        return resolved;
    }

    private void validateExistingComponents(Path resolved) {
        Path cursor = root;
        // Check each existing ancestor, including the target when present.
        // NOFOLLOW_LINKS ensures broken symlinks also receive explicit scrutiny.
        for (Path segment : root.relativize(resolved)) {
            cursor = cursor.resolve(segment);
            if (!Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) continue;
            try {
                if (!cursor.toRealPath().startsWith(root)) {
                    throw new IllegalArgumentException("Workspace path escapes via symbolic link");
                }
            } catch (IOException error) {
                throw new IllegalArgumentException("Workspace path cannot be safely resolved", error);
            }
        }
    }
}
