package io.aetheris.orchestrator;

import io.aetheris.orchestrator.operator.BrowserDownloadEvidenceService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrowserDownloadEvidenceServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void completedArtifactProducesBoundedSha256Evidence() throws Exception {
        BrowserDownloadEvidenceService service = service(1024);
        Path execution = service.createExecutionDirectory();
        Path target = service.prepareExpectedTarget(execution, "report.txt");
        Files.writeString(target, "hello");

        var evidence = service.awaitVerifiedDownload(execution, "report.txt", Duration.ofSeconds(1));

        assertThat(evidence.relativePath()).startsWith("run-").endsWith("/report.txt");
        assertThat(evidence.sizeBytes()).isEqualTo(5L);
        assertThat(evidence.sha256()).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        assertThat(evidence.relativePath()).doesNotContain(tempDir.toAbsolutePath().toString());
    }

    @Test
    void pathTraversalAndPlatformAbsoluteFormsAreRejected() {
        BrowserDownloadEvidenceService service = service(1024);
        Path execution = service.createExecutionDirectory();

        assertThatThrownBy(() -> service.prepareExpectedTarget(execution, "../secret.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single path-safe filename");
        assertThatThrownBy(() -> service.prepareExpectedTarget(execution, "..\\secret.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single path-safe filename");
        assertThatThrownBy(() -> service.prepareExpectedTarget(execution, "C:\\secret.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single path-safe filename");
    }

    @Test
    void preExistingArtifactCannotBeReusedAsFreshDownloadEvidence() throws Exception {
        BrowserDownloadEvidenceService service = service(1024);
        Path execution = service.createExecutionDirectory();
        Files.writeString(execution.resolve("report.txt"), "old-data");

        assertThatThrownBy(() -> service.prepareExpectedTarget(execution, "report.txt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void oversizedArtifactFailsClosedBeforeHashEvidenceIsAccepted() throws Exception {
        BrowserDownloadEvidenceService service = service(4);
        Path execution = service.createExecutionDirectory();
        Path target = service.prepareExpectedTarget(execution, "report.txt");
        Files.writeString(target, "hello");

        assertThatThrownBy(() -> service.awaitVerifiedDownload(execution, "report.txt", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds the configured evidence size limit");
    }

    @Test
    void symbolicLinkArtifactIsRejectedWhenPlatformSupportsSymlinks() throws Exception {
        BrowserDownloadEvidenceService service = service(1024);
        Path execution = service.createExecutionDirectory();
        Path outside = tempDir.resolve("outside.txt");
        Files.writeString(outside, "outside");
        Path target = execution.resolve("report.txt");
        try {
            Files.createSymbolicLink(target, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Platform cannot create a test symbolic link: " + exception.getMessage());
        }

        assertThatThrownBy(() -> service.awaitVerifiedDownload(execution, "report.txt", Duration.ofMillis(300)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Symbolic-link downloads are not accepted");
    }

    @Test
    void missingArtifactTimesOutInsteadOfClaimingSuccess() {
        BrowserDownloadEvidenceService service = service(1024);
        Path execution = service.createExecutionDirectory();
        service.prepareExpectedTarget(execution, "missing.txt");

        assertThatThrownBy(() -> service.awaitVerifiedDownload(execution, "missing.txt", Duration.ofMillis(200)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Timed out waiting for the expected downloaded file");
    }

    private BrowserDownloadEvidenceService service(long maxBytes) {
        return new BrowserDownloadEvidenceService(tempDir.resolve("downloads").toString(), maxBytes);
    }
}
