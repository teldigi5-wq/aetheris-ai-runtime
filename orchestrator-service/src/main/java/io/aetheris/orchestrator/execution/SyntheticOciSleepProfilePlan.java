package io.aetheris.orchestrator.execution;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * NON-EXECUTING, fixed-image Stage 45.5.4 lab profile.
 * No API, bean, endpoint, Docker socket integration, user-defined executable,
 * model-supplied arguments or production RUN authority. A privileged Docker
 * daemon on a hosted CI runner is NOT a production isolation boundary.
 */
public final class SyntheticOciSleepProfilePlan {
    private SyntheticOciSleepProfilePlan() {}

    public static List<String> build(Path fixture, UUID jobId) {
        Objects.requireNonNull(fixture, "fixture");
        Objects.requireNonNull(jobId, "jobId");
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux"))
            throw new IllegalStateException("Linux-only OCI lab");
        Path root = fixture.toAbsolutePath().normalize();
        String source = root.toString();
        if (source.indexOf(',') >= 0 || source.indexOf(':') >= 0
                || source.indexOf('\n') >= 0 || source.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Unsafe OCI mount source");
        for (Path parent = root; parent != null; parent = parent.getParent()) {
            if (Files.isSymbolicLink(parent))
                throw new IllegalArgumentException("Symlinked OCI mount rejected");
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(root.resolve("sleep-fixture.sh"), LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(root.resolve("sleep-fixture.sh")))
            throw new IllegalArgumentException("Missing synthetic fixed worker fixture");

        return List.of("docker", "run", "-d", "--pull=never",
                "--name=aetheris-ci-synthetic-" + jobId,
                "--network=none", "--read-only", "--cap-drop=ALL",
                "--security-opt=no-new-privileges:true",
                "--pids-limit=32", "--memory=128m", "--cpus=0.50",
                "--ipc=none", "--user=65534:65534",
                "--tmpfs=/tmp:rw,nosuid,nodev,noexec,size=16m",
                "--mount=type=bind,source=" + source + ",target=/workspace,readonly",
                "--workdir=/workspace",
                IsolatedOciProbePlan.FIXTURE_IMAGE,
                "/bin/sh", "/workspace/sleep-fixture.sh");
    }

    /** No dynamic shell command and no capability to choose OCI images. */
    public static String containerName(UUID jobId) {
        return "aetheris-ci-synthetic-" + Objects.requireNonNull(jobId, "jobId");
    }
}
