package io.aetheris.orchestrator.execution;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IsolatedOciProbePlanTest {
    @TempDir Path root;

    @Test
    void fixedPlanDisablesNetworkCapabilitiesWritesAndPrivilegedExecution() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"));
        Files.writeString(root.resolve("probe.sh"), "exit 0\n");
        List<String> args = IsolatedOciProbePlan.build(root);
        assertThat(args).contains("--network=none", "--read-only", "--cap-drop=ALL",
                "--security-opt=no-new-privileges:true", "--pids-limit=32",
                "--memory=128m", "--cpus=0.50", "--ipc=none",
                "--user=65534:65534", "--pull=never");
        assertThat(args).contains(IsolatedOciProbePlan.FIXTURE_IMAGE);
        assertThat(args).contains("--mount=type=bind,source=" + root.toAbsolutePath()
                + ",target=/workspace,readonly");
        assertThat(args).doesNotContain("--privileged", "--network=host",
                "--pid=host", "--cap-add=ALL", "--mount=type=bind,source=/,target=/host");
        assertThat(args.getLast()).isEqualTo("/workspace/probe.sh");
        assertThatThrownBy(() -> args.add("evil")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectSymlinkedRootAndProbeAndMissingProbe() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"));
        assertThatThrownBy(() -> IsolatedOciProbePlan.build(root))
                .isInstanceOf(IllegalArgumentException.class);
        Files.writeString(root.resolve("probe.sh"), "exit 0\n");
        Path link = root.getParent().resolve("probe-link-" + java.util.UUID.randomUUID());
        try {
            Files.createSymbolicLink(link, root);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
            Assumptions.assumeTrue(false, "Symlinks unsupported");
        }
        try {
            assertThatThrownBy(() -> IsolatedOciProbePlan.build(link))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("symlink");
            Files.delete(root.resolve("probe.sh"));
            Files.createSymbolicLink(root.resolve("probe.sh"), root.resolve("missing.sh"));
            assertThatThrownBy(() -> IsolatedOciProbePlan.build(root))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            Files.deleteIfExists(link);
        }
    }

    @Test
    void rejectAmbiguousMountDelimitersBeforeAnyLaunch() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"));
        Path unsafe = Files.createDirectory(root.resolve("comma,src=/"));
        Files.writeString(unsafe.resolve("probe.sh"), "exit 0\n");
        assertThatThrownBy(() -> IsolatedOciProbePlan.build(unsafe))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsafe OCI fixture path");
    }
}
