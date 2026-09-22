package io.aetheris.orchestrator.proactive;
import java.time.Instant;
public record ProactiveScanResult(int signalsInspected,int notificationsCreated,Instant scannedAt){}
