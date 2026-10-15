package io.aetheris.orchestrator.voice;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Tests kernel-enforced RLIMIT values only on the Linux synthetic CI host. */
class SyntraSyntheticHostLimitsTest {
    private static byte[] loud() {
        byte[] out = new byte[640];
        for (int i = 0; i < out.length; i += 2) out[i + 1] = 16;
        return out;
    }
    private static boolean supported() {
        return SyntraSyntheticHostLimits.linuxLabAvailable()
                && Files.isReadable(Path.of("/proc/self/limits"));
    }
    private static SyntraSyntheticProcessSpeechBoundary linux(String mode, int waitMs) {
        return SyntraSyntheticProcessSpeechBoundary.forLinuxLimitsTest(
                () -> true, () -> false, waitMs, mode);
    }

    @Test void portableDefaultCommandDoesNotAddHostLimitsOrShell() {
        var command = SyntraSyntheticHostLimits.command(
                SyntraSyntheticHostLimits.Profile.PORTABLE_SYNTHETIC_ONLY,
                "/trusted/java", "/safe/classpath", "NORMAL");
        assertThat(command.get(0)).isEqualTo("/trusted/java");
        assertThat(command).doesNotContain("/usr/bin/prlimit", "-c", "/bin/sh", "bash");
        assertThat(command).contains("-Xmx64m",
                SyntraSyntheticPcmWorkerMain.class.getName(), "NORMAL");
    }

    @Test void linuxLabAddsOnlyHardcodedKernelResourceLimitArguments() {
        assumeTrue(supported(), "Linux resource lab not supported on this CI host");
        var command = SyntraSyntheticHostLimits.command(
                SyntraSyntheticHostLimits.Profile.LINUX_RLIMIT_LAB,
                "/trusted/java", "/safe/classpath", "STALL");
        assertThat(command.subList(0, 5)).containsExactly(
                "/usr/bin/prlimit", "--cpu=5", "--as=4294967296", "--nofile=128", "--");
        assertThat(command.get(5)).isEqualTo("/trusted/java");
        assertThat(command).doesNotContain("sh", "-c", "/bin/bash");
    }

    @Test void linuxKernelConfirmsCpuAddressAndFileDescriptorLimitsOnActualChild()
            throws Exception {
        assumeTrue(supported(), "Linux kernel /proc limit introspection unavailable");
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try (var boundary = linux("STALL", 4_000)) {
            assertThat(boundary.beginSyntheticSession(true))
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.SESSION_READY);
            var pending = caller.submit(() -> boundary.inspectSyntheticPcm(loud(), 16_000));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            long pid = 0;
            while (System.nanoTime() < deadline) {
                pid = boundary.activeChildPidForTest();
                if (pid > 0 && Files.exists(Path.of("/proc", Long.toString(pid), "limits"))) break;
                Thread.sleep(10);
            }
            assertThat(pid).isPositive();
            String limits = Files.readString(Path.of("/proc", Long.toString(pid), "limits"));
            assertThat(Pattern.compile("(?m)^Max cpu time\\s+5\\s+5\\s+seconds\\s*$")
                    .matcher(limits).find()).isTrue();
            assertThat(Pattern.compile("(?m)^Max address space\\s+4294967296\\s+4294967296\\s+bytes\\s*$")
                    .matcher(limits).find()).isTrue();
            assertThat(Pattern.compile("(?m)^Max open files\\s+128\\s+128\\s+files\\s*$")
                    .matcher(limits).find()).isTrue();
            boundary.stopNow();
            assertThat(pending.get(6, TimeUnit.SECONDS).outcome())
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.STOP_LATCHED);
            assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false))
                    .isFalse();
        } finally {
            caller.shutdownNow();
        }
    }

    @Test void linuxProfileNormalSyntheticVoiceAndSilenceWorkUnderLimits() {
        assumeTrue(supported(), "Linux resource lab not supported");
        try (var boundary = linux("NORMAL", 5_000)) {
            boundary.beginSyntheticSession(true);
            assertThat(boundary.inspectSyntheticPcm(loud(), 16_000).outcome())
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.VOICE);
            assertThat(boundary.inspectSyntheticPcm(new byte[640], 16_000).outcome())
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.SILENCE);
        }
    }

    @Test void linuxProfileKillsStalledChildAtConfiguredWallTimeout() {
        assumeTrue(supported(), "Linux resource lab not supported");
        try (var boundary = linux("STALL", 350)) {
            boundary.beginSyntheticSession(true);
            assertThat(boundary.inspectSyntheticPcm(loud(), 16_000).outcome())
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.TIMEOUT_LATCHED);
            long pid = boundary.latestChildPidForTest();
            assertThat(pid).isPositive();
            assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false))
                    .isFalse();
            assertThat(boundary.stopped()).isTrue();
        }
    }

    @Test void unsupportedHostsDenyLinuxProfileInsteadOfFallingBack() {
        if (SyntraSyntheticHostLimits.linuxLabAvailable()) return;
        assertThatThrownBy(() -> linux("NORMAL", 5_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LINUX_RESOURCE_LAB_NOT_SUPPORTED");
    }

    @Test void noArbitraryExecutableOrFaultModeCanBeSelectedByThisProfile() {
        assertThatThrownBy(() -> SyntraSyntheticHostLimits.command(
                SyntraSyntheticHostLimits.Profile.PORTABLE_SYNTHETIC_ONLY,
                "/trusted/java", "/trusted/classes", "SH -c evil"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SyntraSyntheticHostLimits.command(
                SyntraSyntheticHostLimits.Profile.PORTABLE_SYNTHETIC_ONLY,
                "/trusted/java", "", "NORMAL"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SyntraSyntheticHostLimits.command(
                null, "/trusted/java", "/trusted/classes", "NORMAL"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void revocationBeforeLinuxChildLaunchStillDeniesAndCannotRearm() {
        assumeTrue(supported(), "Linux resource lab not supported");
        AtomicBoolean stop = new AtomicBoolean(true);
        try (var boundary = SyntraSyntheticProcessSpeechBoundary.forLinuxLimitsTest(
                () -> true, stop::get, 1000, "NORMAL")) {
            assertThat(boundary.beginSyntheticSession(true))
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.STOP_LATCHED);
            stop.set(false);
            assertThat(boundary.beginSyntheticSession(true))
                    .isEqualTo(SyntraSyntheticProcessSpeechBoundary.Outcome.STOP_LATCHED);
            assertThat(boundary.latestChildPidForTest()).isZero();
        }
    }
}
