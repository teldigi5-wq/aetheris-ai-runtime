package io.aetheris.orchestrator.execution;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InWorkerSignedLeaseOciPlanTest {
    @TempDir Path root;

    private void fixture() throws Exception {
        for (var file : List.of("sleep-fixture.sh", "stage45-lease-guard",
                "issuer.pub", "lease.txt")) {
            Files.writeString(root.resolve(file), "synthetic");
        }
    }

    @Test
    void retainsPinnedImageNonrootSandboxAndFixedGuardEntrypoint() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"));
        fixture();
        UUID job = UUID.randomUUID();
        var args = InWorkerSignedLeaseOciPlan.build(root, job, "syntheticWorker", 1);
        assertThat(args).contains("--network=none", "--read-only", "--cap-drop=ALL",
                "--security-opt=no-new-privileges:true", "--user=65534:65534",
                "--memory=128m", "--cpus=0.50", "--pids-limit=32",
                IsolatedOciProbePlan.FIXTURE_IMAGE, "/workspace/stage45-lease-guard",
                "/workspace/issuer.pub", "/workspace/lease.txt",
                "syntheticWorker", job.toString(), "1");
        assertThat(args).doesNotContain("--privileged", "--network=host",
                "--pid=host", "/bin/sh", "--user=0:0", "--cap-add=ALL");
        assertThatThrownBy(() -> args.add("--privileged"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void missingAndLinkedLeaseInputsFailClosed() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"));
        fixture();
        assertThatThrownBy(() -> InWorkerSignedLeaseOciPlan.build(
                root, UUID.randomUUID(), "../worker", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InWorkerSignedLeaseOciPlan.build(
                root, UUID.randomUUID(), "worker", 0))
                .isInstanceOf(IllegalArgumentException.class);
        Files.delete(root.resolve("issuer.pub"));
        Files.createSymbolicLink(root.resolve("issuer.pub"), root.resolve("lease.txt"));
        assertThatThrownBy(() -> InWorkerSignedLeaseOciPlan.build(
                root, UUID.randomUUID(), "worker", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
