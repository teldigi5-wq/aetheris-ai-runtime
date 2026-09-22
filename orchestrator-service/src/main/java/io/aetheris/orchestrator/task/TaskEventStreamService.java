package io.aetheris.orchestrator.task;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class TaskEventStreamService {

    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();
    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<SseEmitter> globalEmitters = new CopyOnWriteArrayList<>();
    private final TaskEventJournalService journal;

    public TaskEventStreamService(TaskEventJournalService journal) { this.journal = journal; }

    public SseEmitter subscribe(UUID taskId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(taskId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(taskId, emitter)); emitter.onTimeout(() -> remove(taskId, emitter)); emitter.onError(error -> remove(taskId, emitter));
        return emitter;
    }

    public SseEmitter subscribeAll() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        globalEmitters.add(emitter);
        emitter.onCompletion(() -> globalEmitters.remove(emitter)); emitter.onTimeout(() -> globalEmitters.remove(emitter)); emitter.onError(error -> globalEmitters.remove(emitter));
        return emitter;
    }

    public List<TaskEvent> history(UUID taskId) { return journal.history(taskId); }

    public void publish(TaskEvent event) {
        journal.append(event);
        send(event, emitters.getOrDefault(event.taskId(), new CopyOnWriteArrayList<>()), () -> {});
        send(event, globalEmitters, () -> {});
    }

    private void send(TaskEvent event, List<SseEmitter> subscribers, Runnable ignored) {
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().name("task-event").id(event.taskId()+":"+event.timestamp()).data(event));
            } catch (IOException | IllegalStateException exception) {
                subscribers.remove(emitter);
            }
        }
    }

    private void remove(UUID taskId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> subscribers = emitters.get(taskId); if (subscribers == null) return;
        subscribers.remove(emitter); if (subscribers.isEmpty()) emitters.remove(taskId);
    }
}
