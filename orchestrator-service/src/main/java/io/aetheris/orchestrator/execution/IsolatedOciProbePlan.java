package io.aetheris.orchestrator.execution;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * NON-EXECUTING Stage 45 OCI isolation probe specification.
 *
 * Not a Spring bean, tool adapter, container daemon client or production
 * executor. This class ONLY constructs a fixed smoke-test plan to permit
 * independent review of important isolation arguments. No arbitrary AI
 * command, user-selected image, Docker socket, env or host mount is accepted.
 * A rootful Docker daemon is a privileged component: do NOT connect this
 * planner to agent execution or production without an isolated runner host.
 */
public final class IsolatedOciProbePlan {
    // Exact linux/amd64 containerd BusyBox image digest; not a moving tag.
    public static final String FIXTURE_IMAGE =
            "ghcr.io/containerd/busybox@sha256:907ca53d7e2947e849b839b1cd258c98fd3916c60f2e6e70c30edbf741ab6754";

    private IsolatedOciProbePlan() {}

    /** Returns Docker CLI argv only. This class never launches a process. */
    public static List<String> build(Path fixtureDirectory) {
        Objects.requireNonNull(fixtureDirectory, "fixtureDirectory");
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (!operatingSystem.contains("linux")) {
            throw new IllegalStateException("OCI probe requires dedicated Linux runner");
        }

        Path root = fixtureDirectory.toAbsolutePath().normalize();
        // The docker --mount parser uses commas as field separators. Reject
        // malformed and potentially ambiguous paths rather than escaping
        // attacker-controlled mount arguments.
        String source = root.toString();
        if (source.indexOf(',') >= 0 || source.indexOf('\n') >= 0
                || source.indexOf('\r') >= 0 || source.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Unsafe OCI fixture path");
        }
        for (Path cursor = root; cursor != null; cursor = cursor.getParent()) {
            if (Files.isSymbolicLink(cursor)) {
                throw new IllegalArgumentException("OCI fixture path contains symlink");
            }
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(root.resolve("probe.sh"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("OCI fixture directory or probe file unavailable");
        }

        List<String> args = new ArrayList<>(List.of(
                "docker", "run",
                "--rm",
                "--pull=never",
                "--network=none",
                "--read-only",
                "--cap-drop=ALL",
                "--security-opt=no-new-privileges:true",
                "--pids-limit=32",
                "--memory=128m",
                "--cpus=0.50",
                "--ipc=none",
                "--user=65534:65534",
                "--tmpfs=/tmp:rw,nosuid,nodev,noexec,size=16m",
                "--mount=type=bind,source=" + source + ",target=/workspace,readonly",
                "--workdir=/workspace",
                FIXTURE_IMAGE,
                "/bin/sh",
                "/workspace/probe.sh"));
        return List.copyOf(args);
    }
}
