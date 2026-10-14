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

    public static List<String> build(Path fixture, UUID jobId,
                                     String workerId, long initialEpoch) {
        Objects.requireNonNull(jobId, "jobId");
        if (workerId == null || !workerId.matches("[A-Za-z0-9_-]{1,80}")
                || initialEpoch < 1)
            throw new IllegalArgumentException("Invalid synthetic worker scope");
        var base = SyntheticOciSleepProfilePlan.build(fixture, jobId);
        Path root = fixture.toAbsolutePath().normalize();
        for (String file : List.of("stage45-lease-guard", "issuer.pub", "lease.txt")) {
            if (!Files.isRegularFile(root.resolve(file), LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(root.resolve(file)))
                throw new IllegalArgumentException("Missing or linked signed lease fixture");
        }
        // Replace ONLY the fixed entrypoint, never the isolation flags,
        // image digest, mount, resource quotas, or synthetic container name.
        var args = new ArrayList<>(base.subList(0, base.size() - 2));
        args.addAll(List.of("/workspace/stage45-lease-guard",
                "/workspace/issuer.pub", "/workspace/lease.txt",
                workerId, jobId.toString(), Long.toString(initialEpoch)));
        return List.copyOf(args);
    }
}
