package io.aetheris.orchestrator.execution;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * NON-EXECUTING fixed OCI plan for a synthetic in-container signed-lease
 * verifier. It does not connect to Docker or expose an agent tool/endpoint.
 * A privileged host daemon remains part of CI, NOT a production sandbox.
 */
public final class InWorkerSignedLeaseOciPlan {
    private InWorkerSignedLeaseOciPlan() {}

    public static List<String> build(Path fixture, Path stateDirectory, UUID jobId,
                                     String workerId, long initialEpoch) {
        Objects.requireNonNull(jobId, "jobId");
        if (workerId == null || !workerId.matches("[A-Za-z0-9_-]{1,80}")
                || initialEpoch < 1)
            throw new IllegalArgumentException("Invalid synthetic worker scope");
        var base = SyntheticOciSleepProfilePlan.build(fixture, jobId);
        Path root = fixture.toAbsolutePath().normalize();
        Path state = Objects.requireNonNull(stateDirectory, "stateDirectory")
                .toAbsolutePath().normalize();
        if (state.startsWith(root) || root.startsWith(state))
            throw new IllegalArgumentException("Worker STOP directory must be a distinct mount");
        String source = state.toString();
        if (source.indexOf(',') >= 0 || source.indexOf(':') >= 0
                || source.indexOf('\n') >= 0 || source.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Unsafe STOP mount");
        for (Path ancestor = state; ancestor != null; ancestor = ancestor.getParent()) {
            if (Files.isSymbolicLink(ancestor))
                throw new IllegalArgumentException("Symlinked STOP mount");
        }
        if (!Files.isDirectory(state, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(state))
            throw new IllegalArgumentException("Worker STOP directory is unavailable");
        try {
            if (Files.getPosixFilePermissions(state)
                    .contains(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE))
                throw new IllegalArgumentException("World-writable STOP mount prohibited");
        } catch (java.io.IOException failure) {
            throw new IllegalArgumentException("Unverifiable STOP mount permissions", failure);
        }
        for (String file : List.of("stage45-lease-guard", "issuer.pub", "lease.txt")) {
            if (!Files.isRegularFile(root.resolve(file), LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(root.resolve(file)))
                throw new IllegalArgumentException("Missing or linked signed lease fixture");
        }
        // Replace ONLY the fixed entrypoint, never the isolation flags,
        // image digest, mount, resource quotas, or synthetic container name.
        var args = new ArrayList<>(base.subList(0, base.size() - 2));
        int imageIndex = args.indexOf(IsolatedOciProbePlan.FIXTURE_IMAGE);
        if (imageIndex < 0) throw new IllegalStateException("OCI image not pinned");
        // A single narrowly scoped, persistent writable STOP volume is
        // mounted in addition to the read-only fixture and root filesystem.
        args.add(imageIndex, "--mount=type=bind,source=" + source
                + ",target=/aetheris-state");
        args.addAll(List.of("/workspace/stage45-lease-guard",
                "/workspace/issuer.pub", "/workspace/lease.txt",
                workerId, jobId.toString(), Long.toString(initialEpoch)));
        return List.copyOf(args);
    }
}
