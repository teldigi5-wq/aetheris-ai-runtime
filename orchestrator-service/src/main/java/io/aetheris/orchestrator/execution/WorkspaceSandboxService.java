package io.aetheris.orchestrator.execution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.nio.channels.SeekableByteChannel;
import java.util.Set;

@Service
public class WorkspaceSandboxService {

    private final Path root;
    private final boolean writeEnabled;
    private final long maxFileBytes;

    public WorkspaceSandboxService(
            @Value("${aetheris.execution.workspace-root:./workspace}") String workspaceRoot,
            @Value("${aetheris.execution.workspace-write-enabled:false}") boolean writeEnabled,
            @Value("${aetheris.execution.max-file-bytes:1048576}") long maxFileBytes) {
        this.root = Path.of(workspaceRoot).toAbsolutePath().normalize();
        this.writeEnabled = writeEnabled;
        this.maxFileBytes = maxFileBytes;
        try {
            // Refuse symlinked sandbox roots, including ancestor components.
            rejectSymlinkComponents(this.root);
            Files.createDirectories(this.root);
            rejectSymlinkComponents(this.root);
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
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                throw new IllegalArgumentException("Workspace file is not a regular, unlinked file");
            long size = Files.size(path);
            if (size > maxFileBytes) throw new IllegalArgumentException("Workspace file exceeds the configured read limit");
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
            if (parent != null) {
                rejectSymlinkComponents(parent);
                Files.createDirectories(parent);
                rejectSymlinkComponents(parent);
            }
            rejectSymlinkComponents(path);
            // NOFOLLOW_LINKS prevents a last-component swap into a symlink.
            // The untrusted parent path race still requires OS sandboxing
            // or SecureDirectoryStream for high-assurance use.
            try (SeekableByteChannel channel = Files.newByteChannel(path,
                    Set.<OpenOption>of(StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            LinkOption.NOFOLLOW_LINKS))) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
            }
            return new WorkspaceWriteResult(root.relativize(path).toString(), bytes.length);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write workspace file", exception);
        }
    }

    public Path resolveDirectory(String relativeDirectory) {
        Path path = relativeDirectory == null || relativeDirectory.isBlank() ? root : resolve(relativeDirectory);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("Working directory unavailable inside sandbox");
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
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("Workspace path rejected");
        rejectSymlinkComponents(resolved);
        return resolved;
    }

    /**
     * Defense in depth: refuse any existing symbolic-link component, not
     * merely lexical '..' traversal. This alone is not an OS sandbox and
     * cannot make concurrent parent renames or hardlinks race-free.
     */
    private static void rejectSymlinkComponents(Path path) {
        Path part = path.getRoot();
        for (Path element : path) {
            part = part == null ? element : part.resolve(element);
            if (Files.isSymbolicLink(part)) {
                throw new IllegalArgumentException("Symbolic links are not permitted in the workspace");
            }
        }
    }
}
