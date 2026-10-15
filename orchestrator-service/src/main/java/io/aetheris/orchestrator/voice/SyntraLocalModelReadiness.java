package io.aetheris.orchestrator.voice;

import java.lang.management.ManagementFactory;
import java.util.OptionalLong;

/**
 * Stage 46.3: conservative, advisory-only local hardware observation.
 * Model-file size is NOT a RAM/VRAM estimate or inference benchmark.
 * Never guesses GPU model, dedicated VRAM, model context or throughput.
 */
public final class SyntraLocalModelReadiness {
    private SyntraLocalModelReadiness() {}

    public record HostMemory(OptionalLong totalBytes, OptionalLong freeBytes) {
        public HostMemory {
            if (totalBytes == null || freeBytes == null) throw new IllegalArgumentException("MEMORY_READING_REQUIRED");
            if (totalBytes.isPresent() && totalBytes.getAsLong()<=0 ||
                freeBytes.isPresent() && freeBytes.getAsLong()<0 ||
                totalBytes.isPresent() && freeBytes.isPresent()
                && freeBytes.getAsLong()>totalBytes.getAsLong())
                throw new IllegalArgumentException("MEMORY_READING_INVALID");
        }
    }

    public record Review(String model, long modelDiskBytes, String sha256Prefix,
                         HostMemory hostMemory, String advisory,
                         boolean loadCertified, boolean gpuCertified) {}

    /** Reads host physical memory only if exposed by the Java runtime.
     * This does NOT prove available cgroup limits, GPU VRAM or speed. */
    public static HostMemory observe() {
        try {
            var bean=ManagementFactory.getOperatingSystemMXBean();
            if (!(bean instanceof com.sun.management.OperatingSystemMXBean os))
                return new HostMemory(OptionalLong.empty(),OptionalLong.empty());
            long total=os.getTotalMemorySize();
            long free=os.getFreeMemorySize();
            if (total<=0 || free<0 || free>total)
                return new HostMemory(OptionalLong.empty(),OptionalLong.empty());
            return new HostMemory(OptionalLong.of(total),OptionalLong.of(free));
        } catch (RuntimeException | LinkageError unavailable) {
            return new HostMemory(OptionalLong.empty(),OptionalLong.empty());
        }
    }

    public static Review evaluate(String model,long diskBytes,String sha256,HostMemory memory) {
        if (model==null || !model.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,79}") ||
                diskBytes<=0 || sha256==null || !sha256.matches("[0-9a-f]{64}") ||
                memory==null)
            throw new IllegalArgumentException("INVALID_LOCAL_MODEL_METADATA");
        String advisory;
        if (memory.freeBytes().isEmpty()) {
            advisory="HOST_MEMORY_UNKNOWN_NO_LOAD_CERTIFICATION";
        } else if (diskBytes>memory.freeBytes().getAsLong()) {
            advisory="MODEL_DISK_SIZE_GREATER_THAN_FREE_HOST_RAM_NOT_A_LOAD_ESTIMATE";
        } else {
            advisory="MODEL_DISK_SIZE_WITHIN_FREE_HOST_RAM_NOT_A_LOAD_CERTIFICATION";
        }
        return new Review(model,diskBytes,sha256.substring(0,12),
                memory,advisory,false,false);
    }
}
