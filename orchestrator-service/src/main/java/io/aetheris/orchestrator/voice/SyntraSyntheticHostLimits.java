package io.aetheris.orchestrator.voice;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Stage 46.11: optional Linux-only resource-limit LAB for the fixed
 * synthetic child JVM. Not a sandbox: same UID, existing filesystem and
 * network privileges, no namespaces, seccomp, cgroups or Windows Job Object.
 * Existing portable synthetic behavior is preserved unchanged.
 *
 * All command elements are fixed except already-trusted Java home,
 * JVM classpath and the strict internal test mode enum. Never shell-exec.
 */
final class SyntraSyntheticHostLimits {
    enum Profile { PORTABLE_SYNTHETIC_ONLY, LINUX_RLIMIT_LAB }

    // Fixed test-only class identity, intentionally not linked to the worker
    // bytecode. Its absence from a production JAR fails closed on invocation.
    // Keep this literal synchronized through SyntheticWorkerProtocolContractTest.
    static final String FIXED_PCM_WORKER_CLASS =
            "io.aetheris.orchestrator.voice.SyntraSyntheticPcmWorkerMain";
    private static final Path LINUX_PRLIMIT = Path.of("/usr/bin/prlimit");
    static final String CPU_SECONDS = "--cpu=5";
    static final String ADDRESS_BYTES = "--as=4294967296"; // 4 GiB virtual address space
    static final String FILE_DESCRIPTORS = "--nofile=128";

    private SyntraSyntheticHostLimits() {}

    static boolean linuxLabAvailable() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.equals("linux")) return false;
        return Files.isRegularFile(LINUX_PRLIMIT, LinkOption.NOFOLLOW_LINKS)
                && Files.isExecutable(LINUX_PRLIMIT)
                && !Files.isSymbolicLink(LINUX_PRLIMIT);
    }

    static List<String> command(Profile profile, String javaExe,
                                String classPath, String internalMode) {
        Objects.requireNonNull(profile);
        Objects.requireNonNull(javaExe);
        Objects.requireNonNull(classPath);
        Objects.requireNonNull(internalMode);
        if (javaExe.isBlank() || classPath.isBlank() ||
                !List.of("NORMAL", "STALL", "MALFORMED", "FLOOD", "FAIL").contains(internalMode))
            throw new IllegalArgumentException("UNTRUSTED_SYNTHETIC_PROCESS_CONFIGURATION");
        List<String> result = new ArrayList<>();
        if (profile == Profile.LINUX_RLIMIT_LAB) {
            if (!linuxLabAvailable())
                throw new IllegalStateException("LINUX_RESOURCE_LAB_NOT_SUPPORTED");
            result.add(LINUX_PRLIMIT.toString());
            result.add(CPU_SECONDS);
            result.add(ADDRESS_BYTES);
            result.add(FILE_DESCRIPTORS);
            result.add("--");
        }
        result.add(javaExe);
        result.add("-Xms16m");
        result.add("-Xmx64m");
        result.add("-cp");
        result.add(classPath);
        result.add(FIXED_PCM_WORKER_CLASS);
        result.add(internalMode);
        return List.copyOf(result);
    }
}
