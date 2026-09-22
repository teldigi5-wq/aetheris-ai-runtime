package io.aetheris.orchestrator.syntracore;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.util.Objects;
import java.util.OptionalLong;

public final class JvmLocalHardwareProbe {
    private static final long BYTES_PER_MEBIBYTE = 1024L * 1024L;

    private final Clock clock;

    public JvmLocalHardwareProbe() {
        this(Clock.systemUTC());
    }

    JvmLocalHardwareProbe(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public LocalHardwareDiscovery discover() {
        OptionalLong totalPhysicalRamMb = OptionalLong.empty();
        var operatingSystem = ManagementFactory.getOperatingSystemMXBean();
        if (operatingSystem instanceof com.sun.management.OperatingSystemMXBean extended) {
            long bytes = extended.getTotalMemorySize();
            if (bytes > 0) {
                totalPhysicalRamMb = OptionalLong.of(bytes / BYTES_PER_MEBIBYTE);
            }
        }

        return new LocalHardwareDiscovery(
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.arch", "unknown"),
                Runtime.getRuntime().availableProcessors(),
                totalPhysicalRamMb,
                clock.instant());
    }
}
