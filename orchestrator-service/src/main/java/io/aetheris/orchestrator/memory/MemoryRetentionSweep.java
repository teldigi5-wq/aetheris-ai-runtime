package io.aetheris.orchestrator.memory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MemoryRetentionSweep {
    private final DurableMemoryService memory;

    public MemoryRetentionSweep(DurableMemoryService memory){this.memory=memory;}

    @Scheduled(fixedDelayString="${aetheris.memory.retention-sweep-ms:300000}")
    public void purgeExpired(){memory.purgeExpired();}
}
