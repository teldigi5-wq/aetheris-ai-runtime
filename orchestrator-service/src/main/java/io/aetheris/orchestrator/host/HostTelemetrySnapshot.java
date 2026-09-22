package io.aetheris.orchestrator.host;
import java.time.Instant;
import java.util.UUID;
public record HostTelemetrySnapshot(UUID hostId,Instant capturedAt,double cpuPercent,double memoryPercent,double gpuPercent,double vramPercent,long freeDiskBytes,String source) {}
