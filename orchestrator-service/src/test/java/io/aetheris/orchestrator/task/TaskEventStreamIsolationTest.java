package io.aetheris.orchestrator.task;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Real TaskEventStreamService regression for issue #23.
 *
 * Until verified task ownership and a scoped event replay exist, production
 * must not retain any "listen to every task" subscriber surface. These are
 * service-level tests, not proof of authenticated HTTP access or revocation.
 */
class TaskEventStreamIsolationTest {

    @Test
    void globalSubscriberApiAndRegistryAreRemoved() {
        assertTrue(Arrays.stream(TaskEventStreamService.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("subscribeAll")),
                "Global event stream must not be recoverable through a public method");
        assertTrue(Arrays.stream(TaskEventStreamService.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .noneMatch(field -> field.getName().toLowerCase(java.util.Locale.ROOT).contains("global")),
                "The service must not retain a global emitter registry");
    }

    @Test
    void taskSpecificSubscriptionAndJournalPublicationRemainFunctional() {
        TaskEventJournalService journal = mock(TaskEventJournalService.class);
        TaskEventStreamService streams = new TaskEventStreamService(journal);
        UUID ownerTaskId = UUID.randomUUID();
        UUID otherTaskId = UUID.randomUUID();

        SseEmitter first = streams.subscribe(ownerTaskId);
        SseEmitter second = streams.subscribe(otherTaskId);
        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(first, second, "Task-specific subscriptions must be distinct");

        TaskEvent event = new TaskEvent(otherTaskId, Instant.parse("2026-11-08T00:00:00Z"),
                TaskState.QUEUED, null, "synthetic task event", Map.of("synthetic", true));
        streams.publish(event);
        verify(journal, times(1)).append(event);
        verifyNoMoreInteractions(journal);
    }
}
