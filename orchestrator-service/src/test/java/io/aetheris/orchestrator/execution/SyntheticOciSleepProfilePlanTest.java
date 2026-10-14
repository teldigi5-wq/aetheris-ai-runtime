package io.aetheris.orchestrator.execution;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyntheticOciSleepProfilePlanTest {
    @TempDir Path root;

    @Test
    void fixedDigestAndNonPrivilegedFilesystemNetworkAndQuotas() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"));
        Files.writeString(root.resolve("sleep-fixture.sh"), "sleep 1\n");
        UUID jobId = UUID.randomUUID();
        var argv = SyntheticOciSleepProfilePlan.build(root, jobId);
        assertThat(argv).contains("--network=none", "--read-only", "--cap-drop=ALL",
                "--security-opt=no-new-privileges:true", "--memory=128m", "--cpus=0.50",
                "--pids-limit=32", "--ipc=none", "--user=65534:65534",
                IsolatedOciProbePlan.FIXTURE_IMAGE,
                "/workspace/sleep-fixture.sh");
        assertThat(argv).doesNotContain("--privileged", "--network=host", "--pid=host",
                "--user=0:0", "--cap-add=ALL");
        assertThat(argv).contains("--name=" + SyntheticOciSleepProfilePlan.containerName(jobId));
        assertThatThrownBy(() -> argv.add("bad"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void refusesMissingFixtureAndSymlinkMount() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"));
        assertThatThrownBy(() -> SyntheticOciSleepProfilePlan.build(root, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        Files.writeString(root.resolve("sleep-fixture.sh"), "sleep 1\n");
        Path link = root.resolve("link");
        Files.createSymbolicLink(link, root.resolve("sleep-fixture.sh"));
        Path target = root.resolve("other");
        Files.createDirectory(target);
        Files.createSymbolicLink(target.resolve("sleep-fixture.sh"), link);
        assertThatThrownBy(() -> SyntheticOciSleepProfilePlan.build(target, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMountDelimiterInjectionWithoutExecutingAnything() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"));
        Path injected = Files.createDirectory(root.resolve("unsafe,volume"));
        Files.writeString(injected.resolve("sleep-fixture.sh"), "sleep 1\n");
        assertThatThrownBy(() -> SyntheticOciSleepProfilePlan.build(injected, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsafe");
    }
}
