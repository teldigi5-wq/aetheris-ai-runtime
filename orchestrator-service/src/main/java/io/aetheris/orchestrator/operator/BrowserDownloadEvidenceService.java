package io.aetheris.orchestrator.operator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

@Service
public class BrowserDownloadEvidenceService {

    private static final long DEFAULT_TIMEOUT_MILLIS = 15_000L;
    private static final long MAX_TIMEOUT_MILLIS = 30_000L;
    private static final long POLL_MILLIS = 100L;

    private final Path downloadRoot;
    private final long maxBytes;

    public BrowserDownloadEvidenceService(
            @Value("${aetheris.browser.download-root:build/browser-downloads}") String downloadRoot,
            @Value("${aetheris.browser.download-max-bytes:52428800}") long maxBytes) {
        String configuredRoot = downloadRoot == null || downloadRoot.isBlank()
                ? "build/browser-downloads"
                : downloadRoot.trim();
        this.downloadRoot = Path.of(configuredRoot).toAbsolutePath().normalize();
        if (maxBytes <= 0) throw new IllegalArgumentException("Browser download max bytes must be positive");
        this.maxBytes = maxBytes;
    }

    public Path createExecutionDirectory() {
        try {
            Path realRoot = ensureRoot();
            Path created = Files.createTempDirectory(realRoot, "run-").toRealPath();
            if (!created.startsWith(realRoot) || created.equals(realRoot)) {
                throw new IllegalStateException("Browser download execution directory escaped the configured root");
            }
            return created;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create isolated browser download directory", exception);
        }
    }

    public Path prepareExpectedTarget(Path executionDirectory, String expectedFileName) {
        Path target = resolveExpectedTarget(executionDirectory, expectedFileName);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Expected download target already exists; a fresh filename is required");
        }
        return target;
    }

    public VerifiedDownload awaitVerifiedDownload(
            Path executionDirectory,
            String expectedFileName,
            Duration requestedTimeout) {
        Path target = resolveExpectedTarget(executionDirectory, expectedFileName);
        long timeoutMillis = boundedTimeout(requestedTimeout);
        long deadline = System.nanoTime() + Duration.ofMillis(timeoutMillis).toNanos();
        long previousSize = -1L;
        long previousModified = -1L;
        int stableObservations = 0;

        while (System.nanoTime() <= deadline) {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                rejectUnsafeTarget(target);
                try {
                    long size = Files.size(target);
                    long modified = Files.getLastModifiedTime(target, LinkOption.NOFOLLOW_LINKS).toMillis();
                    if (size > maxBytes) {
                        throw new IllegalStateException("Downloaded file exceeds the configured evidence size limit");
                    }

                    if (size == previousSize && modified == previousModified) {
                        stableObservations++;
                    } else {
                        previousSize = size;
                        previousModified = modified;
                        stableObservations = 0;
                    }

                    if (stableObservations >= 1) {
                        String digest = sha256(target);
                        long finalSize = Files.size(target);
                        long finalModified = Files.getLastModifiedTime(target, LinkOption.NOFOLLOW_LINKS).toMillis();
                        if (finalSize == size && finalModified == modified) {
                            Path realRoot = ensureRoot();
                            Path realTarget = target.toRealPath(LinkOption.NOFOLLOW_LINKS);
                            if (!realTarget.startsWith(realRoot)) {
                                throw new IllegalStateException("Downloaded file escaped the configured evidence root");
                            }
                            String relative = realRoot.relativize(realTarget).toString().replace('\\', '/');
                            return new VerifiedDownload(relative, finalSize, digest);
                        }
                        previousSize = finalSize;
                        previousModified = finalModified;
                        stableObservations = 0;
                    }
                } catch (RuntimeException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new IllegalStateException("Unable to inspect downloaded-file evidence", exception);
                }
            }
            sleepPollInterval();
        }

        throw new IllegalStateException("Timed out waiting for the expected downloaded file");
    }

    public Path configuredRoot() {
        return downloadRoot;
    }

    public long maxBytes() {
        return maxBytes;
    }

    private Path resolveExpectedTarget(Path executionDirectory, String expectedFileName) {
        String filename = validateExpectedFileName(expectedFileName);
        try {
            Path realRoot = ensureRoot();
            if (executionDirectory == null) throw new IllegalArgumentException("Download execution directory is required");
            Path realExecution = executionDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!Files.isDirectory(realExecution, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(realExecution)
                    || !realExecution.startsWith(realRoot)
                    || realExecution.equals(realRoot)) {
                throw new IllegalArgumentException("Download execution directory is outside the configured root");
            }
            Path target = realExecution.resolve(filename).normalize();
            if (!target.getParent().equals(realExecution)) {
                throw new IllegalArgumentException("Expected download filename must remain inside the isolated execution directory");
            }
            return target;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to resolve browser download evidence path", exception);
        }
    }

    private String validateExpectedFileName(String expectedFileName) {
        String filename = expectedFileName == null ? "" : expectedFileName.trim();
        if (filename.isBlank()) throw new IllegalArgumentException("Expected download filename is required");
        if (filename.equals(".") || filename.equals("..")
                || filename.contains("/") || filename.contains("\\") || filename.contains(":")) {
            throw new IllegalArgumentException("Expected download filename must be a single path-safe filename");
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".crdownload") || lower.endsWith(".part") || lower.endsWith(".tmp")) {
            throw new IllegalArgumentException("Expected download filename must name the completed artifact, not a temporary download file");
        }
        if (filename.length() > 255) throw new IllegalArgumentException("Expected download filename is too long");
        return filename;
    }

    private void rejectUnsafeTarget(Path target) {
        if (Files.isSymbolicLink(target)) {
            throw new IllegalStateException("Symbolic-link downloads are not accepted as browser evidence");
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Downloaded artifact must be a regular file");
        }
    }

    private Path ensureRoot() {
        try {
            Files.createDirectories(downloadRoot);
            Path realRoot = downloadRoot.toRealPath();
            if (!Files.isDirectory(realRoot)) throw new IllegalStateException("Configured browser download root is not a directory");
            return realRoot;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to prepare configured browser download root", exception);
        }
    }

    private long boundedTimeout(Duration requestedTimeout) {
        long millis = requestedTimeout == null ? DEFAULT_TIMEOUT_MILLIS : requestedTimeout.toMillis();
        if (millis <= 0) millis = DEFAULT_TIMEOUT_MILLIS;
        return Math.min(millis, MAX_TIMEOUT_MILLIS);
    }

    private String sha256(Path target) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0L;
            byte[] buffer = new byte[16 * 1024];
            try (InputStream input = Files.newInputStream(target)) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    total += read;
                    if (total > maxBytes) {
                        throw new IllegalStateException("Downloaded file exceeds the configured evidence size limit");
                    }
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash downloaded-file evidence", exception);
        }
    }

    private void sleepPollInterval() {
        try {
            Thread.sleep(POLL_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for downloaded-file evidence", exception);
        }
    }

    public record VerifiedDownload(String relativePath, long sizeBytes, String sha256) {
        public VerifiedDownload {
            relativePath = relativePath == null ? "" : relativePath;
            sha256 = sha256 == null ? "" : sha256;
        }
    }
}
