package io.aetheris.orchestrator.task;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Internal event journaling remains functional even while HTTP event reads
 * are closed. Confirms there is no unscoped global subscription interface.
 */
class InternalTaskEventJournalLockdownTest {
    @Test
    void eventPublicationStillPersistsButCannotRegisterGlobalSubscriber() {
        TaskEventJournalService journal = mock(TaskEventJournalService.class);
        TaskEventStreamService streams = new TaskEventStreamService(journal);
        TaskEvent event = new TaskEvent(UUID.randomUUID(), Instant.now(),
            TaskState.QUEUED, null, "Synthetic task event", Map.of("secret", "do-not-broadcast"));
        streams.publish(event);
        verify(journal, times(1)).append(event);
        assertEquals(0, java.util.Arrays.stream(TaskEventStreamService.class.getMethods())
            .filter(method -> "subscribeAll".equals(method.getName())).count());
        assertEquals(0, java.util.Arrays.stream(TaskEventStreamService.class.getDeclaredFields())
            .filter(field -> "globalEmitters".equals(field.getName())).count());
    }
}
